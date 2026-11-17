package com.asalariado.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.asalariado.app.MainActivity
import com.asalariado.app.R
import com.asalariado.app.data.AppDatabase
import com.asalariado.app.data.Backup
import com.asalariado.app.data.ConditionsStore
import com.asalariado.app.data.toModel
import com.asalariado.app.widget.ShiftWidget
import com.asalariado.core.DayEntry
import com.asalariado.core.ShiftType
import com.asalariado.core.effectiveHours
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Preferencias de avisos y copia automática. */
object AutoPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("auto", Context.MODE_PRIVATE)
    fun notificationsOn(c: Context) = prefs(c).getBoolean("notifications", false)
    fun setNotifications(c: Context, on: Boolean) = prefs(c).edit().putBoolean("notifications", on).apply()
    fun backupFolder(c: Context): Uri? = prefs(c).getString("backup_folder", null)?.let(Uri::parse)
    fun setBackupFolder(c: Context, uri: Uri?) = prefs(c).edit().putString("backup_folder", uri?.toString()).apply()
    fun lastBackup(c: Context): String? = prefs(c).getString("last_backup", null)
    fun setLastBackup(c: Context, v: String) = prefs(c).edit().putString("last_backup", v).apply()
}

object Scheduler {
    private const val DAILY = "daily_check"
    private const val BACKUP = "weekly_backup"

    /** Programa la revisión diaria (avisos y widget) a las 20:00 y la copia semanal. */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(20, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            DAILY, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS).setInitialDelay(delay, TimeUnit.MINUTES).build(),
        )
        if (AutoPrefs.backupFolder(context) != null) {
            wm.enqueueUniquePeriodicWork(
                BACKUP, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build(),
            )
        } else {
            wm.cancelUniqueWork(BACKUP)
        }
    }
}

private const val CHANNEL = "avisos"

private fun notify(context: Context, id: Int, title: String, text: String) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val nm = context.getSystemService(NotificationManager::class.java)
    if (nm.getNotificationChannel(CHANNEL) == null) {
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Avisos de turnos y nóminas", NotificationManager.IMPORTANCE_DEFAULT))
    }
    val open = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val n = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_shift)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    runCatching { NotificationManagerCompat.from(context).notify(id, n) }
}

/** Revisión diaria: avisa de cambios de turno, paga extra y nómina pendiente, y refresca el widget. */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        runCatching { ShiftWidget().updateAll(ctx) }
        if (!AutoPrefs.notificationsOn(ctx)) return Result.success()
        val dao = AppDatabase.get(ctx).dao()
        val c = ConditionsStore(ctx).state.value
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val days = dao.daysBetween(today.minusDays(10).toEpochDay(), tomorrow.toEpochDay()).map { it.toModel() }.associateBy { it.date }

        // Cambio de turno: mañana se trabaja con un turno distinto al último trabajado.
        val next = days[tomorrow]
        if (next != null && next.shift.isWork) {
            val last = generateSequence(today) { it.minusDays(1) }.take(10).mapNotNull { days[it] }.firstOrNull { it.shift.isWork }
            if (last == null || last.shift != next.shift) {
                notify(ctx, 1, "Mañana: turno de ${next.shift.label.lowercase()}", describe(next, c))
            }
        }

        // Paga extra: el día aproximado de cobro.
        c.extraPayDay(today.monthValue)?.let { day ->
            if (today.dayOfMonth == day) {
                val month = today.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-ES"))
                notify(ctx, 2, "Paga extra de $month", "Hoy debería llegarte la paga extra. Cuando tengas el recibo, impórtalo para comprobarlo.")
            }
        }

        // Nómina del mes pasado sin apuntar (día 5).
        if (today.dayOfMonth == 5) {
            val prev = today.minusMonths(1)
            val has = dao.payslipsOnce().any { it.year == prev.year && it.month == prev.monthValue }
            if (!has) {
                val month = prev.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-ES"))
                notify(ctx, 3, "¿Te ha llegado la nómina?", "Importa la nómina de $month para comprobar que cobras lo que te corresponde.")
            }
        }
        return Result.success()
    }

    private fun describe(d: DayEntry, c: com.asalariado.core.Conditions): String {
        val h = d.effectiveHours(c) ?: return "Revisa tu calendario."
        fun t(x: LocalTime) = "%02d:%02d".format(x.hour, x.minute)
        return "De ${t(h.start)} a ${t(h.end)}." + if (d.shift == ShiftType.NIGHT) " Turno de noche." else ""
    }
}

/** Copia de seguridad semanal en la carpeta elegida (puede ser una carpeta sincronizada con la nube). */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val folder = AutoPrefs.backupFolder(ctx) ?: return Result.success()
        return try {
            val json = Backup.export(AppDatabase.get(ctx), ConditionsStore.toJson(ConditionsStore(ctx).state.value))
            val dir = DocumentFile.fromTreeUri(ctx, folder) ?: return Result.failure()
            val name = "asalariado-copia-automatica.json"
            val file = dir.findFile(name) ?: dir.createFile("application/json", name) ?: return Result.failure()
            ctx.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(json.toByteArray()) }
            AutoPrefs.setLastBackup(ctx, LocalDateTime.now().toString().take(16).replace('T', ' '))
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
