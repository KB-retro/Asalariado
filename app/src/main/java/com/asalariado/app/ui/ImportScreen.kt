package com.asalariado.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asalariado.app.AppViewModel
import com.asalariado.app.ImportRow
import com.asalariado.core.AnnualCalendar
import com.asalariado.core.Conditions
import com.asalariado.core.DayEntry
import com.asalariado.core.DayType
import com.asalariado.core.Payroll
import java.time.YearMonth
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: AppViewModel, existing: Map<LocalDate, DayEntry>, c: Conditions, initialUri: Uri?, onClose: () -> Unit) {
    val st by vm.import.collectAsState()
    var showRaw by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if (uris.isNotEmpty()) vm.runOcr(uris)
    }
    // Calendario anual: acepta el PDF directamente o una imagen.
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.runOcr(listOf(uri))
    }
    LaunchedEffect(initialUri) { initialUri?.let { vm.runOcr(listOf(it)) } }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("Importar") },
                navigationIcon = {
                    IconButton(onClick = { vm.clearImport(); onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TabRow(selectedTabIndex = if (st.annualMode) 0 else 1) {
                Tab(selected = st.annualMode, onClick = { vm.setImportMode(true) }, text = { Text("Calendario anual") })
                Tab(selected = !st.annualMode, onClick = { vm.setImportMode(false) }, text = { Text("Cuadrante de turnos") })
            }
            if (st.annualMode) {
                Text(
                    "Sube el calendario laboral del año entero (el PDF del convenio o una captura). La app busca los 12 meses " +
                        "y lee el color de cada día: festivos, sábados/F.R., vacaciones, jornadas de 6 h, activables y puentes.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = { docPicker.launch(arrayOf("application/pdf", "image/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PhotoLibrary, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Elegir PDF o imagen del año")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Año:", style = MaterialTheme.typography.bodyMedium)
                    YearSelector(st.annual?.year ?: st.annualYear ?: LocalDate.now().year) { vm.setAnnualYear(it) }
                    Text("(se detecta solo si viene en el calendario)", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text(
                    "Elige una o varias capturas de tu cuadrante de turnos. La app lee en el móvil (sin enviarlas a ningún " +
                        "sitio) las fechas y turnos (Mañana/Tarde/Noche, M/T/N, Libre, 06:00-14:00…) y te deja revisarlos.",
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
            }

            when {
                st.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                st.error != null -> Text(st.error!!, color = MaterialTheme.colorScheme.error)
                st.annual != null || st.annualMode -> {}
                st.rawText.isNotEmpty() && st.rows.isEmpty() -> {
                    Text(
                        "No he encontrado fechas con turno en la imagen. Prueba a recortar la captura para que sólo " +
                            "salga el cuadrante, o revisa el texto leído.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            st.annual?.let { annual ->
                AnnualPreview(annual, st.yearDetected, c, Modifier.weight(1f)) { useHours, fill ->
                    vm.commitAnnual(useHours, fill); onClose()
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
            } else if (st.rawText.isNotEmpty() && st.annual == null && !st.loading) {
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
fun YearSelector(year: Int, onChange: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { menu = true }) { Text("$year") }
        val now = LocalDate.now().year
        DropdownMenu(menu, { menu = false }) {
            (now - 1..now + 2).forEach { y -> DropdownMenuItem(text = { Text("$y") }, onClick = { onChange(y); menu = false }) }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnnualPreview(
    annual: AnnualCalendar,
    yearDetected: Boolean,
    c: Conditions,
    modifier: Modifier,
    onSave: (useHours: Boolean, fillShifts: Boolean) -> Unit,
) {
    var useHours by remember { mutableStateOf(true) }
    var fill by remember { mutableStateOf(c.rotationAnchor != null) }
    val total = annual.days.values.sumOf { Payroll.plannedHours(it, c) }
    val free = c.personalDays * c.normalDayHours
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "Calendario laboral ${annual.year} · ${annual.months.size} meses detectados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (yearDetected) "Año ${annual.year} leído del propio calendario: se guardará en ${annual.year}."
                else "No pone el año: se guardará en ${annual.year}. Cámbialo arriba si no es ese.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (yearDetected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
            Text("Comprueba que los colores coinciden con tu calendario; luego podrás cambiar cualquier día tocándolo.",
                style = MaterialTheme.typography.bodySmall)
            val missing = (1..12).filter { it !in annual.months }
            if (missing.isNotEmpty()) Text(
                "No he encontrado: ${missing.joinToString(", ") { monthName(it).lowercase() }}. Guarda lo que sí ha salido " +
                    "y vuelve a importar una captura recortada de ese mes: se añadirá al resto del año.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayType.entries.filter { it != DayType.LABORABLE }.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(dayTypeColor(t)))
                        Text(" ${t.label}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        items(annual.months) { m ->
            val ym = YearMonth.of(annual.year, m)
            val types = (1..ym.lengthOfMonth()).map { annual.days[ym.atDay(it)] }
            val lab = types.count { it?.workDay == true }
            val h = types.filterNotNull().sumOf { Payroll.plannedHours(it, c) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${monthName(m)} · $lab laborables · ${hours(h)}", fontWeight = FontWeight.Medium)
                    MiniMonth(ym, annual)
                }
            }
        }
        item {
            SectionCard("Jornada anual") {
                KeyValue("Horas según calendario", hours(total))
                if (free > 0) KeyValue("Menos ${c.personalDays} días de libre disposición", "−" + hours(free))
                KeyValue("Jornada anual", hours(total - free))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(useHours, { useHours = it })
                    Text("Usar ${hours(total - free)} como horas anuales de convenio", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(fill, { fill = it })
                    Text("Rellenar mis turnos de ${annual.year} con la rotación de mañana/tarde", style = MaterialTheme.typography.bodySmall)
                }
                if (fill && c.rotationAnchor == null) Text(
                    "Aún no has dicho qué semana empiezas de mañana: se tomará la primera semana como mañana. " +
                        "Puedes cambiarlo luego con el botón de rotación.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        item {
            Button(onClick = { onSave(useHours, fill) }, modifier = Modifier.fillMaxWidth()) {
                Text("Guardar calendario laboral")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MiniMonth(ym: YearMonth, annual: AnnualCalendar) {
    val offset = ym.atDay(1).dayOfWeek.value - 1
    val cells = List(offset) { null } + (1..ym.lengthOfMonth()).toList()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (0 until 7).forEach { i ->
                    val day = week.getOrNull(i)
                    val t = day?.let { annual.days[ym.atDay(it)] }
                    val bg = t?.takeIf { it != DayType.LABORABLE }?.let { dayTypeColor(it) }
                        ?: MaterialTheme.colorScheme.surface
                    Box(
                        Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(3.dp))
                            .background(if (day == null) Color.Transparent else bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day != null) Text(
                            "$day",
                            fontSize = 10.sp,
                            color = when (t) {
                                null, DayType.LABORABLE -> MaterialTheme.colorScheme.onSurface
                                DayType.JORNADA_CORTA, DayType.JORNADA_4H, DayType.ACTIVABLE_FR -> Color.Black
                                else -> Color.White
                            },
                        )
                    }
                }
            }
        }
    }
}
