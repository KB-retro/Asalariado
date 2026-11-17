package com.asalariado.core

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

/** Una línea de texto reconocida por OCR con su caja (píxeles de la imagen). */
data class TextItem(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

data class ParsedDay(
    val date: LocalDate,
    val shift: ShiftType,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val raw: String = "",
)

data class ParseResult(
    val days: List<ParsedDay>,
    /** Mes y año detectados en el texto (p. ej. "Noviembre 2026"), si los hay. */
    val detectedYear: Int?,
    val detectedMonth: Int?,
)

/**
 * Convierte el texto reconocido en una captura de un cuadrante/horario en días con turno.
 *
 * Funciona tanto con listas ("Lun 3 nov  Mañana", "03/11 06:00-14:00") como con tablas,
 * emparejando cada turno con la fecha más cercana en la misma fila (a la izquierda)
 * o en la misma columna (encima).
 */
object ScheduleParser {

    private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val monthAlt = "ene(?:ro)?|feb(?:rero)?|mar(?:zo)?|abr(?:il)?|may(?:o)?|jun(?:io)?|jul(?:io)?|ago(?:sto)?|sep(?:tiembre)?|set(?:iembre)?|oct(?:ubre)?|nov(?:iembre)?|dic(?:iembre)?"

    private val timeRange = Regex(
        """(?<![\d/])(\d{1,2})(?:[:.h](\d{2})|h)\s*(?:-|–|—|a|al|hasta)\s*(\d{1,2})(?:[:.h](\d{2})|h)?(?![\d/])""",
        RegexOption.IGNORE_CASE,
    )
    private val timeRangeDeA = Regex("""\bde\s+(\d{1,2})(?::(\d{2}))?\s+a\s+(\d{1,2})(?::(\d{2}))?\b""", RegexOption.IGNORE_CASE)
    private val fullDate = Regex("""(?<![\d:])(\d{1,2})[/\-.](\d{1,2})(?:[/\-.](\d{2,4}))?(?![\d:])""")
    private val dayMonthName = Regex("""(?<!\d)(\d{1,2})\s*(?:de\s+)?($monthAlt)\b\.?""", RegexOption.IGNORE_CASE)
    private val monthYear = Regex("""\b($monthAlt)\s*(?:de\s+|/|-)?\s*(20\d{2})\b""", RegexOption.IGNORE_CASE)
    private val weekdayDay = Regex("""\b(?:lun(?:es)?|mar(?:tes)?|mi[eé](?:rcoles)?|jue(?:ves)?|vie(?:rnes)?|s[aá]b(?:ado)?|dom(?:ingo)?)\.?,?\s*(\d{1,2})\b(?![:.h]\d)""", RegexOption.IGNORE_CASE)
    private val bareNumber = Regex("""(?<![\d:./\-])(\d{1,2})(?![\d:./\-h])""")

    private data class KeywordRule(val regex: Regex, val shift: ShiftType)

    private val keywordRules = listOf(
        KeywordRule(Regex("""\b(?:ma[ñn]ana|ma[ñn]\.?|matinal|ma[ñn]anas)\b""", RegexOption.IGNORE_CASE), ShiftType.MORNING),
        KeywordRule(Regex("""\b(?:tarde|tardes|tard\.?)\b""", RegexOption.IGNORE_CASE), ShiftType.AFTERNOON),
        KeywordRule(Regex("""\b(?:noche|noches|nocturno|noct?\.?)\b""", RegexOption.IGNORE_CASE), ShiftType.NIGHT),
        KeywordRule(Regex("""\b(?:partido|partida|jornada partida)\b""", RegexOption.IGNORE_CASE), ShiftType.SPLIT),
        KeywordRule(Regex("""\b(?:libre|libranza|descanso|desc\.?|off|lib\.?)\b""", RegexOption.IGNORE_CASE), ShiftType.OFF),
        KeywordRule(Regex("""\b(?:vacaciones|vac\.?)\b""", RegexOption.IGNORE_CASE), ShiftType.VACATION),
        KeywordRule(Regex("""\b(?:festivo|fest\.?)\b""", RegexOption.IGNORE_CASE), ShiftType.HOLIDAY),
        KeywordRule(Regex("""\b(?:baja|it)\b""", RegexOption.IGNORE_CASE), ShiftType.SICK),
        KeywordRule(Regex("""\b(?:asuntos propios|a\.?p\.?)\b""", RegexOption.IGNORE_CASE), ShiftType.PERSONAL),
    )

    /** Códigos de una sola letra en mayúscula (habituales en cuadrantes). */
    private val singleLetter = Regex("""(?<![\p{L}\d])([MTNLDVF])(?![\p{L}\d])""")
    private val letterShift = mapOf(
        "M" to ShiftType.MORNING, "T" to ShiftType.AFTERNOON, "N" to ShiftType.NIGHT,
        "L" to ShiftType.OFF, "D" to ShiftType.OFF, "V" to ShiftType.VACATION, "F" to ShiftType.HOLIDAY,
    )
    private val weekdayLetters = setOf("L", "M", "X", "J", "V", "S", "D")

    private data class Token(
        val cx: Double, val cy: Double, val height: Double,
        val line: Int, val order: Int,
        val date: LocalDate? = null,
        val shift: ShiftType? = null,
        val start: LocalTime? = null,
        val end: LocalTime? = null,
        val raw: String,
        val explicitDate: Boolean = false,
    )

    fun parse(
        items: List<TextItem>,
        defaultYear: Int,
        defaultMonth: Int,
        conditions: Conditions = Conditions(),
    ): ParseResult {
        val bucket = maxOf(1, lineHeight(items) / 2)
        val sorted = items.filter { it.text.isNotBlank() }
            .sortedWith(compareBy<TextItem> { (it.top + it.bottom) / 2 / bucket }.thenBy { it.left })

        // Mes/año del encabezado.
        var detYear: Int? = null
        var detMonth: Int? = null
        for (it in sorted) {
            val m = monthYear.find(it.text) ?: continue
            detMonth = monthIndex(m.groupValues[1])
            detYear = m.groupValues[2].toInt()
            break
        }
        val year = detYear ?: defaultYear
        val month = detMonth ?: defaultMonth

        val dates = ArrayList<Token>()
        val shifts = ArrayList<Token>()
        var order = 0

        sorted.forEachIndexed { lineIdx, item ->
            val text = item.text
            val masked = StringBuilder(text)
            val len = text.length.coerceAtLeast(1)
            val w = (item.right - item.left).toDouble()
            val cy = (item.top + item.bottom) / 2.0
            val h = (item.bottom - item.top).toDouble().coerceAtLeast(1.0)
            fun xAt(start: Int, end: Int) = item.left + w * ((start + end) / 2.0) / len
            fun mask(r: IntRange) { for (i in r) masked.setCharAt(i, ' ') }

            // 1) Rangos horarios.
            for (re in listOf(timeRange, timeRangeDeA)) {
                for (m in re.findAll(masked.toString())) {
                    val sh = m.groupValues[1].toInt(); val sm = m.groupValues[2].toIntOrNull() ?: 0
                    val eh = m.groupValues[3].toInt(); val em = m.groupValues[4].toIntOrNull() ?: 0
                    if (sh > 24 || eh > 24 || sm > 59 || em > 59) continue
                    val st = LocalTime.of(sh % 24, sm)
                    val en = LocalTime.of(eh % 24, em)
                    shifts += Token(xAt(m.range.first, m.range.last + 1), cy, h, lineIdx, order++,
                        shift = classify(st, conditions), start = st, end = en, raw = m.value)
                    mask(m.range)
                }
            }

            // 2) Fechas explícitas.
            for (m in dayMonthName.findAll(masked.toString())) {
                val d = safeDate(year, monthIndex(m.groupValues[2]) ?: continue, m.groupValues[1].toInt()) ?: continue
                dates += Token(xAt(m.range.first, m.range.last + 1), cy, h, lineIdx, order++, date = d, raw = m.value, explicitDate = true)
                mask(m.range)
            }
            for (m in fullDate.findAll(masked.toString())) {
                val dd = m.groupValues[1].toInt(); val mm = m.groupValues[2].toInt()
                val yy = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it } ?: year
                val d = safeDate(yy, mm, dd) ?: continue
                dates += Token(xAt(m.range.first, m.range.last + 1), cy, h, lineIdx, order++, date = d, raw = m.value, explicitDate = true)
                mask(m.range)
            }
            for (m in weekdayDay.findAll(masked.toString())) {
                val d = safeDate(year, month, m.groupValues[1].toInt()) ?: continue
                dates += Token(xAt(m.range.first, m.range.last + 1), cy, h, lineIdx, order++, date = d, raw = m.value)
                mask(m.range)
            }
            // Quitar mes/año del encabezado para que el año no se lea como día.
            monthYear.findAll(masked.toString()).forEach { mask(it.range) }

            // 3) Palabras clave de turno.
            for (rule in keywordRules) {
                for (m in rule.regex.findAll(masked.toString())) {
                    shifts += Token(xAt(m.range.first, m.range.last + 1), cy, h, lineIdx, order++, shift = rule.shift, raw = m.value)
                    mask(m.range)
                }
            }

            // 4) Letras sueltas, salvo que la línea sea una cabecera de días (L M X J V S D).
            val letters = singleLetter.findAll(masked.toString()).toList()
            val looseTokens = masked.toString().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val isWeekHeader = looseTokens.size >= 4 && looseTokens.count { it.uppercase() in weekdayLetters } >= 4 &&
                looseTokens.any { it.uppercase() == "X" || it.uppercase() == "J" }
            if (!isWeekHeader) {
                for (m in letters) {
                    val s = letterShift[m.groupValues[1]] ?: continue
                    shifts += Token(xAt(m.range.first, m.range.last + 1), cy, h, lineIdx, order++, shift = s, raw = m.value)
                    mask(m.range)
                }
            } else {
                letters.forEach { mask(it.range) }
            }

            // 5) Números sueltos (1–31) = día del mes por defecto.
            for (m in bareNumber.findAll(masked.toString())) {
                val d = safeDate(year, month, m.groupValues[1].toInt()) ?: continue
                dates += Token(xAt(m.range.first, m.range.last + 1), cy, h, lineIdx, order++, date = d, raw = m.value)
            }
        }

        val result = pair(dates, shifts, conditions)
        return ParseResult(result.sortedBy { it.date }, detYear, detMonth)
    }

    private fun pair(dates: List<Token>, shifts: List<Token>, c: Conditions): List<ParsedDay> {
        if (dates.isEmpty() || shifts.isEmpty()) return emptyList()
        val rowTol = (dates + shifts).map { it.height }.average() * 0.7
        val avgDx = averageColumnGap(dates)
        val colTol = (avgDx * 0.45).coerceAtLeast(rowTol)

        // Para cada turno, la fecha más cercana a su izquierda (misma fila) o encima (misma columna).
        data class Match(val shift: Token, val date: Token, val score: Double)
        val matches = ArrayList<Match>()
        for (s in shifts) {
            var best: Match? = null
            for (d in dates) {
                val dx = s.cx - d.cx
                val dy = s.cy - d.cy
                val score = when {
                    s.line == d.line && d.order < s.order -> abs(dx) * 0.5
                    abs(dy) <= rowTol && dx > 0 -> dx
                    abs(dx) <= colTol && dy > 0 -> dy + abs(dx) * 0.5
                    else -> Double.MAX_VALUE
                }
                if (score < (best?.score ?: Double.MAX_VALUE)) best = Match(s, d, score)
            }
            best?.let { matches += it }
        }

        val byDate = LinkedHashMap<LocalDate, ParsedDay>()
        matches.sortedBy { it.score }.forEach { m ->
            val date = m.date.date ?: return@forEach
            val prev = byDate[date]
            if (prev == null) {
                byDate[date] = ParsedDay(date, m.shift.shift ?: ShiftType.OFF, m.shift.start, m.shift.end,
                    "${m.date.raw} → ${m.shift.raw}")
            } else {
                // Combina palabra clave + horario para el mismo día.
                val keyword = if (m.shift.start == null) m.shift.shift else null
                byDate[date] = prev.copy(
                    shift = keyword ?: prev.shift,
                    start = prev.start ?: m.shift.start,
                    end = prev.end ?: m.shift.end,
                    raw = prev.raw + " · " + m.shift.raw,
                )
            }
        }
        if (byDate.isNotEmpty()) return byDate.values.toList()

        // Último recurso: emparejar en orden de lectura si hay el mismo número.
        if (dates.size == shifts.size) {
            return dates.sortedBy { it.order }.zip(shifts.sortedBy { it.order }).mapNotNull { (d, s) ->
                d.date?.let { ParsedDay(it, s.shift ?: ShiftType.OFF, s.start, s.end, "${d.raw} → ${s.raw}") }
            }
        }
        return emptyList()
    }

    private fun averageColumnGap(dates: List<Token>): Double {
        val gaps = dates.groupBy { it.line }.values.flatMap { row ->
            row.sortedBy { it.cx }.zipWithNext { a, b -> b.cx - a.cx }
        }.filter { it > 0 }
        return if (gaps.isEmpty()) 80.0 else gaps.sorted()[gaps.size / 2]
    }

    /** Clasifica un horario por la hora de entrada, usando los turnos configurados. */
    fun classify(start: LocalTime, c: Conditions): ShiftType {
        val candidates = listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT)
            .mapNotNull { t -> c.hoursFor(t)?.let { t to it.start } }
        if (candidates.isEmpty()) {
            return when (start.hour) { in 4..11 -> ShiftType.MORNING; in 12..19 -> ShiftType.AFTERNOON; else -> ShiftType.NIGHT }
        }
        fun dist(a: LocalTime, b: LocalTime): Int {
            val d = abs(a.toSecondOfDay() - b.toSecondOfDay()) / 60
            return minOf(d, 24 * 60 - d)
        }
        return candidates.minBy { dist(it.second, start) }.first
    }

    private fun monthIndex(s: String): Int? {
        val k = s.lowercase().take(3).let { if (it == "set") "sep" else it }
        val i = MONTHS.indexOf(k)
        return if (i >= 0) i + 1 else null
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        if (m in 1..12 && d in 1..31) runCatching { LocalDate.of(y, m, d) }.getOrNull() else null

    private fun lineHeight(items: List<TextItem>): Int =
        items.map { it.bottom - it.top }.filter { it > 0 }.sorted().let { if (it.isEmpty()) 20 else it[it.size / 2] }
}
