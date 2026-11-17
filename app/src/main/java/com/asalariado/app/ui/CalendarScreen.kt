package com.asalariado.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.filled.Insights
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asalariado.app.AppViewModel
import com.asalariado.core.Conditions
import com.asalariado.core.DayEntry
import com.asalariado.core.DayType
import com.asalariado.core.MedicalVisit
import com.asalariado.core.Payroll
import com.asalariado.core.Rotation
import com.asalariado.core.ShiftType
import com.asalariado.core.Stoppage
import com.asalariado.core.StoppageKind
import com.asalariado.core.effectiveHours
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(
    vm: AppViewModel,
    days: Map<LocalDate, DayEntry>,
    calendar: Map<LocalDate, DayType>,
    medical: List<MedicalVisit>,
    stoppages: List<Stoppage>,
    c: Conditions,
    onImport: () -> Unit,
    onboarding: @Composable () -> Unit = {},
) {
    // El mes que se ve vive en el ViewModel: no se pierde al cambiar de pestaña y empieza en el actual.
    val ym by vm.calendarMonth.collectAsState()
    var editing by remember { mutableStateOf<LocalDate?>(null) }
    var rotationOpen by remember { mutableStateOf(false) }
    // Punto rojo en los días con médico, paro o asamblea.
    val medicalDates = remember(medical, stoppages) { (medical.map { it.date } + stoppages.map { it.date }).toSet() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cabecera: el mes con sus flechas y las acciones.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.calendarMonth.value = ym.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(monthName(ym.monthValue), style = MaterialTheme.typography.headlineSmall)
                    Text("${ym.year}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.calendarMonth.value = ym.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ym != YearMonth.now()) {
                    FilledTonalButton(onClick = { vm.calendarMonth.value = YearMonth.now() }) { Text("Hoy") }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { rotationOpen = true }) {
                    Icon(Icons.Default.Autorenew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Rotación")
                }
                FilledTonalButton(onClick = onImport) {
                    Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Importar")
                }
            }

            if (ym == YearMonth.now()) TodayStrip(days, calendar, c)
            if (c.rotationAnchor == null) RotationBanner(onPick = { vm.startRotationFrom(LocalDate.now(), it) })

            val ui = LocalUiStyle.current
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = if (ui.flatCards) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = ui.cardBorder(),
                modifier = Modifier.fillMaxWidth().styleShadow(MaterialTheme.shapes.large, ui),
            ) {
                Box(Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                    MonthGrid(ym, days, calendar, medicalDates, onDay = { editing = it },
                        actions = WeekActions(
                            startFrom = { d, s -> vm.startRotationFrom(d, s) },
                            swap = { vm.swapWeek(it) },
                            set = { d, s -> vm.setWeek(d, s) },
                        ),
                    )
                }
            }
            MonthSummaryCard(ym, days, calendar, medical, stoppages, c)
            // La guía va después del calendario para que lo primero que se vea sea el mes.
            onboarding()
            Legend(showDayTypes = calendar.isNotEmpty())
            Spacer(Modifier.height(16.dp))
        }
    }

    editing?.let { date ->
        DayDialog(
            date = date,
            entry = days[date],
            type = calendar[date],
            c = c,
            strikeHours = stoppages.firstOrNull { it.date == date && it.kind == StoppageKind.STRIKE }?.hours ?: 0.0,
            assemblyHours = stoppages.firstOrNull { it.date == date && it.kind == StoppageKind.ASSEMBLY }?.hours ?: 0.0,
            overtimeHours = stoppages.firstOrNull { it.date == date && it.kind == StoppageKind.OVERTIME }?.hours ?: 0.0,
            onStoppage = { k, h -> vm.setStoppage(date, k, h) },
            onType = { vm.setCalendarType(date, it) },
            onDismiss = { editing = null },
            onSave = { e, to -> if (to != null) vm.saveDayRange(e, to) else vm.saveDay(e); editing = null },
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

/** Acciones del menú de cada semana. */
private class WeekActions(
    val startFrom: (LocalDate, ShiftType) -> Unit,
    val swap: (LocalDate) -> Unit,
    val set: (LocalDate, ShiftType) -> Unit,
)

private val weekdayHeaders = listOf("L", "M", "X", "J", "V", "S", "D")

@Composable
private fun MonthGrid(
    ym: YearMonth,
    days: Map<LocalDate, DayEntry>,
    calendar: Map<LocalDate, DayType>,
    medicalDates: Set<LocalDate>,
    onDay: (LocalDate) -> Unit,
    actions: WeekActions,
) {
    val first = Rotation.mondayOf(ym.atDay(1))
    val weeks = generateSequence(first) { it.plusWeeks(1) }.takeWhile { !it.isAfter(ym.atEndOfMonth()) }.toList()
    val today = LocalDate.now()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.width(30.dp))
            weekdayHeaders.forEachIndexed { i, w ->
                Text(w, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge,
                    color = if (i >= 5) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        weeks.forEach { monday ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                WeekChip(monday, days, actions)
                (0L..6L).forEach { i ->
                    val d = monday.plusDays(i)
                    // Los días de otros meses se dejan en blanco para no confundir.
                    if (YearMonth.from(d) != ym) {
                        Spacer(Modifier.weight(1f).height(54.dp))
                        return@forEach
                    }
                    DayCell(
                        date = d,
                        entry = days[d],
                        type = calendar[d],
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
private fun WeekChip(monday: LocalDate, days: Map<LocalDate, DayEntry>, actions: WeekActions) {
    var menu by remember { mutableStateOf(false) }
    val dominant = (0L..6L).mapNotNull { days[monday.plusDays(it)]?.shift }.filter { it.isWork }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    Box {
        // Columna estrecha: el turno de la semana; al tocarla se abre el menú de la semana.
        val bg = if (dominant != null) shiftColors(dominant).first else MaterialTheme.colorScheme.surfaceVariant
        Box(
            Modifier.width(30.dp).height(54.dp).clickable { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(bg.copy(alpha = 0.3f))
                    .border(1.5.dp, bg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(dominant?.code ?: "·", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Text(
                "Semana del ${monday.format(shortFmt)}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            DropdownMenuItem(
                text = { Text("Esta semana estoy de mañana (y alternar)") },
                onClick = { actions.startFrom(monday, ShiftType.MORNING); menu = false },
            )
            DropdownMenuItem(
                text = { Text("Esta semana estoy de tarde (y alternar)") },
                onClick = { actions.startFrom(monday, ShiftType.AFTERNOON); menu = false },
            )
            DropdownMenuItem(
                text = { Text("Cambiar sólo esta semana (mañana ↔ tarde)") },
                onClick = { actions.swap(monday); menu = false },
            )
            HorizontalDivider()
            listOf(ShiftType.NIGHT, ShiftType.SPLIT, ShiftType.VACATION).forEach { s ->
                DropdownMenuItem(text = { Text("Sólo esta semana: ${s.label}") }, onClick = { actions.set(monday, s); menu = false })
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    entry: DayEntry?,
    type: DayType?,
    inMonth: Boolean,
    isToday: Boolean,
    hasMedical: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shift = entry?.shift
    val typeColor = type?.takeIf { it != DayType.LABORABLE }?.let { dayTypeColor(it) }
    val (bg, fg) = when {
        shift != null -> shiftColors(shift)
        typeColor != null -> typeColor to if (type == DayType.JORNADA_CORTA || type == DayType.JORNADA_4H || type == DayType.ACTIVABLE_FR) Color.Black else Color.White
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val todayColor = MaterialTheme.colorScheme.primary
    Box(
        modifier.height(54.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .then(if (isToday) Modifier.border(3.dp, todayColor, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        // Número del día; el de hoy, en un círculo del color principal para que se vea de un vistazo.
        if (isToday) {
            Box(
                Modifier.align(Alignment.TopStart).size(20.dp).clip(CircleShape).background(todayColor),
                contentAlignment = Alignment.Center,
            ) { Text("${date.dayOfMonth}", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        } else {
            Text("${date.dayOfMonth}", color = fg, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp))
        }
        if (shift != null) {
            Text(shift.code, color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center).padding(top = 6.dp))
        } else if (type != null && type.code.isNotEmpty()) {
            Text(type.code, color = fg, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center).padding(top = 6.dp))
        }
        // Franja inferior con el tipo de día del calendario laboral cuando hay turno.
        if (shift != null && typeColor != null) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.7f).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(typeColor),
            )
        }
        if (hasMedical) {
            Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        }
    }
}

/** Franja de «hoy»: el turno de hoy con su horario y el de la semana que viene, en poco espacio. */
@Composable
private fun TodayStrip(days: Map<LocalDate, DayEntry>, calendar: Map<LocalDate, DayType>, c: Conditions) {
    val today = LocalDate.now()
    val entry = days[today]
    val monday = Rotation.mondayOf(today)
    val nextWeek = (0L..6L).mapNotNull { days[monday.plusWeeks(1).plusDays(it)]?.shift }.filter { it.isWork }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    val main = entry?.shift
    val color = main?.let { shiftColors(it).first } ?: MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(main?.code ?: "·", color = main?.let { shiftColors(it).second } ?: MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Hoy, " + today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d", ES)),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val title = when {
                main == null -> calendar[today]?.label ?: "Sin turno apuntado"
                else -> main.label
            }
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (main?.isWork == true) entry?.effectiveHours(c)?.let {
                Text("${it.start.hhmm()} – ${it.end.hhmm()}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (nextWeek != null) Column(horizontalAlignment = Alignment.End) {
            Text("Próx. semana", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(nextWeek.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RotationBanner(onPick: (ShiftType) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("¿De qué turno estás esta semana?", fontWeight = FontWeight.SemiBold)
            Text(
                "Las semanas siguientes se alternarán solas (mañana, tarde, mañana…) respetando festivos y vacaciones.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPick(ShiftType.MORNING) }) { Text("Mañana") }
                Button(onClick = { onPick(ShiftType.AFTERNOON) }) { Text("Tarde") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(showDayTypes: Boolean) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text(if (open) "Ocultar leyenda" else "Ver leyenda y ayuda") }
    if (!open) return
    Text(
        "Toca un día para cambiarlo (turno, horario, vacaciones, médico, horas extra…). Toca el círculo de la izquierda " +
            "de una semana para decir «esta semana estoy de mañana/tarde» o cambiar sólo esa semana.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (showDayTypes) {
        Text("Calendario laboral (franja inferior)", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DayType.entries.filter { it != DayType.LABORABLE }.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp, 10.dp).clip(RoundedCornerShape(3.dp)).background(dayTypeColor(t)))
                    Text(" ${t.label}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text("Turnos", style = MaterialTheme.typography.labelMedium)
    }
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
private fun MonthSummaryCard(
    ym: YearMonth,
    days: Map<LocalDate, DayEntry>,
    calendar: Map<LocalDate, DayType>,
    medical: List<MedicalVisit>,
    stoppages: List<Stoppage>,
    c: Conditions,
) {
    val s = remember(ym, days, calendar, medical, stoppages, c) {
        Payroll.month(ym, days.values, medical, c, null, calendar, stoppages = stoppages)
    }
    var detail by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("Horas", hours(s.workedHours), if (s.calendarHours > 0) "de ${hours(s.calendarHours)}" else null, Modifier.weight(1f))
        StatTile("Días", "${s.workedDays}", "trabajados", Modifier.weight(1f))
        StatTile("Neto", money(s.expectedNet).substringBefore(",") + " €", "a la cuenta", Modifier.weight(1f))
    }
    TextButton(onClick = { detail = !detail }) { Text(if (detail) "Ocultar detalle del mes" else "Ver detalle del mes") }
    if (detail) SectionCard("Detalle de ${monthName(ym.monthValue).lowercase()}", icon = Icons.Default.Insights) {
        KeyValue("Días trabajados", "${s.workedDays}")
        KeyValue("Horas trabajadas", hours(s.workedHours))
        if (s.calendarHours > 0) KeyValue("Horas según calendario laboral", hours(s.calendarHours))
        if (s.activableDays > 0) KeyValue("Activables trabajados", "${s.activableDays} (${hours(s.activableHours)})")
        if (s.strikeHours > 0) KeyValue("Sin cobrar (huelga, paros, médico sin justif.)", hours(s.strikeHours))
        if (s.assemblyHours > 0) KeyValue("Asambleas (sin prima)", hours(s.assemblyHours))
        if (s.overtimeHours > 0) KeyValue("Horas extra / compensadas", "${hours(s.overtimeHours)} · ${money(s.overtimePay)}")
        if (s.parentalDays > 0) KeyValue("Permiso por nacimiento", "${s.parentalDays} días")
        s.extra?.let { KeyValue("Paga extra (hacia el ${it.day})", money(it.gross)) }
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
    type: DayType?,
    c: Conditions,
    strikeHours: Double,
    assemblyHours: Double,
    overtimeHours: Double,
    onStoppage: (StoppageKind, Double) -> Unit,
    onType: (DayType?) -> Unit,
    onDismiss: () -> Unit,
    onSave: (DayEntry, LocalDate?) -> Unit,
    onDelete: () -> Unit,
) {
    var strike by remember { mutableStateOf(strikeHours) }
    var assembly by remember { mutableStateOf(assemblyHours) }
    var overtime by remember { mutableStateOf(overtimeHours) }
    var untilOn by remember { mutableStateOf(false) }
    var until by remember { mutableStateOf(date.plusWeeks(16).minusDays(1)) }
    var shift by remember { mutableStateOf(entry?.shift ?: ShiftType.MORNING) }
    var start by remember { mutableStateOf(entry?.start) }
    var end by remember { mutableStateOf(entry?.end) }
    var holiday by remember { mutableStateOf(entry?.holidayWorked ?: false) }
    var note by remember { mutableStateOf(entry?.note ?: "") }
    var dayType by remember { mutableStateOf(type) }
    var typeMenu by remember { mutableStateOf(false) }
    val def = c.hoursFor(shift)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(dayFmt).replaceFirstChar { it.uppercase() }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { typeMenu = true }) {
                        Text("Calendario laboral: ${dayType?.label ?: "sin definir"}")
                    }
                    DropdownMenu(typeMenu, { typeMenu = false }) {
                        DayType.entries.forEach { t ->
                            DropdownMenuItem(text = { Text(t.label) }, onClick = { dayType = t; typeMenu = false })
                        }
                        DropdownMenuItem(text = { Text("Sin definir") }, onClick = { dayType = null; typeMenu = false })
                    }
                }
                if (dayType?.activable == true) Text(
                    "Día activable: si te lo hacen trabajar, elige el turno (jornada de ${hours(c.normalDayHours)}). " +
                        "Contará como activable y se pagará como sábado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text("Turno", style = MaterialTheme.typography.labelLarge)
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
                when (shift) {
                    ShiftType.STRIKE -> Text(
                        "Día de huelga: no se cobra nada (ni salario ni prima) y se descuenta de la paga extra.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                    )
                    ShiftType.PARENTAL -> Text(
                        "Permiso por nacimiento: esos días no los paga la empresa y se descuentan de la paga extra. " +
                            "Marca «Aplicar hasta» para poner todo el permiso de una vez.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                    )
                    else -> {}
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(untilOn, { untilOn = it })
                    Text("Aplicar hasta…", style = MaterialTheme.typography.bodyMedium)
                }
                if (untilOn) DateButton(until, Modifier.fillMaxWidth(), "Hasta") { until = it }

                if (shift != ShiftType.PARENTAL && shift != ShiftType.STRIKE) {
                    NumberField("Horas extra / compensadas", overtime, Modifier.fillMaxWidth(), "h") { overtime = it }
                    Text(
                        "Se pagan a ${money(Payroll.overtimePrice(date, type, c))}/h (${
                            when {
                                date.dayOfWeek == java.time.DayOfWeek.SUNDAY || type == DayType.FESTIVO -> "domingo o festivo"
                                date.dayOfWeek == java.time.DayOfWeek.SATURDAY || type?.activable == true -> "sábado"
                                else -> "laborable"
                            }
                        }).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (shift.isWork) {
                    Text("Paros y asambleas", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("Horas de paro", strike, Modifier.weight(1f), "h") { strike = it }
                        NumberField("Horas asamblea", assembly, Modifier.weight(1f), "h") { assembly = it }
                    }
                    Text(
                        "Paro: esas horas no se cobran. Asamblea: se cobra el salario, no la prima. Admite medias horas (0,5).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(note, { note = it }, label = { Text("Nota") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (dayType != type) onType(dayType)
                // En jornada corta, si no se indica horario, se guarda la duración corta.
                val short = dayType == DayType.JORNADA_CORTA || dayType == DayType.JORNADA_4H
                val autoEnd = if (short && start == null && end == null && shift.isWork) {
                    def?.let { it.start to it.start.plusMinutes((Payroll.plannedHours(dayType!!, c) * 60).toLong()) }
                } else null
                if (strike != strikeHours) onStoppage(StoppageKind.STRIKE, if (shift.isWork) strike else 0.0)
                if (assembly != assemblyHours) onStoppage(StoppageKind.ASSEMBLY, if (shift.isWork) assembly else 0.0)
                if (overtime != overtimeHours) onStoppage(StoppageKind.OVERTIME, overtime)
                onSave(
                    DayEntry(date, shift, autoEnd?.first ?: start, autoEnd?.second ?: end, holiday && shift.isWork, note),
                    if (untilOn) until else null,
                )
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
                        "(se cambian en Condiciones). Si has importado el calendario laboral, manda él: " +
                        "festivos, vacaciones y puentes quedan libres y los días amarillos son de ${hours(c.shortDayHours)}.",
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
