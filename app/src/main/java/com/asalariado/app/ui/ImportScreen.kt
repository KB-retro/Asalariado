package com.asalariado.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asalariado.app.AppViewModel
import com.asalariado.app.ImportRow
import com.asalariado.core.DayEntry
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: AppViewModel, existing: Map<LocalDate, DayEntry>, initialUri: Uri?, onClose: () -> Unit) {
    val st by vm.import.collectAsState()
    var showRaw by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if (uris.isNotEmpty()) vm.runOcr(uris)
    }
    LaunchedEffect(initialUri) { initialUri?.let { vm.runOcr(listOf(it)) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importar horario") },
                navigationIcon = {
                    IconButton(onClick = { vm.clearImport(); onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Elige una o varias capturas de tu cuadrante. La app lee el texto en el móvil (sin enviarlo a ningún sitio), " +
                    "detecta fechas y turnos (Mañana/Tarde/Noche, M/T/N, Libre, horarios tipo 06:00-14:00) " +
                    "y te deja revisarlos antes de guardarlos.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PhotoLibrary, null)
                Spacer(Modifier.size(8.dp))
                Text("Elegir capturas")
            }

            MonthSelector(st.year, st.month) { y, m -> vm.setImportMonth(y, m) }
            Text(
                "Mes que se usa cuando la captura sólo pone el número del día.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                st.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                st.error != null -> Text(st.error!!, color = MaterialTheme.colorScheme.error)
                st.rawText.isNotEmpty() && st.rows.isEmpty() -> {
                    Text(
                        "No he encontrado fechas con turno en la imagen. Prueba a recortar la captura para que sólo " +
                            "salga el cuadrante, o revisa el texto leído.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (st.rows.isNotEmpty()) {
                val selected = st.rows.count { it.include }
                Text("${st.rows.size} días detectados · toca uno para corregirlo", fontWeight = FontWeight.SemiBold)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(st.rows) { i, row ->
                        ImportRowView(row, existing[row.day.date], onToggle = { vm.updateImportRow(i, row.copy(include = it)) }) {
                            editIndex = i
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (st.rawText.isNotEmpty()) OutlinedButton(onClick = { showRaw = true }) { Text("Texto leído") }
                    Button(onClick = { vm.commitImport(); onClose() }, enabled = selected > 0, modifier = Modifier.weight(1f)) {
                        Text("Guardar $selected días")
                    }
                }
                Spacer(Modifier.height(8.dp))
            } else if (st.rawText.isNotEmpty()) {
                OutlinedButton(onClick = { showRaw = true }) { Text("Ver texto leído") }
            }
        }
    }

    editIndex?.let { i ->
        val row = st.rows.getOrNull(i)
        if (row != null) {
            AlertDialog(
                onDismissRequest = { editIndex = null },
                title = { Text(row.day.date.format(dayFmt)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShiftPicker(row.day.shift, { vm.updateImportRow(i, row.copy(day = row.day.copy(shift = it))) })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TimeField("Entrada", row.day.start, Modifier.weight(1f)) {
                                vm.updateImportRow(i, row.copy(day = row.day.copy(start = it)))
                            }
                            TimeField("Salida", row.day.end, Modifier.weight(1f)) {
                                vm.updateImportRow(i, row.copy(day = row.day.copy(end = it)))
                            }
                        }
                        DateButton(row.day.date, label = "Fecha") { vm.updateImportRow(i, row.copy(day = row.day.copy(date = it))) }
                        Text("Leído: ${row.day.raw}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                confirmButton = { TextButton(onClick = { editIndex = null }) { Text("Hecho") } },
            )
        }
    }

    if (showRaw) {
        AlertDialog(
            onDismissRequest = { showRaw = false },
            title = { Text("Texto reconocido") },
            text = { LazyColumn { item { Text(st.rawText, style = MaterialTheme.typography.bodySmall) } } },
            confirmButton = { TextButton(onClick = { showRaw = false }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun ImportRowView(row: ImportRow, current: DayEntry?, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val (bg, fg) = shiftColors(row.day.shift)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(row.include, onToggle)
            Column(Modifier.weight(1f)) {
                Text(row.day.date.format(dayFmt).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Medium)
                val times = if (row.day.start != null) " · ${row.day.start!!.hhmm()}–${row.day.end?.hhmm() ?: "?"}" else ""
                Text(row.day.shift.label + times, style = MaterialTheme.typography.bodySmall)
                if (current != null && current.shift != row.day.shift) {
                    Text(
                        "Ahora en el calendario: ${current.shift.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(bg), contentAlignment = Alignment.Center) {
                Text(row.day.shift.code, color = fg, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MonthSelector(year: Int, month: Int, onChange: (Int, Int) -> Unit) {
    var monthMenu by remember { mutableStateOf(false) }
    var yearMenu by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            OutlinedButton(onClick = { monthMenu = true }) { Text(monthName(month)) }
            DropdownMenu(monthMenu, { monthMenu = false }) {
                (1..12).forEach { m -> DropdownMenuItem(text = { Text(monthName(m)) }, onClick = { onChange(year, m); monthMenu = false }) }
            }
        }
        Box {
            OutlinedButton(onClick = { yearMenu = true }) { Text("$year") }
            val now = LocalDate.now().year
            DropdownMenu(yearMenu, { yearMenu = false }) {
                (now - 2..now + 2).forEach { y -> DropdownMenuItem(text = { Text("$y") }, onClick = { onChange(y, month); yearMenu = false }) }
            }
        }
    }
}
