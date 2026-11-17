package com.asalariado.core

import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Acceso a los píxeles de la imagen (ARGB o RGB empaquetado en un Int). */
interface PixelSource {
    val width: Int
    val height: Int
    fun rgb(x: Int, y: Int): Int
}

data class AnnualCalendar(
    val year: Int,
    val days: Map<LocalDate, DayType>,
    /** Meses que se han encontrado en la imagen. */
    val months: List<Int>,
)

/**
 * Lee un calendario laboral anual con colores (los típicos de convenio: 12 meses en cuadrícula,
 * cada día coloreado según sea festivo, sábado, vacaciones, jornada corta…).
 *
 * El OCR sólo se usa para encontrar los títulos de los meses y algunos números de día; con ellos
 * se calcula dónde está cada casilla y se lee su color, así que no importa que el OCR se salte
 * números sueltos.
 */
object AnnualCalendarParser {

    private val MONTHS = listOf(
        "ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO",
        "JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE",
    )
    private val numberRe = Regex("""^\d{1,2}$""")
    private val yearRe = Regex("""\b(20\d{2})\b""")

    private data class Header(val month: Int, val item: TextItem) {
        val cx get() = (item.left + item.right) / 2.0
        val cy get() = (item.top + item.bottom) / 2.0
    }

    private data class Num(val value: Int, val cx: Double, val cy: Double)

