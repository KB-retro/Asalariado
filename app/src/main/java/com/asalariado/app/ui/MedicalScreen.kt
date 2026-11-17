package com.asalariado.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asalariado.app.AppViewModel
import com.asalariado.core.Conditions
import com.asalariado.core.MedicalKind
import com.asalariado.core.MedicalVisit
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun MedicalScreen(vm: AppViewModel, medical: List<MedicalVisit>, c: Conditions) {
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var editing by remember { mutableStateOf<MedicalVisit?>(null) }
    val list = medical.filter { it.date.year == year }
    val used = list.filter { it.kind.limited }.sumOf { it.hours }
    val specialist = list.filter { !it.kind.limited }.sumOf { it.hours }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = MedicalVisit(date = LocalDate.now(), start = LocalTime.of(9, 0), end = LocalTime.of(11, 0)) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Añadir cita") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader("Médico", "Citas y horas de permiso") { YearSwitcher(year) { year = it } }
            }
            item {
                val allowed = c.medicalHoursPerYear
                HeroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { if (allowed > 0) (used / allowed).toFloat().coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.size(84.dp),
                                color = LocalContentColor.current,
                                trackColor = LocalContentColor.current.copy(alpha = 0.22f),
                                strokeWidth = 8.dp,
                            )
                            Text(hours(used), style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.padding(start = 18.dp)) {
                            Text("Bolsa de médico general / acompañante", style = MaterialTheme.typography.labelLarge)
                            Text("Te quedan ${hours((allowed - used).coerceAtLeast(0.0))}", style = MaterialTheme.typography.headlineSmall)
                            Text("de ${hours(allowed)} · especialista: ${hours(specialist)}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                SectionCard("Médico general / acompañante", icon = Icons.Default.LocalHospital) {
                    val allowed = c.medicalHoursPerYear
                    KeyValue("Usadas", "${hours(used)} de ${hours(allowed)}")
                    if (allowed > 0) LinearProgressIndicator(
                        progress = { (used / allowed).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (used > allowed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    KeyValue("Disponibles", hours((allowed - used).coerceAtLeast(0.0)))

                    if (used > allowed) Text(
                        "Has superado las horas del convenio en ${hours(used - allowed)}. Revisa si te las pueden descontar.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                SectionCard("Especialista (propio)", icon = Icons.Default.MedicalServices) {
                    KeyValue("Horas este año", hours(specialist))
                    Text("Sin límite de horas.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (c.bonusPerHour > 0 && list.isNotEmpty()) item {
                val all = list.sumOf { it.hours }
                Text(
                    "En las horas de médico (de cualquier tipo) no se cobra la prima: ${hours(all)} × ${money(c.bonusPerHour)} = " +
                        "${money(all * c.bonusPerHour)} menos en las nóminas de este año (ya está en el cálculo).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (list.isEmpty()) item { Text("Aún no hay citas este año.", Modifier.padding(16.dp)) }
            items(list, key = { it.id }) { m ->
                Card(onClick = { editing = m }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.date.format(dayFmt).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Medium)
                            Text("${m.kind.label} · ${m.start.hhmm()}–${m.end.hhmm()}" + if (m.reason.isNotBlank()) " · ${m.reason}" else "",
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
    var kind by remember { mutableStateOf(m.kind) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (m.id == 0L) "Nueva cita médica" else "Cita médica") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MedicalKind.entries.forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) })
                }
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
                onClick = { onSave(m.copy(date = date, start = start!!, end = end!!, reason = reason, justified = justified, kind = kind)) },
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
