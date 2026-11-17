package com.asalariado.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asalariado.app.data.AppDatabase
import com.asalariado.app.data.Backup
import com.asalariado.app.data.PdfText
import com.asalariado.app.data.SecureStore
import com.asalariado.app.data.toExtraEntity
import com.asalariado.app.data.StoppageEntity
import com.asalariado.core.Stoppage
import com.asalariado.core.StoppageKind
import com.asalariado.app.ui.UiPrefs
import com.asalariado.app.ui.UiPrefsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.asalariado.core.PayslipData
import com.asalariado.core.PayslipParser
import com.asalariado.core.TablaSalarial
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

/** Nómina leída de un PDF o imagen, pendiente de confirmar. */
data class PayslipImport(
    val loading: Boolean = false,
    val items: List<ImportedPayslip> = emptyList(),
)

/** Una nómina leída: sus datos, a qué mes va, si es paga extra y su PDF sin contraseña. */
data class ImportedPayslip(
    val data: PayslipData? = null,
    val error: String? = null,
    val year: Int = LocalDate.now().year,
    val month: Int = LocalDate.now().monthValue,
    val asExtra: Boolean = false,
    val include: Boolean = true,
    val pdf: ByteArray? = null,
)

data class ImportRow(val day: ParsedDay, val include: Boolean = true)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.dao()
    private val store = ConditionsStore(app)
    private val uiStore = UiPrefsStore(app)

    /** Aspecto de la app (tema, modo claro/oscuro, fondo). */
    val ui: StateFlow<UiPrefs> = uiStore.state
    fun saveUi(p: UiPrefs) = uiStore.save(p)

    /** Mensaje breve para mostrar al usuario (copias de seguridad…). */
    val message = MutableStateFlow<String?>(null)

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        message.value = try {
            val json = Backup.export(db, ConditionsStore.toJson(conditions.value))
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
            "Copia de seguridad guardada"
        } catch (e: Exception) {
            "No se pudo guardar la copia: ${e.message}"
        }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        message.value = try {
            val json = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            } ?: error("no se pudo abrir el archivo")
            Backup.import(db, json)?.let { store.save(ConditionsStore.fromJson(it)) }
            "Datos restaurados"
        } catch (e: Exception) {
            "No se pudo restaurar: ${e.message}"
        }
    }

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

    /** Horas de paro y de asamblea. */
    val stoppages: StateFlow<List<Stoppage>> = dao.allStoppages()
        .map { list -> list.mapNotNull { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Guarda las horas de paro o asamblea de un día (0 = borrarlas). */
    fun setStoppage(date: LocalDate, kind: StoppageKind, hours: Double) = viewModelScope.launch {
        if (hours <= 0) dao.deleteStoppage(date.toEpochDay(), kind.name)
        else dao.saveStoppage(StoppageEntity(date.toEpochDay(), kind.name, hours))
    }

    /** Pone el mismo tipo de día desde [from] hasta [to] (p. ej. un permiso de nacimiento o vacaciones). */
    fun saveDayRange(template: DayEntry, to: LocalDate) = viewModelScope.launch {
        val from = template.date
        if (to.isBefore(from)) return@launch
        val list = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .map { template.copy(date = it, source = Source.MANUAL).toEntity() }.toList()
        dao.upsertDays(list)
    }

    /** Recibos de las pagas extra (aparte de la nómina del mes). */
    val extraPayslips: StateFlow<List<Payslip>> = dao.allExtraPayslips()
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveExtraPayslip(p: Payslip) = viewModelScope.launch {
        if (p.gross == null && p.net == null && p.note.isBlank()) dao.deleteExtraPayslip(p.year, p.month)
        else dao.saveExtraPayslip(p.toExtraEntity())
    }

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

    // ---- Rotación semanal ----

    /**
     * «Esta semana estoy de [shift]»: fija la rotación a partir de esta semana alternando
     * (mañana, tarde, mañana…) y rellena hasta final de año (o hasta donde llegue el calendario laboral).
     * Respeta vacaciones, bajas y días cambiados a mano de otras semanas.
     */
    fun startRotationFrom(anyDay: LocalDate, shift: ShiftType) = viewModelScope.launch {
        val monday = Rotation.mondayOf(anyDay)
        val cycle = conditions.value.rotation.takeIf { shift in it && it.size >= 2 }
            ?: listOf(ShiftType.MORNING, ShiftType.AFTERNOON).takeIf { shift in it }
            ?: listOf(shift)
        val idx = cycle.indexOf(shift)
        val rotated = cycle.drop(idx) + cycle.take(idx)
        val c = conditions.value.copy(rotation = rotated, rotationAnchor = monday)
        store.save(c)
        val cal = calendar.value
        // Hasta fin de año, y al menos un año por delante (si se marca en diciembre sigue en enero).
        val to = maxOf(LocalDate.of(monday.year, 12, 31), monday.plusYears(1), cal.keys.maxOrNull() ?: monday)
        val existing = dao.daysBetween(monday.toEpochDay(), to.toEpochDay()).map { it.toModel() }.associateBy { it.date }
        val generated = Rotation.generate(monday, to, c, cal)
        val weekEnd = monday.plusDays(6)
        val toSave = generated.filter { g ->
            val e = existing[g.date] ?: return@filter true
            if (!g.date.isAfter(weekEnd)) {
                // En la semana marcada manda lo que dices, salvo vacaciones, bajas o permisos apuntados.
                e.shift.isWork || e.shift == ShiftType.OFF || e.source == Source.PATTERN
            } else {
                e.source == Source.PATTERN
            }
        }
        dao.upsertDays(toSave.map { it.toEntity() })
    }

    /** Cambia sólo esta semana (mañana ↔ tarde) por alguna circunstancia; el resto sigue la rotación. */
    fun swapWeek(anyDay: LocalDate) = viewModelScope.launch {
        val monday = Rotation.mondayOf(anyDay)
        val c = conditions.value
        val cal = calendar.value
        val existing = days.value
        val list = (0L..6L).map { monday.plusDays(it) }.mapNotNull { d ->
            val e = existing[d] ?: return@mapNotNull null
            val other = when (e.shift) {
                ShiftType.MORNING -> ShiftType.AFTERNOON
                ShiftType.AFTERNOON -> ShiftType.MORNING
                else -> return@mapNotNull null
            }
            Rotation.plannedDay(d, other, c, cal[d]).copy(note = e.note, holidayWorked = e.holidayWorked, source = Source.MANUAL)
        }
        dao.upsertDays(list.map { it.toEntity() })
    }

    // ---- Nóminas ----

    val payslipImport = MutableStateFlow(PayslipImport())

    private val secure = SecureStore(app)

    /** Contraseña de los PDF de las nóminas (la misma para todas), guardada cifrada. */
    val payslipPassword = MutableStateFlow(secure.payslipPassword)
    fun savePayslipPassword(p: String?) {
        secure.payslipPassword = p
        payslipPassword.value = p
    }

    /** ¿Es un PDF de nómina? (lo tiene que abrir la contraseña guardada o no tener). */
    suspend fun isPayslipPdf(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        if (!PdfText.isPdf(app, uri)) return@withContext false
        runCatching {
            val text = PdfText.read(app, uri, payslipPassword.value).pages.flatten().joinToString(" ") { it.text }.uppercase()
            "DEVENGOS" in text || "LÍQUIDO" in text || "LIQUIDO" in text || "NÓMINA" in text
        }.getOrElse { it is PdfText.WrongPassword } // un PDF con contraseña que no abre: casi seguro una nómina
    }

    /** Lee una o varias nóminas (PDF, con o sin contraseña, o capturas). */
    fun readPayslips(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        payslipImport.value = PayslipImport(loading = true)
        val app = getApplication<Application>()
        val items = uris.map { uri ->
            try {
                var pdf: ByteArray? = null
                val lines = withContext(Dispatchers.IO) {
                    if (PdfText.isPdf(app, uri)) {
                        val doc = PdfText.read(app, uri, payslipPassword.value)
                        pdf = doc.unlocked
                        if (doc.images.isEmpty()) doc.pages.flatten()
                        else doc.images.flatMap { Ocr.recognizeBitmap(it).lines }
                    } else {
                        Ocr.recognize(app, uri).flatMap { it.lines }
                    }
                }
                val data = PayslipParser.parse(lines)
                if (!data.isUseful) ImportedPayslip(error = "No he encontrado los importes (total devengos, líquido…).")
                else ImportedPayslip(
                    data = data,
                    year = data.year ?: LocalDate.now().year,
                    month = data.month ?: LocalDate.now().monthValue,
                    asExtra = data.isExtra,
                    pdf = pdf,
                )
            } catch (e: PdfText.WrongPassword) {
                ImportedPayslip(
                    error = if (payslipPassword.value == null) "El PDF tiene contraseña: ponla en Ajustes → Nóminas."
                    else "La contraseña guardada no abre este PDF.",
                )
            } catch (e: Exception) {
                ImportedPayslip(error = "No se pudo leer: ${e.message}")
            }
        }
        payslipImport.value = PayslipImport(items = items)
    }

    fun updateImportedPayslip(index: Int, item: ImportedPayslip) {
        val list = payslipImport.value.items.toMutableList()
        if (index in list.indices) list[index] = item
        payslipImport.value = payslipImport.value.copy(items = list)
    }

    fun clearPayslipImport() { payslipImport.value = PayslipImport() }

    /** Archivo donde se guarda la copia (sin contraseña) del PDF de una nómina. */
    fun payslipFile(year: Int, month: Int, extra: Boolean): java.io.File =
        java.io.File(getApplication<Application>().filesDir, "nominas/%04d-%02d%s.pdf".format(year, month, if (extra) "-extra" else ""))

    /**
     * Guarda todas las nóminas leídas (con su PDF) y, si se pide, actualiza las condiciones con los
     * datos de la nómina normal más reciente.
     */
    fun commitPayslips(updateConditions: Boolean) = viewModelScope.launch {
        val ok = payslipImport.value.items.filter { it.data != null && it.include }
        for (it in ok) {
            val p = it.data!!
            val slip = Payslip(it.year, it.month, p.totalDevengos, p.liquido, "Importada", p.irpfPercent)
            if (it.asExtra) dao.saveExtraPayslip(slip.toExtraEntity()) else dao.savePayslip(slip.toEntity())
            it.pdf?.let { bytes ->
                withContext(Dispatchers.IO) {
                    val f = payslipFile(it.year, it.month, it.asExtra)
                    f.parentFile?.mkdirs()
                    f.writeBytes(bytes)
                }
            }
        }
        val latest = ok.filter { !it.asExtra }.maxByOrNull { it.year * 12 + it.month }?.data
        if (updateConditions && latest != null) {
            val p = latest
            var c = conditions.value
            // Sólo se aplica la tabla si cambia el escalón, para no pisar lo que hayas ajustado a mano.
            p.escalon?.let { e ->
                if (e != c.escalon && TablaSalarial.BELLOTA_2026.escalon(e) != null) c = TablaSalarial.BELLOTA_2026.aplicar(c, e)
            }
            c = c.copy(
                baseMonthly = p.jornal ?: c.baseMonthly,
                bonusMonthly = p.prima ?: c.bonusMonthly,
                bonusPerHour = p.bonusHourPrice ?: c.bonusPerHour,
                activablePricePerHour = p.activablePrice ?: c.activablePricePerHour,
                irpfPercent = p.irpfPercent ?: c.irpfPercent,
                ssPercent = p.ssPercent ?: c.ssPercent,
                geroaPercent = p.geroaPercent ?: c.geroaPercent,
                employerMonthlyExtras = p.seguroVida ?: c.employerMonthlyExtras,
                seniorityDate = p.seniorityDate ?: c.seniorityDate,
            )
            store.save(c)
        }
        message.value = "${ok.size} nómina(s) guardada(s)"
        clearPayslipImport()
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
            lastOcr = uris.flatMap { Ocr.recognize(getApplication(), it, payslipPassword.value) }
            withContext(Dispatchers.Default) { reparseNow(detect = true) }
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
        // Leer colores y cuadrículas cuesta un poco: se hace fuera del hilo de la pantalla.
        viewModelScope.launch(Dispatchers.Default) { reparseNow(detect) }
    }

    private fun reparseNow(detect: Boolean) {
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
            val a = AnnualCalendarParser.parse(r.lines, r.pixels, st.annualYear ?: year, st.annualYear, r.words) ?: continue
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
