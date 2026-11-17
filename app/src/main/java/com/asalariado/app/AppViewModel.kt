package com.asalariado.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asalariado.app.data.AppDatabase
import com.asalariado.app.data.ConditionsStore
import com.asalariado.app.data.Ocr
import com.asalariado.app.data.toEntity
import com.asalariado.app.data.toModel
import com.asalariado.app.data.CalendarDayEntity
import com.asalariado.core.AnnualCalendar
import com.asalariado.core.AnnualCalendarParser
import com.asalariado.core.Conditions
import com.asalariado.core.DayType
import com.asalariado.core.Payroll
import com.asalariado.core.DayEntry
import com.asalariado.core.MedicalVisit
import com.asalariado.core.ParsedDay
import com.asalariado.core.Payslip
import com.asalariado.core.Rotation
import com.asalariado.core.ScheduleParser
import com.asalariado.core.ShiftType
import com.asalariado.core.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ImportState(
    /** true = calendario laboral anual (PDF o imagen con colores); false = cuadrante de turnos. */
    val annualMode: Boolean = true,
    /** Año elegido a mano para el calendario anual (null = el que ponga la imagen). */
    val annualYear: Int? = null,
    /** El año del calendario anual se ha leído de la propia imagen/PDF. */
    val yearDetected: Boolean = false,
    val loading: Boolean = false,
    /** Calendario laboral anual detectado por colores (si la captura lo es). */
    val annual: AnnualCalendar? = null,
    val error: String? = null,
    val rows: List<ImportRow> = emptyList(),
    val rawText: String = "",
    val year: Int = LocalDate.now().year,
    val month: Int = LocalDate.now().monthValue,
)

