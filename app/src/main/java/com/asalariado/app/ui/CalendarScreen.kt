package com.asalariado.app.ui

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
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asalariado.app.AppViewModel
import com.asalariado.core.Conditions
import com.asalariado.core.DayEntry
import com.asalariado.core.MedicalVisit
import com.asalariado.core.Payroll
import com.asalariado.core.Rotation
import com.asalariado.core.ShiftType
import com.asalariado.core.effectiveHours
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(vm: AppViewModel, days: Map<LocalDate, DayEntry>, medical: List<MedicalVisit>, c: Conditions, onImport: () -> Unit) {
    var ymText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(ymText)
    var editing by remember { mutableStateOf<LocalDate?>(null) }
    var rotationOpen by remember { mutableStateOf(false) }
    val medicalDates = remember(medical) { medical.map { it.date }.toSet() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { rotationOpen = true }) {
                    Icon(Icons.Default.Autorenew, contentDescription = "Rotación de turnos")
                }
                ExtendedFloatingActionButton(
                    onClick = onImport,
                    icon = { Icon(Icons.Default.AddPhotoAlternate, null) },
                    text = { Text("Subir captura") },
                )
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ymText = ym.minusMonths(1).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior")
                }
                Text(
                    "${monthName(ym.monthValue)} ${ym.year}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { ymText = ym.plusMonths(1).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente")
                }
            }
            TextButton(onClick = { ymText = YearMonth.now().toString() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Ir a hoy")
            }

            MonthGrid(ym, days, medicalDates, onDay = { editing = it }, onWeek = { d, s -> vm.setWeek(d, s) })
            Legend()
            MonthSummaryCard(ym, days, medical, c)
            Text(
                "Toca la columna «Sem» para marcar una semana entera de mañana o de tarde, " +
                    "y un día para cambiarlo (vacaciones, horario distinto, festivo…).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(120.dp))
        }
    }

    editing?.let { date ->
        DayDialog(
            date = date,
            entry = days[date],
            c = c,
            onDismiss = { editing = null },
            onSave = { vm.saveDay(it); editing = null },
            onDelete = { vm.deleteDay(date); editing = null },
        )
    }
    if (rotationOpen) {
        RotationDialog(c, ym, onDismiss = { rotationOpen = false }) { anchor, cycle, from, to, overwrite ->
            vm.applyRotation(anchor, cycle, from, to, overwrite)
            rotationOpen = false
        }
    }
}

private val weekdayHeaders = listOf("L", "M", "X", "J", "V", "S", "D")

