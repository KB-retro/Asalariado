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
import com.asalariado.core.Conditions
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
    val loading: Boolean = false,
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

    val payslips: StateFlow<List<Payslip>> = dao.allPayslips()
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val import = MutableStateFlow(ImportState())

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
        val list = (0L..6L).map { monday.plusDays(it) }.map { d ->
            val prev = existing[d]
            val isWorkDay = d.dayOfWeek in c.workDays
            val keep = prev != null && !prev.shift.isWork && prev.shift != ShiftType.OFF // vacaciones, baja...
            when {
                keep -> prev!!
                isWorkDay -> (prev ?: DayEntry(d, shift)).copy(shift = shift, start = null, end = null, source = Source.MANUAL)
                else -> prev ?: DayEntry(d, ShiftType.OFF, source = Source.PATTERN)
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
            val toSave = Rotation.merge(existing, Rotation.generate(from, to, c), overwrite)
            dao.upsertDays(toSave.map { it.toEntity() })
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
        import.value = import.value.copy(loading = true, error = null)
        try {
            lastOcr = uris.map { Ocr.recognize(getApplication(), it) }
            reparse(detect = true)
        } catch (e: Exception) {
            import.value = import.value.copy(loading = false, error = "No se pudo leer la imagen: ${e.message}")
        }
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
        for (r in lastOcr) {
            val res = ScheduleParser.parse(r.items, year, month, c)
            if (detect && res.detectedYear != null && res.detectedMonth != null) {
                year = res.detectedYear!!; month = res.detectedMonth!!
            }
            res.days.forEach { all[it.date] = it }
        }
        import.value = st.copy(
            loading = false,
            rows = all.values.sortedBy { it.date }.map { ImportRow(it) },
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
        import.value = ImportState()
    }
}
