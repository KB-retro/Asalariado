package com.asalariado.app.ui

import android.os.Build
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
        ScreenHeader("Ajustes", "Aspecto de la app y copias de seguridad")

        SectionCard("Tema de color", icon = Icons.Default.Palette) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Palette.entries.filter { it != Palette.DINAMICO || Build.VERSION.SDK_INT >= 31 }.forEach { p ->
                    PaletteSwatch(p, selected = prefs.palette == p) { vm.saveUi(prefs.copy(palette = p)) }
                }
            }
        }

        SectionCard("Modo", icon = Icons.Default.DarkMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(selected = prefs.mode == m, onClick = { vm.saveUi(prefs.copy(mode = m)) }, label = { Text(m.label) })
                }
            }
        }

        SectionCard("Fondo", icon = Icons.Default.Wallpaper) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Backdrop.entries.forEach { b ->
                    BackdropPreview(b, selected = prefs.backdrop == b) { vm.saveUi(prefs.copy(backdrop = b)) }
                }
            }
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
