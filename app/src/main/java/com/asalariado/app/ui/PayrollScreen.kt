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
import com.asalariado.app.ImportedPayslip
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.draw.clip
import com.asalariado.core.Conditions
import com.asalariado.app.data.Report
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    // Dos vistas para no amontonar: el mes (lo que importa cada mes) y el año (totales y gráficas).
    var yearView by remember { mutableStateOf(false) }
    var month by remember { mutableIntStateOf(if (year == today.year) today.monthValue else 12) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.readPayslips(uris)
    }
    val importPayslips = { picker.launch(arrayOf("application/pdf", "image/*")) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader("Nómina") {
                FilledTonalButton(onClick = importPayslips) {
                    Icon(Icons.Default.UploadFile, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Importar")
                }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !yearView, onClick = { yearView = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Mes") }
                SegmentedButton(selected = yearView, onClick = { yearView = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Año") }
            }
        }
        if (!yearView) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (month == 1) { year--; month = 12 } else month-- }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior")
                    }
                    Text("${monthName(month)} $year", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { if (month == 12) { year++; month = 1 } else month++ }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente")
                    }
                }
            }
            val m = y.months[month - 1]
            item {
                HeroCard {
                    Text("Deberías cobrar (neto)", style = MaterialTheme.typography.labelLarge)
                    Text(money(m.expectedNet), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("Bruto ${money(m.expectedGross)}", style = MaterialTheme.typography.bodyLarge)
                    val d = m.diffNet ?: m.diffGross
                    Box(Modifier.padding(top = 4.dp)) {
                        when {
                            d == null -> Pill("Sin nómina apuntada", LocalContentColor.current.copy(alpha = 0.14f))
                            abs(d) < 1 -> Pill("✓ Cuadra con tu nómina", Color(0xFF2E7D32))
                            d < 0 -> Pill("Te faltan ${money(-d)}", Color(0xFFC62828))
                            else -> Pill("Cobras ${money(d)} de más", LocalContentColor.current.copy(alpha = 0.2f))
                        }
                    }
                }
            }
            item { PayslipCard(m, onEdit = { editing = m }, onImport = importPayslips) }
            m.extra?.let { e -> item { ExtraCard(e) { editingExtra = e } } }
            item { BreakdownCard(m) }
        } else {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { YearSwitcher(year) { year = it } } }
            item {
                SectionCard("Total del año", icon = Icons.Default.Euro) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StatTile("Neto esperado", money(y.expectedNet).substringBefore(","), null, Modifier.weight(1f))
                        StatTile("Neto cobrado", if (y.monthsWithPayslip > 0) money(y.receivedNet).substringBefore(",") else "—",
                            "${y.monthsWithPayslip} nóminas", Modifier.weight(1f))
                    }
                    if (y.monthsWithPayslip > 0) {
                        KeyValue("Diferencia (meses con nómina)", money(y.diffNet), diffColor(y.diffNet))
                        if (y.diffNet < -1) Text(
                            "Has cobrado menos de lo esperado. Pide la explicación en la empresa o consulta con el sindicato.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item {
                SectionCard("Mes a mes", icon = Icons.Default.CalendarMonth) {
                    y.months.forEach { mm ->
                        CompactMonthRow(mm) { month = mm.month; yearView = false }
                    }
                }
            }
            item {
                SectionCard("Gráficas", icon = Icons.Default.BarChart) {
                    Text("Neto por mes", style = MaterialTheme.typography.labelLarge)
                    MonthBars(
                        expected = y.months.map { it.expectedNet },
                        actual = y.months.map { it.payslip?.net },
                        expectedLabel = "Esperado", actualLabel = "Cobrado",
                    )
                    Text("Horas por mes", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    MonthBars(
                        expected = y.months.map { maxOf(it.calendarHours, it.countedHours) },
                        actual = y.months.map { it.countedHours.takeIf { h -> h > 0 } },
                        expectedLabel = if (y.calendarHours > 0) "Calendario" else "Total", actualLabel = "Trabajadas + médico",
                    )
                }
            }
            item {
                SectionCard("Jornada anual", icon = Icons.Default.Schedule) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StatTile("Convenio", hours(y.annualHours), null, Modifier.weight(1f))
                        StatTile("En calendario", hours(y.countedHours), "trabajo + médico", Modifier.weight(1f))
                    }
                    LinearProgressIndicator(
                        progress = { if (y.annualHours > 0) (y.countedHours / y.annualHours).toFloat().coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (year == today.year) KeyValue("Deberías llevar a hoy / llevas", "${hours(expectedToDate)} / ${hours(countedToDate)}")
                    val diff = y.countedHours - y.annualHours
                    if (diff > 0) {
                        KeyValue("Exceso de jornada", "${hours(diff)} · ${money(y.overtimeValue)}", MaterialTheme.colorScheme.error)
                        Text("Esas horas deberían pagarse como extra o compensarse con descanso.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                SectionCard("Permisos", icon = Icons.Default.BeachAccess) {
                    KeyValue("Vacaciones", "${y.vacationUsed} de ${y.vacationAllowed} días",
                        if (y.vacationUsed > y.vacationAllowed) MaterialTheme.colorScheme.error else Color.Unspecified)
                    KeyValue("Libre disposición", "${y.personalUsed} de ${y.personalAllowed} días")
                    KeyValue("Activables trabajados", "${y.activableUsed} de ${y.maxActivables}",
                        if (y.activableUsed > y.maxActivables) MaterialTheme.colorScheme.secondary else Color.Unspecified)
                    KeyValue("Médico general / acomp.", "${hours(y.medicalHoursUsed)} de ${hours(y.medicalHoursAllowed)}",
                        if (y.medicalHoursUsed > y.medicalHoursAllowed) MaterialTheme.colorScheme.error else Color.Unspecified)
                }
            }
            item {
                val ctx = LocalContext.current
                val scope = rememberCoroutineScope()
                OutlinedButton(onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { Report.yearPdf(ctx, y, c) }
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
                        val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { ctx.startActivity(Intent.createChooser(send, "Compartir informe $year")) }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Compartir informe PDF de $year") }
            }
        }
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
    if (imp.loading || imp.items.isNotEmpty()) {
        PayslipImportDialog(vm, imp, days, calendar, medical, stoppages, c, onDismiss = vm::clearPayslipImport) { update ->
            imp.items.firstOrNull { it.data != null }?.let { year = it.year }
            vm.commitPayslips(update)
        }
    }
}

/** La nómina cobrada de un mes: lo que pone y la diferencia, o cómo apuntarla. */
@Composable
private fun PayslipCard(m: MonthSummary, onEdit: () -> Unit, onImport: () -> Unit) {
    SectionCard("Tu nómina", icon = Icons.Default.Description) {
        val p = m.payslip
        if (p == null) {
            Text("Aún no la has apuntado. Impórtala (PDF o captura) o escribe lo que pone.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImport) { Text("Importar") }
                OutlinedButton(onClick = onEdit) { Text("A mano") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Neto cobrado", money(p.net), m.diffNet?.let { "dif. ${money(it)}" }, Modifier.weight(1f))
                StatTile("Bruto", money(p.gross), m.diffGross?.let { "dif. ${money(it)}" }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("Editar") }
                PdfLink(m.year, m.month, extra = false)
            }
        }
    }
}

/** La paga extra, que va en su propio recibo. */
@Composable
private fun ExtraCard(e: ExtraPaySummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Paga extra · hacia el ${e.day} de ${monthName(e.month).lowercase()}", style = MaterialTheme.typography.titleMedium)
            KeyValue("Deberías cobrar (neto)", money(e.net))
            if (e.lostDays > 0) Text("Descontados ${e.lostDays} días de permiso o huelga del semestre.", style = MaterialTheme.typography.bodySmall)
            val p = e.payslip
            if (p != null) {
                KeyValue("Cobrado (neto)", money(p.net))
                e.diffNet?.let { KeyValue("Diferencia", money(it), diffColor(it)) }
                PdfLink(e.year, e.month, extra = true)
            } else {
                Text("Toca para apuntar lo que te han pagado.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Desglose plegado: de dónde sale el bruto y qué se descuenta. */
@Composable
private fun BreakdownCard(m: MonthSummary) {
    var open by remember { mutableStateOf(false) }
    SectionCard("Desglose del mes", icon = Icons.Default.Insights) {
        if (!open) {
            TextButton(onClick = { open = true }) { Text("Ver de dónde sale") }
            return@SectionCard
        }
        KeyValue("Jornal", money(m.baseGross))
        if (m.seniority > 0) KeyValue("Antigüedad", money(m.seniority))
        if (m.bonus > 0) KeyValue("Prima", money(m.bonus))
        if (m.activablePlus > 0) KeyValue("Sábados / activables (${m.activableDays})", money(m.activablePlus))
        if (m.overtimePay > 0) KeyValue("Horas extra (${hours(m.overtimeHours)})", money(m.overtimePay))
        if (m.nightPlus > 0) KeyValue("Nocturnidad", money(m.nightPlus))
        if (m.nightShiftPlus > 0) KeyValue("Turnos de noche (${m.nightShifts})", money(m.nightShiftPlus))
        if (m.holidayPlus > 0) KeyValue("Festivos", money(m.holidayPlus))
        if (m.bonusDiscount > 0) KeyValue("Sin prima: médico / libre disp. (${hours(m.unpaidBonusHours)})", "−" + money(m.bonusDiscount))
        if (m.assemblyDiscount > 0) KeyValue("Asambleas (${hours(m.assemblyHours)})", "−" + money(m.assemblyDiscount))
        if (m.strikeDeduction > 0) KeyValue("Sin cobrar: huelga, paros… (${hours(m.strikeHours)})", "−" + money(m.strikeDeduction))
        if (m.parentalDeduction > 0) KeyValue("Permiso nacimiento (${m.parentalDays} días)", "−" + money(m.parentalDeduction))
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        KeyValue("Bruto", money(m.expectedGross))
        KeyValue("Seguridad Social", "−" + money(m.ssDeduction))
        KeyValue("IRPF (${num(m.irpfPercent)} %)", "−" + money(m.irpfDeduction))
        if (m.geroaDeduction > 0) KeyValue("EPSV Geroa", "−" + money(m.geroaDeduction))
        KeyValue("Neto", money(m.expectedNet))
        TextButton(onClick = { open = false }) { Text("Ocultar") }
    }
}

/** Fila corta de un mes en la vista del año. */
@Composable
private fun CompactMonthRow(m: MonthSummary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(monthName(m.month), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(money(m.expectedNet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(10.dp))
        val d = m.diffNet
        when {
            d == null -> Text("—", Modifier.size(width = 86.dp, height = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            abs(d) < 1 -> Text("✓", Modifier.size(width = 86.dp, height = 20.dp), color = Color(0xFF2E7D32),
                textAlign = androidx.compose.ui.text.style.TextAlign.End, fontWeight = FontWeight.Bold)
            else -> Text(money(d), Modifier.size(width = 86.dp, height = 20.dp), color = diffColor(d),
                textAlign = androidx.compose.ui.text.style.TextAlign.End, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PayslipImportDialog(
    vm: AppViewModel,
    imp: PayslipImport,
    days: Map<LocalDate, DayEntry>,
    calendar: Map<LocalDate, DayType>,
    medical: List<MedicalVisit>,
    stoppages: List<Stoppage>,
    c: Conditions,
    onDismiss: () -> Unit,
    onSave: (updateConditions: Boolean) -> Unit,
) {
    var update by remember(imp.items.size) { mutableStateOf(true) }
    val valid = imp.items.count { it.data != null && it.include }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (imp.items.size > 1) "${imp.items.size} nóminas leídas" else "Nómina leída") },
        text = {
            when {
                imp.loading -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Leyendo…", Modifier.padding(top = 8.dp))
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(imp.items) { i, item ->
                        ImportedPayslipCard(item, days, calendar, medical, stoppages, c) { vm.updateImportedPayslip(i, it) }
                    }
                    if (valid > 0) item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(update, { update = it })
                            Text("Actualizar mis condiciones con la nómina más reciente (escalón, jornal, prima, IRPF, SS, Geroa…)",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (valid > 0) TextButton(onClick = { onSave(update) }) { Text(if (valid > 1) "Guardar $valid" else "Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (valid > 0) "Cancelar" else "Cerrar") } },
    )
}

@Composable
private fun ImportedPayslipCard(
    item: ImportedPayslip,
    days: Map<LocalDate, DayEntry>,
    calendar: Map<LocalDate, DayType>,
    medical: List<MedicalVisit>,
    stoppages: List<Stoppage>,
    c: Conditions,
    onChange: (ImportedPayslip) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val p = item.data
            if (p == null) {
                Text(item.error ?: "No se pudo leer", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(item.include, { onChange(item.copy(include = it)) })
                MonthSelector(item.year, item.month) { y, m -> onChange(item.copy(year = y, month = m)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(item.asExtra, { onChange(item.copy(asExtra = it)) })
                Text("Es la paga extra", style = MaterialTheme.typography.bodySmall)
            }
            // Se compara con lo esperado usando el IRPF de la propia nómina.
            val ms = Payroll.month(YearMonth.of(item.year, item.month), days.values, medical, c, null, calendar,
                stoppages = stoppages, irpfOverride = p.irpfPercent)
            val expGross = if (item.asExtra) ms.extra?.gross ?: c.baseMonthly else ms.expectedGross
            val expNet = if (item.asExtra) ms.extra?.net ?: (c.baseMonthly * (1 - (p.irpfPercent ?: c.irpfPercent) / 100)) else ms.expectedNet
            p.escalon?.let { KeyValue("Escalón", "$it") }
            p.jornal?.let { KeyValue("Jornal", money(it)) }
            p.prima?.let { KeyValue("Prima", money(it)) }
            p.antiguedad?.let { KeyValue("Antigüedad", money(it)) }
            val actH = p.activableHours
            val dtoH = p.bonusDiscountHours
            if (actH != null) KeyValue("Plus sábados y FR", "${num(actH)} h × ${money(p.activablePrice)}")
            if (dtoH != null && dtoH > 0) KeyValue("Descuento de prima", "${num(dtoH)} h × ${money(p.bonusHourPrice)}")
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            KeyValue("Total devengos", money(p.totalDevengos))
            KeyValue("Esperado", money(expGross))
            p.totalDevengos?.let { g -> KeyValue("Diferencia bruto", money(g - expGross), diffColor(g - expGross)) }
            KeyValue("Líquido", money(p.liquido))
            KeyValue("Neto esperado", money(expNet))
            p.liquido?.let { n -> KeyValue("Diferencia neto", money(n - expNet), diffColor(n - expNet)) }
            val pct = listOfNotNull(
                p.irpfPercent?.let { "IRPF ${num(it)} %" },
                p.ssPercent?.let { "SS ${num(it)} %" },
                p.geroaPercent?.let { "Geroa ${num(it)} %" },
            ).joinToString(" · ")
            if (pct.isNotEmpty()) Text(pct, style = MaterialTheme.typography.bodySmall)
            if (!item.asExtra && dtoH != null && abs(dtoH - ms.unpaidBonusHours - ms.assemblyHours) > 0.01) Text(
                "La nómina descuenta ${num(dtoH)} h de prima y en la app hay ${num(ms.unpaidBonusHours + ms.assemblyHours)} h " +
                    "(médico, libre disposición y asambleas${if (c.discountsNextMonth) " del mes anterior" else ""}). " +
                    "Si la empresa los descuenta un mes después, actívalo en Condiciones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/** Botón para abrir el PDF guardado de una nómina, si lo hay. */
@Composable
private fun PdfLink(year: Int, month: Int, extra: Boolean) {
    val ctx = LocalContext.current
    val file = remember(year, month, extra) {
        java.io.File(ctx.filesDir, "nominas/%04d-%02d%s.pdf".format(year, month, if (extra) "-extra" else ""))
    }
    if (!file.exists()) return
    TextButton(onClick = {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { ctx.startActivity(Intent.createChooser(intent, "Abrir nómina")) }
    }) { Text("Ver PDF de la nómina") }
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