data class ImportRow(val day: ParsedDay, val include: Boolean = true)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()
    private val store = ConditionsStore(app)

    val conditions: StateFlow<Conditions> = store.state

    val days: StateFlow<Map<LocalDate, DayEntry>> = dao.allDays()
        .map { list -> list.map { it.toModel() }.associateBy { it.date } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val medical: StateFlow<List<MedicalVisit>> = dao.allMedical()
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Calendario laboral anual: tipo de cada día (festivo, jornada corta, vacaciones…). */
    val calendar: StateFlow<Map<LocalDate, DayType>> = dao.allCalendarDays()
        .map { list -> list.mapNotNull { e -> DayType.fromName(e.type)?.let { LocalDate.ofEpochDay(e.epochDay) to it } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val payslips: StateFlow<List<Payslip>> = dao.allPayslips()
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val import = MutableStateFlow(ImportState())

    /** Mes al que debe saltar el calendario (p. ej. enero del año importado). */
    val calendarJump = MutableStateFlow<java.time.YearMonth?>(null)

    /** Imagen recibida desde "Compartir" de otra app, pendiente de procesar. */
    val sharedImage = MutableStateFlow<Uri?>(null)

    fun saveConditions(c: Conditions) = store.save(c)

    fun saveDay(d: DayEntry) = viewModelScope.launch { dao.upsertDays(listOf(d.copy(source = Source.MANUAL).toEntity())) }

    fun deleteDay(date: LocalDate) = viewModelScope.launch { dao.deleteDay(date.toEpochDay()) }

    /** Fija el turno de toda la semana (lunes–domingo) que contiene [anyDay]. */
    fun setWeek(anyDay: LocalDate, shift: ShiftType) = viewModelScope.launch {
        val c = conditions.value
        val monday = Rotation.mondayOf(anyDay)
        val existing = days.value
        val cal = calendar.value
        val list = (0L..6L).map { monday.plusDays(it) }.map { d ->
            val prev = existing[d]
            val type = cal[d]
            val isWorkDay = type?.workDay ?: (d.dayOfWeek in c.workDays)
            val keep = prev != null && !prev.shift.isWork && prev.shift != ShiftType.OFF // vacaciones, baja...
            when {
                keep -> prev!!
                isWorkDay && shift.isWork -> Rotation.plannedDay(d, shift, c, type).copy(note = prev?.note ?: "", source = Source.MANUAL)
                isWorkDay -> DayEntry(d, shift, note = prev?.note ?: "", source = Source.MANUAL)
                else -> prev ?: Rotation.plannedDay(d, shift, c, type)
            }
        }
        dao.upsertDays(list.map { it.toEntity() })
    }

    /** Guarda la rotación en las condiciones y rellena el calendario entre [from] y [to]. */
    fun applyRotation(anchorMonday: LocalDate, cycle: List<ShiftType>, from: LocalDate, to: LocalDate, overwrite: Boolean) =
        viewModelScope.launch {
            val c = conditions.value.copy(rotation = cycle, rotationAnchor = Rotation.mondayOf(anchorMonday))
            store.save(c)
            val existing = dao.daysBetween(from.toEpochDay(), to.toEpochDay()).map { it.toModel() }.associateBy { it.date }
            val toSave = Rotation.merge(existing, Rotation.generate(from, to, c, calendar.value), overwrite)
            dao.upsertDays(toSave.map { it.toEntity() })
        }

    /** Cambia a mano el tipo de un día del calendario laboral (null = quitarlo). */
    fun setCalendarType(date: LocalDate, type: DayType?) = viewModelScope.launch {
        if (type == null) dao.deleteCalendarDay(date.toEpochDay())
        else dao.upsertCalendarDays(listOf(CalendarDayEntity(date.toEpochDay(), type.name)))
    }

    /**
     * Guarda el calendario laboral anual importado. Opcionalmente usa sus horas como jornada anual
     * (restando los días de libre disposición) y rellena los turnos de ese año con la rotación.
     */
    fun commitAnnual(useHours: Boolean, fillShifts: Boolean) = viewModelScope.launch {
        val annual = import.value.annual ?: return@launch
        dao.upsertCalendarDays(annual.days.map { (d, t) -> CalendarDayEntity(d.toEpochDay(), t.name) })
        var c = conditions.value
        if (useHours) {
            val hours = annual.days.values.sumOf { Payroll.plannedHours(it, c) } - c.personalDays * c.normalDayHours
            c = c.copy(annualHours = hours)
            store.save(c)
        }
        if (fillShifts) {
            val from = annual.days.keys.min()
            val to = annual.days.keys.max()
            val existing = dao.daysBetween(from.toEpochDay(), to.toEpochDay()).map { it.toModel() }.associateBy { it.date }
            val gen = Rotation.generate(from, to, c, annual.days)
            dao.upsertDays(Rotation.merge(existing, gen, overwrite = false).map { it.toEntity() })
        }
        calendarJump.value = java.time.YearMonth.of(annual.year, annual.months.min())
        clearImport()
    }

    fun saveMedical(m: MedicalVisit) = viewModelScope.launch { dao.saveMedical(m.toEntity()) }
    fun deleteMedical(id: Long) = viewModelScope.launch { dao.deleteMedical(id) }

    fun savePayslip(p: Payslip) = viewModelScope.launch {
        if (p.gross == null && p.net == null && p.note.isBlank()) dao.deletePayslip(p.year, p.month)
        else dao.savePayslip(p.toEntity())
    }

    // ---- Importación de capturas ----

    private var lastOcr: List<Ocr.Result> = emptyList()

    fun runOcr(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        import.value = import.value.copy(loading = true, error = null, annual = null, rows = emptyList(), rawText = "")
        try {
            lastOcr = uris.flatMap { Ocr.recognize(getApplication(), it) }
            reparse(detect = true)
        } catch (e: Exception) {
            import.value = import.value.copy(loading = false, error = "No se pudo leer el archivo: ${e.message}")
        }
    }

    fun setImportMode(annual: Boolean) {
        if (annual == import.value.annualMode) return
        import.value = import.value.copy(annualMode = annual)
        if (lastOcr.isNotEmpty()) reparse(detect = false)
    }

    fun setAnnualYear(year: Int) {
        import.value = import.value.copy(annualYear = year)
        if (lastOcr.isNotEmpty()) reparse(detect = false)
    }

    fun setImportMonth(year: Int, month: Int) {
        import.value = import.value.copy(year = year, month = month)
        reparse(detect = false)
    }

    private fun reparse(detect: Boolean) {
        val st = import.value
        val c = conditions.value
        var year = st.year
        var month = st.month
        val all = LinkedHashMap<LocalDate, ParsedDay>()
        // ¿Es un calendario laboral anual por colores?
        val annualDays = LinkedHashMap<LocalDate, DayType>()
        var annualYear: Int? = null
        val annualMonths = sortedSetOf<Int>()
        for (r in lastOcr) {
            val a = AnnualCalendarParser.parse(r.words, r.pixels, st.annualYear ?: year, st.annualYear) ?: continue
            if (a.months.size >= (if (st.annualMode) 1 else 2)) {
                annualDays.putAll(a.days); annualYear = a.year; annualMonths.addAll(a.months)
            }
        }
        val annual = annualYear?.let { AnnualCalendar(it, annualDays, annualMonths.toList()) }
        if (st.annualMode) {
            import.value = st.copy(
                loading = false,
                annual = annual,
                yearDetected = st.annualYear == null && lastOcr.any { AnnualCalendarParser.detectYear(it.lines) != null },
                rows = emptyList(),
                rawText = lastOcr.joinToString("\n\n——\n\n") { it.fullText },
                error = if (annual == null) {
                    "No he encontrado los meses del calendario. Comprueba que se ven los nombres de los meses " +
                        "(ENERO, FEBRERO…) y los números de los días. Si es un cuadrante de turnos, usa la otra pestaña."
                } else null,
            )
            return
        }
        for (r in lastOcr) {
            val res = ScheduleParser.parse(r.lines, year, month, c)
            if (detect && res.detectedYear != null && res.detectedMonth != null) {
                year = res.detectedYear!!; month = res.detectedMonth!!
            }
            res.days.forEach { all[it.date] = it }
        }
        import.value = st.copy(
            loading = false,
            annual = annual,
            rows = if (annual != null) emptyList() else all.values.sortedBy { it.date }.map { ImportRow(it) },
            rawText = lastOcr.joinToString("\n\n——\n\n") { it.fullText },
            year = year,
            month = month,
        )
    }

    fun updateImportRow(index: Int, row: ImportRow) {
        val rows = import.value.rows.toMutableList()
        if (index in rows.indices) rows[index] = row
        import.value = import.value.copy(rows = rows)
    }

    fun commitImport() = viewModelScope.launch {
        val rows = import.value.rows.filter { it.include }
        val existing = days.value
        dao.upsertDays(rows.map {
            val prev = existing[it.day.date]
            DayEntry(
                date = it.day.date, shift = it.day.shift, start = it.day.start, end = it.day.end,
                holidayWorked = prev?.holidayWorked ?: false, note = prev?.note ?: "", source = Source.OCR,
            ).toEntity()
        })
        clearImport()
    }

    fun clearImport() {
        lastOcr = emptyList()
        import.value = ImportState(annualMode = import.value.annualMode)
    }
}