    /** Divide líneas con varias palabras en palabras, repartiendo la caja proporcionalmente. */
    fun splitWords(items: List<TextItem>): List<TextItem> = items.flatMap { item ->
        val text = item.text
        if (!text.trim().contains(Regex("\\s"))) return@flatMap listOf(item.copy(text = text.trim()))
        val len = text.length.coerceAtLeast(1)
        val w = (item.right - item.left).toDouble()
        Regex("""\S+""").findAll(text).map { m ->
            TextItem(
                m.value,
                (item.left + w * m.range.first / len).roundToInt(),
                item.top,
                (item.left + w * (m.range.last + 1) / len).roundToInt(),
                item.bottom,
            )
        }.toList()
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").uppercase()

    private fun monthOf(text: String): Int? {
        val t = normalize(text).trim()
        val i = MONTHS.indexOfFirst { t.startsWith(it) }
        if (i >= 0) return i + 1
        return if (t.startsWith("SETIEMBRE")) 9 else null
    }

    /**
     * @param defaultYear año si no aparece en la imagen.
     * @param forcedYear año elegido por el usuario (manda sobre el detectado).
     */
    fun parse(lines: List<TextItem>, pixels: PixelSource, defaultYear: Int, forcedYear: Int? = null): AnnualCalendar? {
        val words = splitWords(lines)
        val year = forcedYear ?: detectYear(lines) ?: defaultYear

        val headers = words.mapNotNull { w -> monthOf(w.text)?.let { Header(it, w) } }
        if (headers.isEmpty()) return null
        val nums = words.filter { numberRe.matches(it.text) }
            .map { Num(it.text.toInt(), (it.left + it.right) / 2.0, (it.top + it.bottom) / 2.0) }

        // Ancho de cada bloque de mes: distancia típica entre títulos de la misma fila.
        val headerH = headers.map { (it.item.bottom - it.item.top).toDouble() }.median()
        val gaps = headers.flatMap { a ->
            headers.filter { b -> b !== a && abs(b.cy - a.cy) < headerH && b.cx > a.cx }.map { it.cx - a.cx }
        }
        val blockW = gaps.minOrNull() ?: pixels.width.toDouble()

        data class Fit(val h: Header, val a: Double, val b: Double, val c: Double, val d: Double, val inliers: Int)
        val fits = ArrayList<Fit>()
        for (h in headers) {
            // El bloque del mes termina en la siguiente fila de títulos (o en una tabla resumen).
            val below = headers.filter { o -> o !== h && o.cy > h.cy + headerH }
                .minOfOrNull { it.item.top.toDouble() } ?: pixels.height.toDouble()
            val ym = YearMonth.of(year, h.month)
            val offset = ym.atDay(1).dayOfWeek.value - 1
            val cand = nums.filter {
                it.value in 1..ym.lengthOfMonth() &&
                    abs(it.cx - h.cx) < blockW * 0.5 &&
                    it.cy > h.item.bottom && it.cy < below
            }
            if (cand.size < 4) continue
            val cols = cand.map { ((it.value - 1 + offset) % 7).toDouble() to it.cx }
            val rows = cand.map { ((it.value - 1 + offset) / 7).toDouble() to it.cy }
            var (a, b) = theilSen(cols) ?: continue
            var (c, d) = theilSen(rows) ?: continue
            if (b <= 0 || d <= 0) continue
            fun isInlier(i: Int) = abs(a + b * cols[i].first - cols[i].second) < b * 0.35 &&
                abs(c + d * rows[i].first - rows[i].second) < d * 0.35
            // Afinar con mínimos cuadrados usando sólo los números que encajan.
            val idx = cand.indices.filter(::isInlier)
            if (idx.size >= 4) {
                leastSquares(idx.map { cols[it] })?.let { (na, nb) -> if (nb > 0) { a = na; b = nb } }
                leastSquares(idx.map { rows[it] })?.let { (nc, nd) -> if (nd > 0) { c = nc; d = nd } }
            }
            val inliers = cand.indices.count(::isInlier)
            if (inliers >= 4) fits += Fit(h, a, b, c, d, inliers)
        }
        // Si un mes sale repetido (p. ej. en una tabla resumen), se queda el que mejor encaja.
        val best = fits.groupBy { it.h.month }.mapValues { (_, l) -> l.maxBy { it.inliers } }
        if (best.isEmpty()) return null

        val days = LinkedHashMap<LocalDate, DayType>()
        for ((month, f) in best.toSortedMap()) {
            val ym = YearMonth.of(year, month)
            val offset = ym.atDay(1).dayOfWeek.value - 1
            for (day in 1..ym.lengthOfMonth()) {
                val col = (day - 1 + offset) % 7
                val row = (day - 1 + offset) / 7
                val cx = f.a + f.b * col
                val cy = f.c + f.d * row
                val color = dominantColor(pixels, cx, cy, f.b * 0.38, f.d * 0.36) ?: continue
                days[ym.atDay(day)] = classifyColor(color)
            }
        }
        return AnnualCalendar(year, days, best.keys.sorted())
    }

    /** Año del calendario: el «20xx» escrito con letra más grande (normalmente el título). */
    fun detectYear(lines: List<TextItem>): Int? =
        lines.mapNotNull { l -> yearRe.find(l.text)?.let { it.groupValues[1].toInt() to (l.bottom - l.top) } }
            .maxByOrNull { it.second }?.first

    /** Recta robusta (mediana de pendientes) y = a + b·x. */
    private fun theilSen(points: List<Pair<Double, Double>>): Pair<Double, Double>? {
        val slopes = ArrayList<Double>()
        for (i in points.indices) for (j in i + 1 until points.size) {
            val (x1, y1) = points[i]; val (x2, y2) = points[j]
            if (x1 != x2) slopes += (y2 - y1) / (x2 - x1)
        }
        if (slopes.isEmpty()) return null
        val b = slopes.median()
        val a = points.map { it.second - b * it.first }.median()
        return a to b
    }

    private fun leastSquares(points: List<Pair<Double, Double>>): Pair<Double, Double>? {
        val n = points.size.toDouble()
        val sx = points.sumOf { it.first }; val sy = points.sumOf { it.second }
        val sxx = points.sumOf { it.first * it.first }; val sxy = points.sumOf { it.first * it.second }
        val den = n * sxx - sx * sx
        if (den == 0.0) return null
        val b = (n * sxy - sx * sy) / den
        return (sy - b * sx) / n to b
    }

    private fun List<Double>.median(): Double {
        if (isEmpty()) return 0.0
        val s = sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** Color de fondo de una casilla: el más frecuente, ignorando el texto oscuro. */
    fun dominantColor(px: PixelSource, cx: Double, cy: Double, hw: Double, hh: Double): Int? {
        val x0 = max(0, (cx - hw).toInt()); val x1 = min(px.width - 1, (cx + hw).toInt())
        val y0 = max(0, (cy - hh).toInt()); val y1 = min(px.height - 1, (cy + hh).toInt())
        if (x1 <= x0 || y1 <= y0) return null
        val counts = HashMap<Int, IntArray>() // bucket -> [n, r, g, b]
        for (y in y0..y1) for (x in x0..x1) {
            val p = px.rgb(x, y)
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            if (max(r, max(g, b)) < 90) continue
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val acc = counts.getOrPut(key) { IntArray(4) }
            acc[0]++; acc[1] += r; acc[2] += g; acc[3] += b
        }
        val top = counts.values.maxByOrNull { it[0] } ?: return null
        return ((top[1] / top[0]) shl 16) or ((top[2] / top[0]) shl 8) or (top[3] / top[0])
    }

    /** Traduce el color de la casilla al tipo de día (leyenda habitual de los calendarios de convenio). */
    fun classifyColor(rgb: Int): DayType {
        val r = ((rgb shr 16) and 0xFF) / 255.0
        val g = ((rgb shr 8) and 0xFF) / 255.0
        val b = (rgb and 0xFF) / 255.0
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val v = mx
        val s = if (mx == 0.0) 0.0 else (mx - mn) / mx
        if (s < 0.25 || v < 0.25) return DayType.LABORABLE
        val d = mx - mn
        var h = when (mx) {
            r -> 60 * (((g - b) / d) % 6)
            g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }
        if (h < 0) h += 360
        return when {
            h < 20 || h >= 340 -> DayType.FESTIVO
            h < 40 -> DayType.FESTIVO
            h < 75 -> DayType.JORNADA_CORTA
            h < 165 -> if (v > 0.85 && s > 0.6) DayType.PUENTE else DayType.SABADO_FR
            h < 200 -> DayType.PUENTE
            h < 255 -> DayType.VACACIONES
            h < 290 -> DayType.ACTIVABLE_LABORABLE
            else -> if (s < 0.6) DayType.ACTIVABLE_FR else DayType.ACTIVABLE_LABORABLE
        }
    }
}
