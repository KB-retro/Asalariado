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
import com.asalariado.core.Conditions
import com.asalariado.core.DayEntry
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
    days: Map<LocalDate, DayEntry>,
    medical: List<MedicalVisit>,
    payslips: List<Payslip>,
    c: Conditions,
    onSavePayslip: (Payslip) -> Unit,
) {
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var editing by remember { mutableStateOf<MonthSummary?>(null) }
    val y = remember(year, days, medical, payslips, c) { Payroll.year(year, days.values, medical, payslips, c) }
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
                KeyValue("Asuntos propios", "${y.personalUsed} de ${y.personalAllowed} días")
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
        items(y.months, key = { it.month }) { m -> MonthRow(m) { editing = m } }
        item { Spacer(Modifier.height(24.dp)) }
    }

    editing?.let { m -> PayslipDialog(m, onDismiss = { editing = null }) { onSavePayslip(it); editing = null } }
}

@Composable
private fun MonthRow(m: MonthSummary, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(monthName(m.month), fontWeight = FontWeight.SemiBold)
                Text(hours(m.countedHours), style = MaterialTheme.typography.bodySmall)
            }
            KeyValue("Bruto esperado", money(m.expectedGross))
            if (m.extraPay > 0) KeyValue("  incluye paga extra", money(m.extraPay))
            if (m.nightPlus > 0) KeyValue("  plus nocturnidad", money(m.nightPlus))
            if (m.holidayPlus > 0) KeyValue("  plus festivos", money(m.holidayPlus))
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
