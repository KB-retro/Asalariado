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
    /** Horas de cada mes según la tabla resumen del propio calendario (si la tiene), para comprobar la lectura. */
    val expectedHours: Map<Int, Int> = emptyMap(),
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
    private val numberAny = Regex("""^\d{1,3}$""")
    private val yearRe = Regex("""(?<!\d)(20[2-9]\d)(?!\d)""")

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

    /** Mes al que se refiere una palabra, tolerando un error de OCR (ENER0, EMERO, JUNI0…). */
    fun monthOf(text: String): Int? {
        val t = normalize(text).trim()
            .replace('0', 'O').replace('1', 'I')
            .takeWhile { it.isLetter() }
        if (t.length < 4) return null
        val exact = MONTHS.indexOfFirst { t.startsWith(it) }
        if (exact >= 0) return exact + 1
        if (t.startsWith("SETIEMBRE")) return 9
        // Un carácter cambiado como mucho, y sólo si no hay empate (JUNIO/JULIO).
        val close = MONTHS.indices.filter { i ->
            val name = MONTHS[i]
            name.length >= 5 && t.length >= name.length && editDistance(t.take(name.length), name) <= 1
        }
        return if (close.size == 1) close[0] + 1 else null
    }

    private fun editDistance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    /**
     * Si el OCR no ha leído el título de algún mes, deduce dónde está a partir de la cuadrícula
     * que forman los demás (filas y columnas de meses: enero en la fila 1 columna 1, etc.).
     */
    private fun inferMissingHeaders(headers: List<Header>): List<Header> {
        if (headers.size < 4) return headers
        val h = headers.map { (it.item.bottom - it.item.top).toDouble() }.median()
        // Agrupar en filas; sólo cuentan las filas con varios meses (la tabla resumen tiene uno por fila).
        val rows = ArrayList<MutableList<Header>>()
        for (hd in headers.sortedBy { it.cy }) {
            val row = rows.lastOrNull()
            if (row != null && abs(row.first().cy - hd.cy) < h) row += hd else rows += mutableListOf(hd)
        }
        val cols = rows.maxOf { it.size }
        val grid = rows.filter { it.size >= 2 }.flatten().toMutableList()
        if (cols < 2 || grid.size < 3) return headers
        val w = grid.map { (it.item.right - it.item.left).toDouble() }.median()
        fun colX() = grid.groupBy { (it.month - 1) % cols }.mapValues { (_, l) -> l.map { it.cx }.median() }
        fun rowY() = grid.groupBy { (it.month - 1) / cols }.mapValues { (_, l) -> l.map { it.cy }.median() }
        // Un mes que sale solo en su fila (porque el OCR no leyó sus vecinos) también vale si cae en
        // su columna; así no se confunde con la tabla resumen (meses en una sola columna, más abajo).
        run {
            val cx = colX(); val ry = rowY()
            val fit = if (ry.size >= 2) leastSquares(ry.map { (k, v) -> k.toDouble() to v }) else null
            for (hd in headers.sortedBy { it.cy }) {
                if (hd in grid || grid.any { it.month == hd.month }) continue
                val x = cx[(hd.month - 1) % cols] ?: continue
                val r = (hd.month - 1) / cols
                // Altura esperada de su fila: la de sus vecinos o, si no hay, la que toca por la separación entre filas.
                val y = ry[r] ?: fit?.let { (a, b) -> a + b * r } ?: continue
                if (abs(hd.cx - x) < w * 0.5 && abs(hd.cy - y) < h * 1.5) grid += hd
            }
        }
        val colX = colX()
        val rowY = rowY()
        // Filas o columnas sin ningún título leído: se sacan de la separación regular entre las demás.
        val colFit = if (colX.size >= 2) leastSquares(colX.map { (k, v) -> k.toDouble() to v }) else null
        val rowFit = if (rowY.size >= 2) leastSquares(rowY.map { (k, v) -> k.toDouble() to v }) else null
        val present = grid.map { it.month }.toSet()
        val inferred = (1..12).filter { it !in present }.mapNotNull { m ->
            val r = (m - 1) / cols
            val c = (m - 1) % cols
            val y = rowY[r] ?: rowFit?.let { (a, b) -> a + b * r } ?: return@mapNotNull null
            val x = colX[c] ?: colFit?.let { (a, b) -> a + b * c } ?: return@mapNotNull null
            Header(m, TextItem(MONTHS[m - 1], (x - w / 2).toInt(), (y - h / 2).toInt(), (x + w / 2).toInt(), (y + h / 2).toInt()))
        }
        return headers + inferred
    }

    /**
     * @param defaultYear año si no aparece en la imagen.
     * @param forcedYear año elegido por el usuario (manda sobre el detectado).
     */
    fun parse(
        lines: List<TextItem>,
        pixels: PixelSource,
        defaultYear: Int,
        forcedYear: Int? = null,
        /** Palabras con su caja exacta, si el OCR las da (mejor que repartir las líneas). */
        words: List<TextItem>? = null,
    ): AnnualCalendar? {
        val year = forcedYear ?: detectYear(lines) ?: defaultYear

        val headers = inferMissingHeaders(findHeaders(lines))
        if (headers.isEmpty()) return null
        val nums = (words ?: splitWords(lines)).let { splitWords(it) }.filter { numberRe.matches(it.text) }
            .map { Num(it.text.toInt(), (it.left + it.right) / 2.0, (it.top + it.bottom) / 2.0) }

        // Ancho de cada bloque de mes: distancia típica entre títulos de la misma fila.
        val headerH = headers.map { (it.item.bottom - it.item.top).toDouble() }.median()
        val gaps = headers.flatMap { a ->
            headers.filter { b -> b !== a && abs(b.cy - a.cy) < headerH && b.cx > a.cx }.map { it.cx - a.cx }
        }
        val blockW = gaps.minOrNull() ?: pixels.width.toDouble()

        data class Fit(val h: Header, val a: Double, val b: Double, val c: Double, val d: Double, val inliers: Int)

        fun fitMonth(h: Header, centerX: Double, below: Double): Fit? {
            val ym = YearMonth.of(year, h.month)
            val offset = ym.atDay(1).dayOfWeek.value - 1
            val cand = nums.filter {
                it.value in 1..ym.lengthOfMonth() &&
                    abs(it.cx - centerX) < blockW * 0.5 &&
                    it.cy > h.item.bottom && it.cy < below
            }
            if (cand.size < 4) return null
            val cols = cand.map { ((it.value - 1 + offset) % 7).toDouble() to it.cx }
            val rows = cand.map { ((it.value - 1 + offset) / 7).toDouble() to it.cy }
            var (a, b) = theilSen(cols) ?: return null
            var (c, d) = theilSen(rows) ?: return null
            if (b <= 0 || d <= 0) return null
            fun isInlier(i: Int) = abs(a + b * cols[i].first - cols[i].second) < b * 0.35 &&
                abs(c + d * rows[i].first - rows[i].second) < d * 0.35
            // Afinar con mínimos cuadrados usando sólo los números que encajan.
            val idx = cand.indices.filter(::isInlier)
            if (idx.size >= 4) {
                leastSquares(idx.map { cols[it] })?.let { (na, nb) -> if (nb > 0) { a = na; b = nb } }
                leastSquares(idx.map { rows[it] })?.let { (nc, nd) -> if (nd > 0) { c = nc; d = nd } }
            }
            val inliers = cand.indices.count(::isInlier)
            return if (inliers >= 4) Fit(h, a, b, c, d, inliers) else null
        }

        val fits = ArrayList<Fit>()
        for (h in headers) {
            // El bloque del mes termina en la siguiente fila de títulos (o en una tabla resumen).
            val below = headers.filter { o -> o !== h && o.cy > h.cy + headerH }
                .minOfOrNull { it.item.top.toDouble() } ?: pixels.height.toDouble()
            // Varias posiciones de partida por si el título no está centrado sobre su mes.
            val first = listOf(0.0, -0.25, 0.25).firstNotNullOfOrNull { k -> fitMonth(h, h.cx + k * blockW, below) }
                ?: continue
            // Segunda pasada centrada en la cuadrícula encontrada (columna central = 3).
            val second = fitMonth(h, first.a + first.b * 3, below)
            fits += if (second != null && second.inliers >= first.inliers) second else first
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
        return AnnualCalendar(year, days, best.keys.sorted(), summaryHours(words ?: splitWords(lines)))
    }

    /**
     * Títulos de mes con su caja completa («ENERO / URTARRILA»), para que su centro coincida
     * con el del bloque del mes aunque el OCR separe las palabras o junte varios títulos en una línea.
     */
    private fun findHeaders(lines: List<TextItem>): List<Header> {
        val out = ArrayList<Header>()
        for (line in lines) {
            val ws = splitWords(listOf(line))
            val idx = ws.indices.filter { monthOf(ws[it].text) != null }
            idx.forEachIndexed { k, i ->
                val endIdx = (if (k + 1 < idx.size) idx[k + 1] else ws.size) - 1
                val span = ws.subList(i, endIdx + 1)
                val box = TextItem(
                    span.joinToString(" ") { it.text },
                    span.minOf { it.left }, span.minOf { it.top }, span.maxOf { it.right }, span.maxOf { it.bottom },
                )
                out += Header(monthOf(ws[i].text)!!, box)
            }
        }
        return out
    }

    /**
     * Tabla resumen que traen muchos calendarios («ENERO … 19 148»): horas de cada mes. Se toma el
     * número más a la derecha de la fila del mes que parece de horas (más de 31, así no se confunde
     * con los días laborables).
     */
    fun summaryHours(words: List<TextItem>): Map<Int, Int> {
        val ws = splitWords(words)
        val out = HashMap<Int, Int>()
        for (w in ws) {
            val t = normalize(w.text)
            // Sólo nombres sueltos («ENERO»), no los títulos de la cuadrícula («ENERO / URTARRILA»).
            val m = monthOf(t) ?: continue
            val h = (w.bottom - w.top).coerceAtLeast(1)
            val cy = (w.top + w.bottom) / 2.0
            val nums = ws.filter { n ->
                n.left > w.right && numberAny.matches(n.text) && abs((n.top + n.bottom) / 2.0 - cy) < h * 0.6
            }
            if (nums.size < 1) continue
            val hours = nums.sortedBy { it.left }.map { it.text.toInt() }.lastOrNull { it in 32..260 } ?: continue
            if (m !in out) out[m] = hours
        }
        // Si salen muy pocos meses, no es una tabla resumen fiable.
        return if (out.size >= 6) out else emptyMap()
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
            h < 22 || h >= 340 -> DayType.FESTIVO
            h < 47 -> DayType.JORNADA_4H // naranja
            h < 75 -> DayType.JORNADA_CORTA
            h < 165 -> if (v > 0.85 && s > 0.6) DayType.PUENTE else DayType.SABADO_FR
            h < 200 -> DayType.PUENTE
            // Azul fuerte = vacaciones; azul claro / apagado = puente.
            h < 255 -> if (s < 0.6) DayType.PUENTE else DayType.VACACIONES
            h < 290 -> DayType.ACTIVABLE_LABORABLE
            else -> if (s < 0.6) DayType.ACTIVABLE_FR else DayType.ACTIVABLE_LABORABLE
        }
    }
}