@Composable
private fun MonthGrid(
    ym: YearMonth,
    days: Map<LocalDate, DayEntry>,
    medicalDates: Set<LocalDate>,
    onDay: (LocalDate) -> Unit,
    onWeek: (LocalDate, ShiftType) -> Unit,
) {
    val first = Rotation.mondayOf(ym.atDay(1))
    val weeks = generateSequence(first) { it.plusWeeks(1) }.takeWhile { !it.isAfter(ym.atEndOfMonth()) }.toList()
    val today = LocalDate.now()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Sem", Modifier.width(40.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            weekdayHeaders.forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            }
        }
        weeks.forEach { monday ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                WeekChip(monday, days, onWeek)
                (0L..6L).forEach { i ->
                    val d = monday.plusDays(i)
                    DayCell(
                        date = d,
                        entry = days[d],
                        inMonth = YearMonth.from(d) == ym,
                        isToday = d == today,
                        hasMedical = d in medicalDates,
                        modifier = Modifier.weight(1f),
                        onClick = { onDay(d) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekChip(monday: LocalDate, days: Map<LocalDate, DayEntry>, onWeek: (LocalDate, ShiftType) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val dominant = (0L..6L).mapNotNull { days[monday.plusDays(it)]?.shift }.filter { it.isWork }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    Box {
        val (bg, fg) = if (dominant != null) shiftColors(dominant) else shiftColors(ShiftType.OFF)
        Box(
            Modifier.width(40.dp).height(52.dp).clip(RoundedCornerShape(8.dp))
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(8.dp))
                .background(bg.copy(alpha = 0.35f))
                .clickable { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("S${monday.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", fontSize = 10.sp)
                Text(dominant?.code ?: "·", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Text(
                "Semana del ${monday.format(shortFmt)}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.SPLIT, ShiftType.VACATION).forEach { s ->
                DropdownMenuItem(text = { Text("Toda la semana: ${s.label}") }, onClick = { onWeek(monday, s); menu = false })
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    entry: DayEntry?,
    inMonth: Boolean,
    isToday: Boolean,
    hasMedical: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shift = entry?.shift
    val (bg, fg) = if (shift != null) shiftColors(shift) else MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier.height(52.dp).alpha(if (inMonth) 1f else 0.4f).clip(RoundedCornerShape(8.dp)).background(bg)
            .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        Text("${date.dayOfMonth}", color = fg, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopStart))
        if (shift != null) {
            Text(shift.code, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.align(Alignment.Center))
        }
        if (entry?.start != null || entry?.holidayWorked == true) {
            Text(if (entry?.holidayWorked == true) "F" else "⏱", color = fg, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomStart))
        }
        if (hasMedical) {
            Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ShiftType.entries.forEach { s ->
            val (bg, fg) = shiftColors(s)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(bg), contentAlignment = Alignment.Center) {
                    Text(s.code, color = fg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Text(" ${s.label}", style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
            Text(" Médico", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MonthSummaryCard(ym: YearMonth, days: Map<LocalDate, DayEntry>, medical: List<MedicalVisit>, c: Conditions) {
    val s = remember(ym, days, medical, c) { Payroll.month(ym, days.values, medical, c, null) }
    SectionCard("Resumen de ${monthName(ym.monthValue).lowercase()}") {
        KeyValue("Días trabajados", "${s.workedDays}")
        KeyValue("Horas trabajadas", hours(s.workedHours))
        if (s.medicalHours > 0) KeyValue("Horas de médico (justificadas)", hours(s.medicalHours))
        if (s.nightHours > 0) KeyValue("Horas nocturnas", hours(s.nightHours))
        if (s.holidayHours > 0) KeyValue("Horas en festivo", hours(s.holidayHours))
        val counts = s.shiftCounts.entries.filter { it.value > 0 }.sortedBy { it.key.ordinal }
            .joinToString("  ") { "${it.key.label}: ${it.value}" }
        if (counts.isNotEmpty()) Text(counts, style = MaterialTheme.typography.bodySmall)
        KeyValue("Bruto estimado", money(s.expectedGross))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftPicker(selected: ShiftType, onSelect: (ShiftType) -> Unit, options: List<ShiftType> = ShiftType.entries) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { s ->
            FilterChip(selected = s == selected, onClick = { onSelect(s) }, label = { Text(s.label) })
        }
    }
}

@Composable
private fun DayDialog(
    date: LocalDate,
    entry: DayEntry?,
    c: Conditions,
    onDismiss: () -> Unit,
    onSave: (DayEntry) -> Unit,
    onDelete: () -> Unit,
) {
    var shift by remember { mutableStateOf(entry?.shift ?: ShiftType.MORNING) }
    var start by remember { mutableStateOf(entry?.start) }
    var end by remember { mutableStateOf(entry?.end) }
    var holiday by remember { mutableStateOf(entry?.holidayWorked ?: false) }
    var note by remember { mutableStateOf(entry?.note ?: "") }
    val def = c.hoursFor(shift)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(dayFmt).replaceFirstChar { it.uppercase() }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShiftPicker(shift, { shift = it; start = null; end = null })
                if (shift.isWork) {
                    Text(
                        "Horario${if (def != null) " (por defecto ${def.start.hhmm()}–${def.end.hhmm()})" else ""}. " +
                            "Déjalo vacío para usar el del turno.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField("Entrada", start, Modifier.weight(1f)) { start = it }
                        TimeField("Salida", end, Modifier.weight(1f)) { end = it }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(holiday, { holiday = it })
                        Text("Festivo trabajado")
                    }
                    val eff = DayEntry(date, shift, start, end).effectiveHours(c)
                    if (eff != null) Text("Total: ${hours(com.asalariado.core.hoursBetween(eff.start, eff.end))}")
                }
                OutlinedTextField(note, { note = it }, label = { Text("Nota") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(DayEntry(date, shift, start, end, holiday && shift.isWork, note))
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (entry != null) TextButton(onClick = onDelete) { Text("Borrar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun RotationDialog(
    c: Conditions,
    ym: YearMonth,
    onDismiss: () -> Unit,
    onApply: (anchor: LocalDate, cycle: List<ShiftType>, from: LocalDate, to: LocalDate, overwrite: Boolean) -> Unit,
) {
    val cycles = listOf(
        listOf(ShiftType.MORNING, ShiftType.AFTERNOON),
        listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT),
        listOf(ShiftType.MORNING),
        listOf(ShiftType.AFTERNOON),
        listOf(ShiftType.NIGHT),
    )
    var cycle by remember { mutableStateOf(cycles.firstOrNull { it == c.rotation } ?: cycles[0]) }
    var anchor by remember { mutableStateOf(Rotation.mondayOf(ym.atDay(1))) }
    var startShift by remember { mutableStateOf(cycle.first()) }
    var from by remember { mutableStateOf(ym.atDay(1)) }
    var to by remember { mutableStateOf(LocalDate.of(ym.year, 12, 31)) }
    var overwrite by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rotación de turnos") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ciclo semanal", style = MaterialTheme.typography.labelLarge)
                cycles.forEach { cy ->
                    FilterChip(
                        selected = cy == cycle,
                        onClick = { cycle = cy; if (startShift !in cy) startShift = cy.first() },
                        label = { Text(cy.joinToString(" → ") { it.label }) },
                    )
                }
                Text("¿Qué turno haces en esta semana?", style = MaterialTheme.typography.labelLarge)
                DateButton(anchor, label = "Semana del") { anchor = Rotation.mondayOf(it) }
                ShiftPicker(startShift, { startShift = it }, cycle)
                Text("Rellenar el calendario", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateButton(from, Modifier.weight(1f), "Desde") { from = it }
                    DateButton(to, Modifier.weight(1f), "Hasta") { to = it }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(overwrite, { overwrite = it })
                    Text("Sobrescribir días editados a mano o importados", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Días laborables: ${c.workDays.sortedBy { it.value }.joinToString(" ") { weekdayHeaders[it.value - 1] }} " +
                        "(se cambian en Condiciones).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Gira el ciclo para que la semana elegida empiece con el turno marcado.
                val idx = cycle.indexOf(startShift).coerceAtLeast(0)
                val rotated = cycle.drop(idx) + cycle.take(idx)
                onApply(anchor, rotated, minOf(from, to), maxOf(from, to), overwrite)
            }) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
