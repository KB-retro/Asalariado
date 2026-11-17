package com.asalariado.app.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.asalariado.app.MainActivity
import com.asalariado.app.data.AppDatabase
import com.asalariado.app.data.ConditionsStore
import com.asalariado.app.data.toModel
import com.asalariado.core.Rotation
import com.asalariado.core.effectiveHours
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Widget de la pantalla de inicio: turno de hoy y de esta semana / la siguiente. */
class ShiftWidget : GlanceAppWidget() {

    private data class Info(val dayTitle: String, val shift: String, val hours: String, val weeks: String)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val info = load(context)
        provideContent {
            GlanceTheme {
                Column(
                    GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp)
                        .padding(14.dp).clickable(actionStartActivity<MainActivity>()),
                ) {
                    Text(info.dayTitle, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                    Text(info.shift, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                    if (info.hours.isNotEmpty()) Text(info.hours, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
                    Text(info.weeks, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                }
            }
        }
    }

    private suspend fun load(context: Context): Info {
        val today = LocalDate.now()
        val monday = Rotation.mondayOf(today)
        val dao = AppDatabase.get(context).dao()
        val c = ConditionsStore(context).state.value
        val days = dao.daysBetween(monday.toEpochDay(), monday.plusDays(13).toEpochDay()).map { it.toModel() }.associateBy { it.date }
        fun week(m: LocalDate) = (0L..6L).mapNotNull { days[m.plusDays(it)]?.shift }.filter { it.isWork }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key?.label ?: "—"
        val e = days[today]
        val hours = e?.effectiveHours(c)?.let { "%02d:%02d – %02d:%02d".format(it.start.hour, it.start.minute, it.end.hour, it.end.minute) } ?: ""
        val title = today.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.forLanguageTag("es-ES"))).replaceFirstChar { it.uppercase() }
        return Info(
            dayTitle = "Hoy · $title",
            shift = e?.shift?.label ?: "Sin turno",
            hours = hours,
            weeks = "Esta semana: ${week(monday)} · Siguiente: ${week(monday.plusWeeks(1))}",
        )
    }
}

class ShiftWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ShiftWidget()
}
