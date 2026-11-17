package com.asalariado.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Notifications
import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalContext
import com.asalariado.app.work.AutoPrefs
import com.asalariado.app.work.Scheduler
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Fingerprint
import com.asalariado.app.AppLock
import com.asalariado.app.MainActivity
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asalariado.app.AppViewModel
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel, prefs: UiPrefs) {
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("Ajustes", "Seguridad, nóminas, avisos, copias y aspecto")

        SectionCard("Seguridad", icon = Icons.Default.Fingerprint) {
            val ctx = LocalContext.current
            val activity = ctx as? MainActivity
            val available = remember { AppLock.available(ctx) }
            var lockOn by remember { mutableStateOf(AppLock.enabled(ctx)) }
            var hide by remember { mutableStateOf(AppLock.hideInRecents(ctx)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(lockOn, { want ->
                    if (want) {
                        // Se pide la huella o el PIN una vez para comprobar que funciona antes de activarlo.
                        activity?.confirmLock { AppLock.setEnabled(ctx, true); lockOn = true }
                    } else {
                        AppLock.setEnabled(ctx, false); lockOn = false
                    }
                }, enabled = available || lockOn)
                Spacer(Modifier.width(10.dp))
                Text("Bloquear la app con la huella o el PIN del móvil", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                if (available) "Se pedirá al abrir la app y al volver a ella tras más de un minuto fuera."
                else "Para usarlo, configura primero un bloqueo de pantalla (PIN, patrón o huella) en los ajustes del móvil.",
                style = MaterialTheme.typography.bodySmall,
                color = if (available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(hide, { on -> AppLock.setHideInRecents(ctx, on); hide = on; activity?.applySecureFlag() })
                Spacer(Modifier.width(10.dp))
                Text("Ocultar la app en «recientes»", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Tapa tus datos en la lista de apps abiertas. Mientras esté activo no se pueden hacer capturas de la app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "La contraseña de las nóminas y los PDF guardados están cifrados con una clave que no sale del móvil.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Nóminas en PDF", icon = Icons.Default.Lock) {
            val saved by vm.payslipPassword.collectAsState()
            var pass by remember(saved) { mutableStateOf(saved ?: "") }
            var visible by remember { mutableStateOf(false) }
            Text(
                "Si la empresa os manda las nóminas en PDF con contraseña, ponla aquí: la app abrirá todas las que importes. " +
                    "Se guarda cifrada en el móvil.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                pass, { pass = it },
                label = { Text("Contraseña de las nóminas") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Mostrar")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.savePayslipPassword(pass.ifBlank { null }) }, enabled = pass != (saved ?: "")) { Text("Guardar") }
                if (saved != null) OutlinedButton(onClick = { vm.savePayslipPassword(null) }) { Text("Quitar") }
            }
        }

        SectionCard("Avisos y widget", icon = Icons.Default.Notifications) {
            val ctx = LocalContext.current
            var on by remember { mutableStateOf(AutoPrefs.notificationsOn(ctx)) }
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                on = granted
                AutoPrefs.setNotifications(ctx, granted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(on, { want ->
                    if (want && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else { on = want; AutoPrefs.setNotifications(ctx, want) }
                })
                Spacer(Modifier.width(10.dp))
                Text("Avisarme a las 20:00 si mañana cambio de turno, el día de la paga extra y si falta importar la nómina",
                    style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Widget: mantén pulsado el fondo de la pantalla de inicio → Widgets → Asalariado para ver tu turno de hoy y el de la semana.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard("Copia de seguridad", icon = Icons.Default.Backup) {
            Text(
                "Guarda todos tus datos (turnos, calendario laboral, médico, nóminas y condiciones) en un archivo. " +
                    "Úsalo antes de cambiar de móvil o si alguna vez tienes que reinstalar la app.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exporter.launch("asalariado-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Backup, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Guardar")
                }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Restaurar")
                }
            }
            val ctx = LocalContext.current
            var folder by remember { mutableStateOf(AutoPrefs.backupFolder(ctx)) }
            val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    ctx.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    AutoPrefs.setBackupFolder(ctx, uri)
                    folder = uri
                    Scheduler.schedule(ctx)
                }
            }
            Text("Copia automática semanal", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            Text(
                if (folder == null) "Elige una carpeta (mejor una que se sincronice con la nube) y la app guardará ahí una copia cada semana."
                else "Carpeta elegida. Última copia: ${AutoPrefs.lastBackup(ctx) ?: "pendiente"}.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { folderPicker.launch(null) }) { Text(if (folder == null) "Elegir carpeta" else "Cambiar carpeta") }
                if (folder != null) TextButton(onClick = {
                    AutoPrefs.setBackupFolder(ctx, null); folder = null; Scheduler.schedule(ctx)
                }) { Text("Desactivar") }
            }
            Text(
                "Además, Android guarda los datos de la app en tu copia de Google si la tienes activada en el móvil.",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        ExpandableCard("Aspecto", "Tema ${prefs.palette.label} · ${prefs.mode.label} · estilo ${prefs.style.label}", Icons.Default.Palette) {
            Text("Tema de color", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            run {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Palette.entries.filter { it != Palette.DINAMICO || dynamicColorAvailable }.forEach { p ->
                        PaletteSwatch(p, selected = prefs.palette == p) { vm.saveUi(prefs.copy(palette = p)) }
                    }
                }
            }

            Text("Modo", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            run {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        FilterChip(selected = prefs.mode == m, onClick = { vm.saveUi(prefs.copy(mode = m)) }, label = { Text(m.label) })
                    }
                }
            }

            Text("Estilo", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            run {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    UiStyle.entries.forEach { st ->
                        StylePreview(st, selected = prefs.style == st) {
                            // Como en Baby Registry: Píxel va con su fuente y al dejarlo se vuelve a Nunito.
                            val font = when {
                                st == UiStyle.PIXEL -> AppFont.PIXELIFY
                                prefs.font == AppFont.PIXELIFY -> AppFont.NUNITO
                                else -> prefs.font
                            }
                            vm.saveUi(prefs.copy(style = st, font = font))
                        }
                    }
                }
            }

            Text("Fuente", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            run {
                AppFont.entries.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { vm.saveUi(prefs.copy(font = f)) }
                            .background(if (prefs.font == f) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            f.label,
                            fontFamily = f.family,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (prefs.font == f) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text("Nómina 2.136,13 €", fontFamily = f.family, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (prefs.font == f) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Text("Fondo", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            run {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Backdrop.entries.forEach { b ->
                        BackdropPreview(b, selected = prefs.backdrop == b) { vm.saveUi(prefs.copy(backdrop = b)) }
                    }
                }
            }

        }
        Spacer(Modifier.height(24.dp))
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("¿Restaurar copia?") },
            text = { Text("Se sustituirán todos los datos actuales por los de la copia.") },
            confirmButton = { TextButton(onClick = { vm.importBackup(uri); confirmRestore = null }) { Text("Restaurar") } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PaletteSwatch(p: Palette, selected: Boolean, onClick: () -> Unit) {
    val colors = if (p == Palette.DINAMICO) {
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
    } else listOf(p.primary, p.tertiary)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.size(56.dp).clip(CircleShape)
                .background(Brush.linearGradient(colors))
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(if (p == Palette.DINAMICO) MaterialTheme.colorScheme.secondary else p.secondary))
            if (selected) Icon(Icons.Default.Check, null, tint = Color.White)
        }
        Text(p.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun StylePreview(st: UiStyle, selected: Boolean, onClick: () -> Unit) {
    val spec = uiStyleSpec(st)
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(86.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.size(78.dp, 64.dp)
                .clip(spec.shapes.medium)
                .background(if (selected) cs.primaryContainer else cs.surfaceVariant)
                .then(
                    when {
                        selected -> Modifier.border(2.dp, cs.primary, spec.shapes.medium)
                        spec.border > 0.dp -> Modifier.border(spec.border, if (spec.strongBorder) cs.onSurface else cs.outline, spec.shapes.medium)
                        else -> Modifier
                    },
                )
                .padding(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(22.dp).clip(spec.icon).background(cs.primary))
                Box(Modifier.size(44.dp, 10.dp).clip(spec.pill).background(cs.tertiary))
            }
        }
        Text(st.label, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun BackdropPreview(b: Backdrop, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.size(64.dp, 96.dp).clip(RoundedCornerShape(14.dp))
                .border(
                    BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    RoundedCornerShape(14.dp),
                ),
        ) {
            AppBackground(b) {}
            if (selected) Icon(Icons.Default.Check, null, Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.primary)
        }
        Text(b.label, style = MaterialTheme.typography.labelSmall)
    }
}
