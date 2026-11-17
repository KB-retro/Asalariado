package com.asalariado.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asalariado.core.Conditions
import com.asalariado.core.ShiftHours
import com.asalariado.core.ShiftType
import com.asalariado.core.TablaSalarial
import java.time.DayOfWeek

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionsScreen(c: Conditions, onSave: (Conditions) -> Unit) {
    var d by remember(c) { mutableStateOf(c) }
    var extraMonths by remember(c) { mutableStateOf(c.extraPayMonths.joinToString(", ")) }
    val changed = d != c

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Condiciones anuales", style = MaterialTheme.typography.titleLarge)
        Text(
            "Copia los datos de tu contrato y convenio. Con ellos se calcula lo que deberías cobrar cada mes.",
            style = MaterialTheme.typography.bodySmall,
        )

        SectionCard("Salario") {
            EscalonPicker(d.escalon) { n ->
                d = if (n == null) d.copy(escalon = null) else TablaSalarial.BELLOTA_2026.aplicar(d, n)
            }
            Text(
                "El escalón es tu nivel salarial dentro de la categoría (en la nómina: «ESCALÓN 10»). " +
                    "Al elegirlo se rellenan jornal, prima, prima por hora, nocturnidad (20 % del salario hora) y jornada de 1592 h.",
                style = MaterialTheme.typography.labelSmall,
            )
            NumberField("Jornal / salario base mensual", d.baseMonthly, Modifier.fillMaxWidth(), "€") {
                d = d.copy(baseMonthly = it, escalon = null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Prima mensual", d.bonusMonthly, Modifier.weight(1f), "€") { d = d.copy(bonusMonthly = it, escalon = null) }
                NumberField("Prima por hora", d.bonusPerHour, Modifier.weight(1f), "€") { d = d.copy(bonusPerHour = it, escalon = null) }
            }
            Text(
                "La prima por hora se descuenta en las horas de médico (de cualquier tipo) y en los días de libre disposición.",
                style = MaterialTheme.typography.labelSmall,
            )
            KeyValue("Bruto anual (jornal × pagas + prima × 12)", money(d.grossAnnualSalary))
            Text("Número de pagas", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(12, 14, 15).forEach { p ->
                    FilterChip(selected = d.payments == p, onClick = {
                        d = d.copy(payments = p)
                        extraMonths = when (p) { 14 -> "6, 12"; 15 -> "3, 6, 12"; else -> extraMonths }
                        d = d.copy(extraPayMonths = parseMonths(extraMonths))
                    }, label = { Text("$p") })
                }
            }
            if (d.payments > 12) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(d.proratedExtras, { d = d.copy(proratedExtras = it) })
                    Text("Pagas extra prorrateadas en cada mes")
                }
                if (!d.proratedExtras) OutlinedTextField(
                    extraMonths,
                    { extraMonths = it; d = d.copy(extraPayMonths = parseMonths(it)) },
                    label = { Text("Meses de paga extra (1–12)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("IRPF", d.irpfPercent, Modifier.weight(1f), "%") { d = d.copy(irpfPercent = it) }
                NumberField("Seg. Social", d.ssPercent, Modifier.weight(1f), "%") { d = d.copy(ssPercent = it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("EPSV (Geroa)", d.geroaPercent, Modifier.weight(1f), "%") { d = d.copy(geroaPercent = it) }
                NumberField("Seguro vida (empresa)", d.employerMonthlyExtras, Modifier.weight(1f), "€") { d = d.copy(employerMonthlyExtras = it) }
            }
            Text(
                "Todo esto viene en la nómina: IRPF; Seguridad Social = contingencias comunes 4,70 + desempleo y FP 1,65 + MEI 0,15 = 6,50 %. " +
                    "Si importas una nómina desde la pestaña Nómina se rellenan solos.",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        SectionCard("Jornada") {
            NumberField("Horas anuales de convenio", d.annualHours, Modifier.fillMaxWidth(), "h") { d = d.copy(annualHours = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Jornada normal", d.normalDayHours, Modifier.weight(1f), "h") { d = d.copy(normalDayHours = it) }
                NumberField("Jornada corta", d.shortDayHours, Modifier.weight(1f), "h") { d = d.copy(shortDayHours = it) }
            }
            KeyValue("Precio hora ordinaria", money(d.hourlyRate))
            Text("Días laborables", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { dow ->
                    val sel = dow in d.workDays
                    FilterChip(selected = sel, onClick = {
                        d = d.copy(workDays = if (sel) d.workDays - dow else d.workDays + dow)
                    }, label = { Text(listOf("L", "M", "X", "J", "V", "S", "D")[dow.value - 1]) })
                }
            }
            Text("Horario de cada turno", style = MaterialTheme.typography.labelLarge)
            listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.SPLIT).forEach { s ->
                val h = d.shiftHours[s]
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.label, Modifier.width(72.dp))
                    TimeField("Entrada", h?.start, Modifier.weight(1f)) { t ->
                        t?.let { d = d.copy(shiftHours = d.shiftHours + (s to ShiftHours(it, h?.end ?: it))) }
                    }
                    TimeField("Salida", h?.end, Modifier.weight(1f)) { t ->
                        t?.let { d = d.copy(shiftHours = d.shiftHours + (s to ShiftHours(h?.start ?: it, it))) }
                    }
                }
            }
        }

        SectionCard("Pluses y horas extra") {
            NumberField("Plus nocturnidad por hora (22–6 h)", d.nightBonusPerHour, Modifier.fillMaxWidth(), "€") { d = d.copy(nightBonusPerHour = it) }
            NumberField("Plus festivo por hora", d.holidayBonusPerHour, Modifier.fillMaxWidth(), "€") { d = d.copy(holidayBonusPerHour = it) }
            NumberField("Precio hora en sábado / festivo activable", d.activablePricePerHour, Modifier.fillMaxWidth(), "€") {
                d = d.copy(activablePricePerHour = it)
            }
            NumberField("Activables obligatorios al año", d.maxActivablesPerYear.toDouble(), Modifier.fillMaxWidth(), "días") {
                d = d.copy(maxActivablesPerYear = it.toInt())
            }
            NumberField("Precio hora extra (0 = hora ordinaria)", d.overtimePrice, Modifier.fillMaxWidth(), "€") { d = d.copy(overtimePrice = it) }
        }

        SectionCard("Permisos anuales") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Vacaciones", d.vacationDays.toDouble(), Modifier.weight(1f), "días") { d = d.copy(vacationDays = it.toInt()) }
                NumberField("Libre disposición", d.personalDays.toDouble(), Modifier.weight(1f), "días") { d = d.copy(personalDays = it.toInt()) }
            }
            NumberField("Médico general / acompañante al año", d.medicalHoursPerYear, Modifier.fillMaxWidth(), "h") { d = d.copy(medicalHoursPerYear = it) }
        }

        Button(onClick = { onSave(d) }, enabled = changed, modifier = Modifier.fillMaxWidth()) {
            Text(if (changed) "Guardar condiciones" else "Guardado")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EscalonPicker(selected: Int?, onSelect: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.let { "Escalón $it · tabla Bellota 2026" } ?: "Elegir escalón (tabla Bellota 2026)")
        }
        DropdownMenu(open, { open = false }) {
            TablaSalarial.BELLOTA_2026.escalones.forEach { e ->
                DropdownMenuItem(
                    text = { Text("Escalón ${e.numero}: ${money(e.salarioMes)} + prima ${money(e.primaMes)}") },
                    onClick = { onSelect(e.numero); open = false },
                )
            }
            DropdownMenuItem(text = { Text("Importes a mano") }, onClick = { onSelect(null); open = false })
        }
    }
}

private fun parseMonths(s: String): List<Int> =
    s.split(',', ' ', ';').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..12 }
