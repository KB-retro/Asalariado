package com.asalariado.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asalariado.app.AppViewModel
import com.asalariado.core.Conditions
import com.asalariado.core.MedicalVisit
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun MedicalScreen(vm: AppViewModel, medical: List<MedicalVisit>, c: Conditions) {
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var editing by remember { mutableStateOf<MedicalVisit?>(null) }
    val list = medical.filter { it.date.year == year }
    val used = list.sumOf { it.hours }
    val justified = list.filter { it.justified }.sumOf { it.hours }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = MedicalVisit(date = LocalDate.now(), start = LocalTime.of(9, 0), end = LocalTime.of(11, 0)) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Añadir cita") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { year-- }) { Text("‹") }
                    Text("Horas de médico $year", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { year++ }) { Text("›") }
                }
            }
            item {
                SectionCard("Bolsa anual") {
                    val allowed = c.medicalHoursPerYear
                    KeyValue("Usadas", "${hours(used)} de ${hours(allowed)}")
                    if (allowed > 0) LinearProgressIndicator(
                        progress = { (used / allowed).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (used > allowed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    KeyValue("Disponibles", hours((allowed - used).coerceAtLeast(0.0)))
                    KeyValue("Con justificante (cuentan como trabajadas)", hours(justified))
                    if (used > allowed) Text(
                        "Has superado las horas del convenio en ${hours(used - allowed)}. Revisa si te las pueden descontar.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (list.isEmpty()) item { Text("Aún no hay citas este año.", Modifier.padding(16.dp)) }
            items(list, key = { it.id }) { m ->
                Card(onClick = { editing = m }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.date.format(dayFmt).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Medium)
                            Text("${m.start.hhmm()}–${m.end.hhmm()}" + if (m.reason.isNotBlank()) " · ${m.reason}" else "",
                                style = MaterialTheme.typography.bodySmall)
                            if (!m.justified) Text("Sin justificante", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                        Text(hours(m.hours), fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    editing?.let { m ->
        MedicalDialog(m, onDismiss = { editing = null }, onSave = { vm.saveMedical(it); editing = null },
            onDelete = if (m.id != 0L) ({ vm.deleteMedical(m.id); editing = null }) else null)
    }
}

@Composable
private fun MedicalDialog(m: MedicalVisit, onDismiss: () -> Unit, onSave: (MedicalVisit) -> Unit, onDelete: (() -> Unit)?) {
    var date by remember { mutableStateOf(m.date) }
    var start by remember { mutableStateOf<LocalTime?>(m.start) }
    var end by remember { mutableStateOf<LocalTime?>(m.end) }
    var reason by remember { mutableStateOf(m.reason) }
    var justified by remember { mutableStateOf(m.justified) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (m.id == 0L) "Nueva cita médica" else "Cita médica") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton(date, Modifier.fillMaxWidth(), "Fecha") { date = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField("Desde", start, Modifier.weight(1f)) { start = it }
                    TimeField("Hasta", end, Modifier.weight(1f)) { end = it }
                }
                OutlinedTextField(reason, { reason = it }, label = { Text("Motivo (opcional)") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(justified, { justified = it })
                    Text("Tengo justificante")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = start != null && end != null,
                onClick = { onSave(m.copy(date = date, start = start!!, end = end!!, reason = reason, justified = justified)) },
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Borrar") } }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
