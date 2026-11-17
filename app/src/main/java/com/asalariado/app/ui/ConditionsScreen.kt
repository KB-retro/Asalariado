package com.asalariado.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionsScreen(c: Conditions, onSave: (Conditions) -> Unit) {
    var d by remember(c) { mutableStateOf(c) }
    val changed = d != c
    // Neto y bruto de un mes normal con las condiciones que se están editando.
    val sample = remember(d) { com.asalariado.core.Payroll.month(YearMonth.of(YearMonth.now().year, 5), emptyList(), emptyList(), d, null) }
    val netNow = sample.expectedNet
    val grossNow = sample.expectedGross
    // Se guarda solo: poco después de cada cambio y, si quedaba algo, al salir de la pantalla.
    LaunchedEffect(d) {
        if (d != c) {
            delay(700)
            onSave(d)
        }
    }
    val latest by rememberUpdatedState(d)
    val saved by rememberUpdatedState(c)
    DisposableEffect(Unit) { onDispose { if (latest != saved) onSave(latest) } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Contrato", "Tu contrato y convenio: con esto se calcula lo que deberías cobrar")

        ExpandableCard("Salario", "≈ ${money(netNow)} netos al mes" + (d.escalon?.let { " · escalón $it" } ?: ""), Icons.Default.Euro, initiallyOpen = true) {
            // Lo primero: lo que llega a la cuenta cada mes con estos datos.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Te llega al mes", money(netNow), "neto aproximado", Modifier.weight(1f))
                StatTile("Bruto al mes", money(grossNow), "sin pagas extra", Modifier.weight(1f))
            }
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
            NumberField("Salario por hora (huelga)", d.effectiveSalaryPerHour, Modifier.fillMaxWidth(), "€") {
                d = d.copy(salaryPerHour = it, escalon = null)
            }
            Text(
                "La prima por hora se descuenta en las horas de médico (de cualquier tipo), asambleas y días de libre disposición. " +
                    "En huelga y paros se descuentan salario + prima por hora.",
                style = MaterialTheme.typography.labelSmall,
            )
            KeyValue("Bruto anual (jornal × pagas + prima × 12)", money(d.grossAnnualSalary))
            Text("Número de pagas", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(12, 14, 15).forEach { p ->
                    FilterChip(selected = d.payments == p, onClick = {
                        d = d.copy(payments = p)
                        d = when (p) {
                            14 -> d.copy(extraPayMonths = listOf(7, 12), extraPayDays = listOf(20, 15))
                            15 -> d.copy(extraPayMonths = listOf(3, 7, 12), extraPayDays = listOf(20, 20, 15))
                            else -> d
                        }
                    }, label = { Text("$p") })
                }
            }
            if (d.payments > 12) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(d.proratedExtras, { d = d.copy(proratedExtras = it) })
                    Text("Pagas extra prorrateadas en cada mes")
                }
                if (!d.proratedExtras) {
                    Text("Cada paga extra se cobra aparte de la nómina, hacia el día indicado:", style = MaterialTheme.typography.labelLarge)
                    d.extraPayMonths.take(d.payments - 12).forEachIndexed { i, m ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Paga ${i + 1}", Modifier.width(56.dp))
                            NumberField("Mes", m.toDouble(), Modifier.weight(1f)) { v ->
                                val mm = v.toInt().coerceIn(1, 12)
                                d = d.copy(extraPayMonths = d.extraPayMonths.toMutableList().also { it[i] = mm })
                            }
                            NumberField("Día aprox.", (d.extraPayDays.getOrNull(i) ?: 20).toDouble(), Modifier.weight(1f)) { v ->
                                val dd = v.toInt().coerceIn(1, 31)
                                val days = d.extraPayDays.toMutableList()
                                while (days.size <= i) days += 20
                                days[i] = dd
                                d = d.copy(extraPayDays = days)
                            }
                        }
                    }
                    Text(
                        "La paga de julio se genera de enero a junio y la de diciembre de julio a diciembre: los días de " +
                            "permiso por nacimiento y de huelga de cada semestre se descuentan de su paga.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
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

        ExpandableCard("Jornada y turnos", "${hours(d.annualHours)} al año · horarios de mañana, tarde y noche", Icons.Default.Schedule) {
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

        ExpandableCard("Pluses y horas extra", "Sábado ${money(d.overtimeSaturdayPrice)}/h · noche ${money(d.nightShiftBonus)}/turno", Icons.Default.Star) {
            NumberField("Plus nocturnidad por hora (22–6 h)", d.nightBonusPerHour, Modifier.fillMaxWidth(), "€") { d = d.copy(nightBonusPerHour = it) }
            NumberField("Plus festivo por hora", d.holidayBonusPerHour, Modifier.fillMaxWidth(), "€") { d = d.copy(holidayBonusPerHour = it) }
            NumberField("Precio hora en sábado / festivo activable", d.activablePricePerHour, Modifier.fillMaxWidth(), "€") {
                d = d.copy(activablePricePerHour = it)
            }
            NumberField("Activables obligatorios al año", d.maxActivablesPerYear.toDouble(), Modifier.fillMaxWidth(), "días") {
                d = d.copy(maxActivablesPerYear = it.toInt())
            }
            NumberField("Plus turno de noche (por jornada > 4 h)", d.nightShiftBonus, Modifier.fillMaxWidth(), "€") { d = d.copy(nightShiftBonus = it) }
            Text("Horas extra / compensadas (se apuntan en cada día):", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Laborable", d.overtimeWeekdayPrice, Modifier.weight(1f), "€") { d = d.copy(overtimeWeekdayPrice = it) }
                NumberField("Sábado", d.overtimeSaturdayPrice, Modifier.weight(1f), "€") { d = d.copy(overtimeSaturdayPrice = it) }
                NumberField("Dom./fest.", d.overtimeSundayPrice, Modifier.weight(1f), "€") { d = d.copy(overtimeSundayPrice = it) }
            }
            NumberField("Precio hora extra si no hay otro (0 = hora ordinaria)", d.overtimePrice, Modifier.fillMaxWidth(), "€") { d = d.copy(overtimePrice = it) }
            Text(
                "Con el escalón elegido se rellenan según la tabla: laborable = salario hora + 10,73 €; " +
                    "sábado = salario anual / 1592 + 42 %; domingo o festivo = + 66 %; turno de noche 7,92 €.",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        ExpandableCard("Antigüedad", d.seniorityDate?.let { "${d.quinquenios(YearMonth.now())} quinquenios · ${money(d.quinquenioMonthly)}/mes cada uno" } ?: "Sin fecha de antigüedad", Icons.Default.Schedule) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DateButton(d.seniorityDate ?: LocalDate.now(), Modifier.weight(1f), "Desde") { d = d.copy(seniorityDate = it) }
                NumberField("Por quinquenio", d.quinquenioMonthly, Modifier.weight(1f), "€/mes") { d = d.copy(quinquenioMonthly = it) }
            }
            val q = d.quinquenios(YearMonth.now())
            Text(
                (if (d.seniorityDate == null) "Pon la fecha de antigüedad de tu nómina. " else "Llevas $q quinquenio${if (q == 1) "" else "s"}. ") +
                    "Cada 5 años se suma el importe al jornal, a las pagas extra y a la base de cotización.",
                style = MaterialTheme.typography.labelSmall,
            )
            d.seniorityDate?.let { from ->
                val next = from.plusYears(((q + 1) * 5).toLong())
                Text("Próximo quinquenio: ${next.format(shortFmt)} ${next.year}", style = MaterialTheme.typography.bodySmall)
            }
        }

        ExpandableCard("Cómo descuenta la empresa", if (d.discountsNextMonth) "Los descuentos llegan el mes siguiente" else "Los descuentos van en el mismo mes", Icons.Default.Euro) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(d.discountsNextMonth, { d = d.copy(discountsNextMonth = it) })
                Text("Los descuentos (médico, libre disposición, asambleas, huelga) llegan en la nómina del mes siguiente",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }

        ExpandableCard("Permisos anuales", "${d.vacationDays} vacaciones · ${d.personalDays} libre disp. · ${hours(d.medicalHoursPerYear)} médico", Icons.Default.BeachAccess) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Vacaciones", d.vacationDays.toDouble(), Modifier.weight(1f), "días") { d = d.copy(vacationDays = it.toInt()) }
                NumberField("Libre disposición", d.personalDays.toDouble(), Modifier.weight(1f), "días") { d = d.copy(personalDays = it.toInt()) }
            }
            NumberField("Médico general / acompañante al año", d.medicalHoursPerYear, Modifier.fillMaxWidth(), "h") { d = d.copy(medicalHoursPerYear = it) }
        }

        Text(
            if (changed) "Guardando…" else "Los cambios se guardan solos.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
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

