package com.asalariado.app.ui

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
    c: Conditions,
    onSavePayslip: (Payslip) -> Unit,
) {
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var editing by remember { mutableStateOf<MonthSummary?>(null) }
    val y = remember(year, days, calendar, medical, payslips, c) { Payroll.year(year, days.values, medical, payslips, c, calendar) }
    val today = LocalDate.now()
    val expectedToDate = Payroll.expectedHoursToDate(year, today, c)
    val countedToDate = y.months.filter { year < today.year || it.month <= today.monthValue }.sumOf { it.countedHours }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { year-- }) { Text("‹") }
                Text("Control $year", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { year++ }) { Text("›") }
            }
        }
        item {
            SectionCard("Jornada anual") {
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
            SectionCard("Permisos") {
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
            SectionCard("Salario") {
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
        items(y.months, key = { it.month }) { m -> MonthRow(m) { editing = m } }
        item { Spacer(Modifier.height(24.dp)) }
    }

    editing?.let { m -> PayslipDialog(m, onDismiss = { editing = null }) { onSavePayslip(it); editing = null } }

    val imp by vm.payslipImport.collectAsState()
    if (imp.loading || imp.error != null || imp.data != null) {
        PayslipImportDialog(imp, days, calendar, medical, c, onDismiss = vm::clearPayslipImport) { yy, mm, update ->
            vm.commitPayslip(yy, mm, update)
            year = yy
        }
    }
}

@Composable
private fun MonthRow(m: MonthSummary, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(monthName(m.month), fontWeight = FontWeight.SemiBold)
                Text(hours(m.countedHours), style = MaterialTheme.typography.bodySmall)
            }
            KeyValue("Jornal", money(m.baseGross))
            if (m.bonus > 0) KeyValue("Prima", money(m.bonus))
            if (m.bonusDiscount > 0) KeyValue("  sin prima ${hours(m.unpaidBonusHours)} (médico / libre disp.)", "−" + money(m.bonusDiscount))
            KeyValue("Bruto esperado", money(m.expectedGross))
            if (m.extraPay > 0) KeyValue("  incluye paga extra", money(m.extraPay))
            if (m.nightPlus > 0) KeyValue("  plus nocturnidad", money(m.nightPlus))
            if (m.holidayPlus > 0) KeyValue("  plus festivos", money(m.holidayPlus))
            if (m.activablePlus > 0) KeyValue("  ${m.activableDays} activables (precio sábado)", money(m.activablePlus))
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
}

@Composable
private fun PayslipImportDialog(
    imp: PayslipImport,
    days: Map<LocalDate, DayEntry>,
    calendar: Map<LocalDate, DayType>,
    medical: List<MedicalVisit>,
    c: Conditions,
    onDismiss: () -> Unit,
    onSave: (year: Int, month: Int, updateConditions: Boolean) -> Unit,
) {
    val p = imp.data
    var year by remember(p) { mutableIntStateOf(p?.year ?: LocalDate.now().year) }
    var month by remember(p) { mutableIntStateOf(p?.month ?: LocalDate.now().monthValue) }
    var update by remember(p) { mutableStateOf(true) }
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
                        val exp = Payroll.month(YearMonth.of(year, month), days.values, medical, c, null, calendar)
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
                            KeyValue("Esperado por la app", money(exp.expectedGross))
                            p.totalDevengos?.let { g ->
                                val d = g - exp.expectedGross
                                KeyValue("Diferencia bruto", money(d), diffColor(d))
                            }
                            KeyValue("Líquido a percibir", money(p.liquido))
                            KeyValue("Neto esperado", money(exp.expectedNet))
                            p.liquido?.let { n ->
                                val d = n - exp.expectedNet
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
            if (p != null) TextButton(onClick = { onSave(year, month, update) }) { Text("Guardar nómina") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (p != null) "Cancelar" else "Cerrar") } },
    )
}

@Composable
private fun PayslipDialog(m: MonthSummary, onDismiss: () -> Unit, onSave: (Payslip) -> Unit) {
    var gross by remember { mutableStateOf(m.payslip?.gross?.let { num(it) } ?: "") }
    var net by remember { mutableStateOf(m.payslip?.net?.let { num(it) } ?: "") }
    var note by remember { mutableStateOf(m.payslip?.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nómina de ${monthName(m.month).lowercase()} ${m.year}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Esperado: ${money(m.expectedGross)} bruto · ${money(m.expectedNet)} neto", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(gross, { gross = it }, label = { Text("Total devengado (bruto)") }, singleLine = true,
                    isError = gross.isNotBlank() && parseNum(gross) == null, suffix = { Text("€") })
                OutlinedTextField(net, { net = it }, label = { Text("Líquido a percibir (neto)") }, singleLine = true,
                    isError = net.isNotBlank() && parseNum(net) == null, suffix = { Text("€") })
                OutlinedTextField(note, { note = it }, label = { Text("Notas") })
                Text("Deja los campos vacíos para borrar la nómina.", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Payslip(m.year, m.month, parseNum(gross), parseNum(net), note)) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
