package com.asalariado.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import com.asalariado.app.AppViewModel
import com.asalariado.app.PayslipImport
import com.asalariado.core.Conditions
import com.asalariado.core.ExtraPaySummary
import com.asalariado.core.Stoppage
import androidx.compose.material3.CardDefaults
import java.time.YearMonth
import com.asalariado.core.DayEntry
import com.asalariado.core.DayType
import com.asalariado.core.MedicalVisit
import com.asalariado.core.MonthSummary
import com.asalariado.core.Payroll
import com.asalariado.core.Payslip
import java.time.LocalDate
import kotlin.math.abs

private val ok = Color(0xFF2E7D32)

@Composable
private fun diffColor(d: Double?): Color = when {
    d == null -> Color.Unspecified
    abs(d) < 1.0 -> ok
    d < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.secondary
}

@Composable
fun PayrollScreen(
    vm: AppViewModel,
    days: Map<LocalDate, DayEntry>,
    calendar: Map<LocalDate, DayType>,
    medical: List<MedicalVisit>,
    payslips: List<Payslip>,
    extraPayslips: List<Payslip>,
    stoppages: List<Stoppage>,
    c: Conditions,
    onSavePayslip: (Payslip) -> Unit,
) {
    var editingExtra by remember { mutableStateOf<ExtraPaySummary?>(null) }
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var editing by remember { mutableStateOf<MonthSummary?>(null) }
    val y = remember(year, days, calendar, medical, payslips, extraPayslips, stoppages, c) {
        Payroll.year(year, days.values, medical, payslips, c, calendar, extraPayslips, stoppages)
    }
    val today = LocalDate.now()
    val expectedToDate = Payroll.expectedHoursToDate(year, today, c)
    val countedToDate = y.months.filter { year < today.year || it.month <= today.monthValue }.sumOf { it.countedHours }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader("Nómina", "¿Cobras lo que te corresponde?") { YearSwitcher(year) { year = it } }
        }
        item {
            val m = y.months[(if (year == today.year) today.monthValue else 12) - 1]
            HeroCard {
                Text("${monthName(m.month)} ${m.year} · neto esperado", style = MaterialTheme.typography.labelLarge)
                Text(money(m.expectedNet), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("Bruto ${money(m.expectedGross)}", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    val d = m.diffNet ?: m.diffGross
                    when {
                        d == null -> Pill("Sin nómina apuntada", LocalContentColor.current.copy(alpha = 0.14f))
                        abs(d) < 1 -> Pill("✓ Cuadra con tu nómina", Color(0xFF2E7D32))
                        d < 0 -> Pill("Te faltan ${money(-d)}", Color(0xFFC62828))
                        else -> Pill("Cobras ${money(d)} de más", LocalContentColor.current.copy(alpha = 0.2f))
                    }
                    if (y.monthsWithPayslip > 0) Pill("Año: ${money(y.diffNet)}", LocalContentColor.current.copy(alpha = 0.14f))
                }
            }
        }
        item {
            SectionCard("Jornada anual", icon = Icons.Default.Schedule) {
                KeyValue("Horas del convenio", hours(y.annualHours))
                if (y.calendarHours > 0) KeyValue("Horas según calendario laboral", hours(y.calendarHours))
                KeyValue("Horas en calendario (trabajo + médico)", hours(y.countedHours))
                LinearProgressIndicator(
                    progress = { if (y.annualHours > 0) (y.countedHours / y.annualHours).toFloat().coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (year == today.year) {
                    KeyValue("Deberías llevar a hoy (aprox.)", hours(expectedToDate))
                    KeyValue("Llevas hasta este mes", hours(countedToDate))
                }
                val diff = y.countedHours - y.annualHours
                if (diff > 0) {
                    KeyValue("Exceso de jornada", hours(diff), MaterialTheme.colorScheme.error)
                    KeyValue("Valor a precio de hora extra", money(y.overtimeValue), MaterialTheme.colorScheme.error)
                    Text(
                        "Tu calendario supera las horas anuales del convenio: esas horas deberían pagarse como extra o compensarse con descanso.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    KeyValue("Horas que faltan por planificar", hours(-diff))
                }
                KeyValue("Precio hora ordinaria", money(c.hourlyRate))
            }
        }
        item {
            SectionCard("Permisos", icon = Icons.Default.BeachAccess) {
                KeyValue("Vacaciones", "${y.vacationUsed} de ${y.vacationAllowed} días",
                    if (y.vacationUsed > y.vacationAllowed) MaterialTheme.colorScheme.error else Color.Unspecified)
                KeyValue("Asuntos propios / libre disposición", "${y.personalUsed} de ${y.personalAllowed} días")
                KeyValue("Activables trabajados (sábados/F.R.)", "${y.activableUsed} de ${y.maxActivables} obligatorios",
                    if (y.activableUsed > y.maxActivables) MaterialTheme.colorScheme.secondary else Color.Unspecified)
                if (y.activableUsed > y.maxActivables) Text(
                    "Has trabajado más activables de los que te pueden obligar: los de más deberían ser voluntarios.",
                    style = MaterialTheme.typography.bodySmall,
                )
                KeyValue("Médico", "${hours(y.medicalHoursUsed)} de ${hours(y.medicalHoursAllowed)}",
                    if (y.medicalHoursUsed > y.medicalHoursAllowed) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        }
        item {
            SectionCard("Salario", icon = Icons.Default.Euro) {
                KeyValue("Bruto anual esperado", money(y.expectedGross))
                KeyValue("Neto anual estimado", money(y.expectedNet))
                if (y.monthsWithPayslip > 0) {
                    HorizontalDivider()
                    KeyValue("Nóminas introducidas", "${y.monthsWithPayslip}")
                    KeyValue("Bruto cobrado", money(y.receivedGross))
                    KeyValue("Diferencia bruto", money(y.diffGross), diffColor(y.diffGross))
                    KeyValue("Diferencia neto", money(y.diffNet), diffColor(y.diffNet))
                    if (y.diffGross < -1) Text(
                        "En los meses revisados has cobrado menos de lo esperado. Pide la explicación en la empresa o consulta con tu sindicato/gestoría.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "El neto es aproximado (IRPF y Seguridad Social según tus condiciones). Toca un mes para apuntar lo que pone tu nómina.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) vm.readPayslip(uri)
            }
            Button(onClick = { picker.launch(arrayOf("application/pdf", "image/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Importar nómina (PDF o captura)")
            }
        }
        items(y.months, key = { it.month }) { m -> MonthRow(m, onExtraClick = { editingExtra = m.extra }) { editing = m } }
        item { Spacer(Modifier.height(24.dp)) }
    }

    editing?.let { m ->
        PayslipDialog("Nómina de ${monthName(m.month).lowercase()} ${m.year}", m.year, m.month, m.expectedGross, m.expectedNet, m.payslip,
            onDismiss = { editing = null }) { onSavePayslip(it); editing = null }
    }
    editingExtra?.let { e ->
        PayslipDialog("Paga extra de ${monthName(e.month).lowercase()} ${e.year}", e.year, e.month, e.gross, e.net, e.payslip,
            onDismiss = { editingExtra = null }) { vm.saveExtraPayslip(it); editingExtra = null }
    }

    val imp by vm.payslipImport.collectAsState()
    if (imp.loading || imp.error != null || imp.data != null) {
        PayslipImportDialog(imp, days, calendar, medical, stoppages, c, onDismiss = vm::clearPayslipImport) { yy, mm, update, asExtra ->
            vm.commitPayslip(yy, mm, update, asExtra)
            year = yy
        }
    }
}

@Composable
private fun MonthRow(m: MonthSummary, onExtraClick: () -> Unit, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(monthName(m.month), fontWeight = FontWeight.SemiBold)
                    Text(hours(m.countedHours), style = MaterialTheme.typography.bodySmall)
                }
                KeyValue("Jornal", money(m.baseGross))
                if (m.bonus > 0) KeyValue("Prima", money(m.bonus))
                if (m.bonusDiscount > 0) KeyValue("  sin prima ${hours(m.unpaidBonusHours)} (médico / libre disp.)", "−" + money(m.bonusDiscount))
                if (m.assemblyDiscount > 0) KeyValue("  asamblea ${hours(m.assemblyHours)} (sin prima)", "−" + money(m.assemblyDiscount))
                if (m.strikeDeduction > 0) KeyValue("  huelga / paros ${hours(m.strikeHours)}", "−" + money(m.strikeDeduction))
                if (m.parentalDeduction > 0) KeyValue("  permiso nacimiento ${m.parentalDays} días", "−" + money(m.parentalDeduction))
                if (m.nightPlus > 0) KeyValue("  plus nocturnidad", money(m.nightPlus))
                if (m.holidayPlus > 0) KeyValue("  plus festivos", money(m.holidayPlus))
                if (m.activablePlus > 0) KeyValue("  ${m.activableDays} activables (precio sábado)", money(m.activablePlus))
                KeyValue("Bruto esperado", money(m.expectedGross))
                KeyValue("Neto estimado", money(m.expectedNet))
                m.payslip?.let { p ->
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    if (p.gross != null) KeyValue("Bruto en nómina", money(p.gross))
                    if (p.net != null) KeyValue("Neto en nómina", money(p.net))
                    m.diffGross?.let { KeyValue("Diferencia bruto", money(it), diffColor(it)) }
                    m.diffNet?.let { KeyValue("Diferencia neto", money(it), diffColor(it)) }
                    if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        m.extra?.let { e ->
            // La paga extra va en su propio recibo, hacia el día indicado.
            Card(
                onClick = onExtraClick,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Paga extra de ${monthName(e.month).lowercase()}", fontWeight = FontWeight.SemiBold)
                        Text("hacia el ${e.day} (aprox.)", style = MaterialTheme.typography.bodySmall)
                    }
                    if (e.lostDays > 0) Text(
                        "Se descuentan ${e.lostDays} de ${e.periodDays} días del semestre (permiso por nacimiento / huelga).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    KeyValue("Bruto esperado", money(e.gross))
                    KeyValue("Neto estimado (sólo IRPF)", money(e.net))
                    e.payslip?.let { p ->
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        if (p.gross != null) KeyValue("Bruto cobrado", money(p.gross))
                        if (p.net != null) KeyValue("Neto cobrado", money(p.net))
                        e.diffGross?.let { KeyValue("Diferencia bruto", money(it), diffColor(it)) }
                        e.diffNet?.let { KeyValue("Diferencia neto", money(it), diffColor(it)) }
                    } ?: Text("Toca para apuntar lo que te han pagado.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PayslipImportDialog(
    imp: PayslipImport,
    days: Map<LocalDate, DayEntry>,
    calendar: Map<LocalDate, DayType>,
    medical: List<MedicalVisit>,
    stoppages: List<Stoppage>,
    c: Conditions,
    onDismiss: () -> Unit,
    onSave: (year: Int, month: Int, updateConditions: Boolean, asExtra: Boolean) -> Unit,
) {
    val p = imp.data
    var year by remember(p) { mutableIntStateOf(p?.year ?: LocalDate.now().year) }
    var month by remember(p) { mutableIntStateOf(p?.month ?: LocalDate.now().monthValue) }
    var update by remember(p) { mutableStateOf(true) }
    var asExtra by remember(p) { mutableStateOf(p?.isExtra == true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nómina importada") },
        text = {
            when {
                imp.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                imp.error != null -> Text(imp.error!!, color = MaterialTheme.colorScheme.error)
                p != null -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item { MonthSelector(year, month) { y, m -> year = y; month = m } }
                    item {
                        val ms = Payroll.month(YearMonth.of(year, month), days.values, medical, c, null, calendar, stoppages = stoppages)
                        // Si es la paga extra se compara con lo esperado de la extra.
                        val expGross = if (asExtra) ms.extra?.gross ?: c.baseMonthly else ms.expectedGross
                        val expNet = if (asExtra) ms.extra?.net ?: c.baseMonthly * (1 - c.irpfPercent / 100) else ms.expectedNet
                        val exp = ms
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            p.escalon?.let { KeyValue("Escalón", "$it") }
                            p.jornal?.let { KeyValue("Jornal", money(it)) }
                            p.prima?.let { KeyValue("Prima", money(it)) }
                            val actH = p.activableHours
                            val dtoH = p.bonusDiscountHours
                            if (actH != null) KeyValue(
                                "Plus sábados y FR", "${num(actH)} h × ${money(p.activablePrice)} = ${money(p.activableAmount)}",
                            )
                            if (dtoH != null && dtoH > 0) KeyValue(
                                "Descuento de prima", "${num(dtoH)} h × ${money(p.bonusHourPrice)}",
                            )
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            KeyValue("Total devengos", money(p.totalDevengos))
                            KeyValue("Esperado por la app", money(expGross))
                            p.totalDevengos?.let { g ->
                                val d = g - expGross
                                KeyValue("Diferencia bruto", money(d), diffColor(d))
                            }
                            KeyValue("Líquido a percibir", money(p.liquido))
                            KeyValue("Neto esperado", money(expNet))
                            p.liquido?.let { n ->
                                val d = n - expNet
                                KeyValue("Diferencia neto", money(d), diffColor(d))
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            val pct = listOfNotNull(
                                p.irpfPercent?.let { "IRPF ${num(it)} %" },
                                p.ssPercent?.let { "SS ${num(it)} %" },
                                p.geroaPercent?.let { "Geroa ${num(it)} %" },
                            ).joinToString(" · ")
                            if (pct.isNotEmpty()) Text(pct, style = MaterialTheme.typography.bodySmall)
                            if (dtoH != null && abs(dtoH - exp.unpaidBonusHours) > 0.01) Text(
                                "En la nómina se descuentan ${num(dtoH)} h de prima y en tu calendario hay " +
                                    "${num(exp.unpaidBonusHours)} h de médico/libre disposición este mes. A veces se descuentan " +
                                    "en la nómina del mes siguiente; si no, apúntalas en Médico.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(asExtra, { asExtra = it })
                                Text("Es el recibo de la paga extra (julio / diciembre)", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(update, { update = it })
                                Text("Actualizar mis condiciones (escalón, jornal, prima, IRPF, SS, Geroa, precio sábado)",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (p != null) TextButton(onClick = { onSave(year, month, update, asExtra) }) {
                Text(if (asExtra) "Guardar paga extra" else "Guardar nómina")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (p != null) "Cancelar" else "Cerrar") } },
    )
}

@Composable
private fun PayslipDialog(
    title: String,
    year: Int,
    month: Int,
    expectedGross: Double,
    expectedNet: Double,
    existing: Payslip?,
    onDismiss: () -> Unit,
    onSave: (Payslip) -> Unit,
) {
    var gross by remember { mutableStateOf(existing?.gross?.let { num(it) } ?: "") }
    var net by remember { mutableStateOf(existing?.net?.let { num(it) } ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Esperado: ${money(expectedGross)} bruto · ${money(expectedNet)} neto", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(gross, { gross = it }, label = { Text("Total devengado (bruto)") }, singleLine = true,
                    isError = gross.isNotBlank() && parseNum(gross) == null, suffix = { Text("€") })
                OutlinedTextField(net, { net = it }, label = { Text("Líquido a percibir (neto)") }, singleLine = true,
                    isError = net.isNotBlank() && parseNum(net) == null, suffix = { Text("€") })
                OutlinedTextField(note, { note = it }, label = { Text("Notas") })
                Text("Deja los campos vacíos para borrarla.", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Payslip(year, month, parseNum(gross), parseNum(net), note)) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
