package com.asalariado.core

import java.text.Normalizer
import kotlin.math.abs

/** Datos leídos de una nómina (todos opcionales: depende de lo que se lea bien). */
data class PayslipData(
    val year: Int? = null,
    val month: Int? = null,
    val escalon: Int? = null,
    val jornal: Double? = null,
    val prima: Double? = null,
    /** Horas y precio de «Plus disponibilidad sábados y FR». */
    val activableHours: Double? = null,
    val activablePrice: Double? = null,
    val activableAmount: Double? = null,
    /** Horas descontadas de prima (licencia retribuida, médico, asamblea…) y precio hora de prima. */
    val bonusDiscountHours: Double? = null,
    val bonusHourPrice: Double? = null,
    val bonusDiscount: Double? = null,
    val totalDevengos: Double? = null,
    val totalDeducciones: Double? = null,
    val liquido: Double? = null,
    val irpfPercent: Double? = null,
    val ssPercent: Double? = null,
    val geroaPercent: Double? = null,
    val seguroVida: Double? = null,
    val pagaExtra: Double? = null,
) {
    val isUseful: Boolean get() = totalDevengos != null || liquido != null || jornal != null
}

/**
 * Lee una nómina (formato habitual: concepto, cantidad, precio, importe por filas).
 * Está probada con el modelo de Bellota pero busca los conceptos por nombre, no por posición fija.
 */
object PayslipParser {

    private val amountRe = Regex("""-?\d{1,3}(?:\.\d{3})*,\d{2,4}|-?\d+,\d{2,4}""")
    private val periodRe = Regex("""DEL\s*(\d{1,2})/(\d{1,2})/(\d{4})""")
    private val escalonRe = Regex("""ESCALON\s*(\d{1,2})""")

    private data class Row(val key: String, val item: TextItem, val values: List<Double>) {
        val cy get() = (item.top + item.bottom) / 2.0
    }

    private fun key(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
            .uppercase().replace(Regex("[^A-Z0-9]"), "")

    fun amount(s: String): Double? = amountRe.find(s)?.value?.replace(".", "")?.replace(',', '.')?.toDoubleOrNull()

    fun parse(lines: List<TextItem>): PayslipData {
        val items = lines.flatMap { splitTextAndNumbers(it) }
        val h = items.map { (it.bottom - it.top).toDouble() }.sorted().let { if (it.isEmpty()) 20.0 else it[it.size / 2] }
        val numbers = items.filter { amountRe.matches(it.text.trim()) }
        val labels = items.filter { !amountRe.matches(it.text.trim()) }

        fun valuesRightOf(l: TextItem): List<Double> {
            val cy = (l.top + l.bottom) / 2.0
            return numbers.filter { n -> abs((n.top + n.bottom) / 2.0 - cy) < h * 0.6 && n.left >= l.right - 2 }
                .sortedBy { it.left }.mapNotNull { amount(it.text) }
        }

        fun valueBelow(l: TextItem): Double? {
            val cx = (l.left + l.right) / 2.0
            return numbers.filter { n ->
                val ncy = (n.top + n.bottom) / 2.0
                ncy > l.bottom && ncy < l.bottom + h * 5 && n.right > l.left - h * 2 && n.left < l.right + h * 2
            }.minByOrNull { n -> ((n.top + n.bottom) / 2.0 - l.bottom) + abs((n.left + n.right) / 2.0 - cx) * 0.2 }
                ?.let { amount(it.text) }
        }

        fun row(predicate: (String) -> Boolean): Row? =
            labels.firstOrNull { predicate(key(it.text)) }?.let { Row(key(it.text), it, valuesRightOf(it)) }

        val all = labels.joinToString(" ") { key(it.text) }
        val period = periodRe.find(lines.joinToString(" ") { it.text.uppercase().replace(" ", "") })

        val jornal = row { it == "JORNAL" || it.startsWith("SALARIOBASE") }
        val prima = row { it == "PRIMA" || it.startsWith("PRIMAPRODUC") }
        val plus = row { it.startsWith("PLUSDISPONIBILIDAD") }
        val dtoRows = labels.filter { key(it.text).startsWith("DTO") }.map { valuesRightOf(it) }
        val devengos = row { it.contains("TOTALDEVENGOS") }
        val deducciones = row { it.contains("TOTALDEDUCCIONES") }
        val irpf = row { it.startsWith("RETENCIONIRPF") || it.startsWith("RETENCION") && it.contains("IRPF") }
        val ssRows = labels.filter {
            val k = key(it.text)
            k.startsWith("SSCONTINGENCIAS") || k.startsWith("SSDESEMPLEO") || k.startsWith("COTIZACIONME")
        }.map { valuesRightOf(it) }
        val geroa = row { it.startsWith("EPSV") }
        val seguro = row { it.startsWith("SEGURODEVIDA") }
        val liquidoLabel = labels.firstOrNull { key(it.text).startsWith("TOTALLIQUIDO") }
        val pagaExtraLabel = labels.firstOrNull { key(it.text).startsWith("PAGAEXTRA") }

        // Descuentos de prima: cantidad (horas) × precio negativo.
        val dtoHours = dtoRows.mapNotNull { v -> v.firstOrNull()?.takeIf { v.size >= 2 } }.sum().takeIf { dtoRows.isNotEmpty() }
        val dtoPrice = dtoRows.mapNotNull { v -> v.getOrNull(1)?.let { abs(it) }?.takeIf { v.size >= 3 } }.firstOrNull()
        val dtoAmount = dtoRows.mapNotNull { it.lastOrNull() }.sum().takeIf { dtoRows.isNotEmpty() }

        return PayslipData(
            year = period?.groupValues?.get(3)?.toInt(),
            month = period?.groupValues?.get(2)?.toInt()?.takeIf { it in 1..12 },
            escalon = escalonRe.find(all)?.groupValues?.get(1)?.toInt(),
            jornal = jornal?.values?.lastOrNull(),
            prima = prima?.values?.lastOrNull(),
            activableHours = plus?.values?.takeIf { it.size >= 3 }?.get(0),
            activablePrice = plus?.values?.takeIf { it.size >= 3 }?.get(1),
            activableAmount = plus?.values?.lastOrNull(),
            bonusDiscountHours = dtoHours,
            bonusHourPrice = dtoPrice,
            bonusDiscount = dtoAmount,
            totalDevengos = devengos?.values?.lastOrNull(),
            totalDeducciones = deducciones?.values?.lastOrNull()?.let { abs(it) },
            liquido = liquidoLabel?.let { valuesRightOf(it).lastOrNull() ?: valueBelow(it) },
            irpfPercent = irpf?.values?.takeIf { it.size >= 2 }?.first(),
            ssPercent = ssRows.mapNotNull { v -> v.firstOrNull()?.takeIf { v.size >= 2 } }.takeIf { it.isNotEmpty() }?.sum()
                ?.let { Math.round(it * 100) / 100.0 },
            geroaPercent = geroa?.values?.takeIf { it.size >= 2 }?.first(),
            seguroVida = seguro?.values?.firstOrNull(),
            pagaExtra = pagaExtraLabel?.let { valueBelow(it) },
        )
    }

    /** Separa en una línea los trozos de texto y los importes, con su caja aproximada. */
    private fun splitTextAndNumbers(line: TextItem): List<TextItem> {
        val text = line.text
        val len = text.length.coerceAtLeast(1)
        val w = (line.right - line.left).toDouble()
        fun box(from: Int, to: Int, t: String) = TextItem(
            t, (line.left + w * from / len).toInt(), line.top, (line.left + w * to / len).toInt(), line.bottom,
        )
        val out = ArrayList<TextItem>()
        var pos = 0
        for (m in amountRe.findAll(text)) {
            // Un importe pegado a letras (p. ej. «ESCALON10») no es un importe.
            val before = text.getOrNull(m.range.first - 1)
            if (before != null && before.isLetter()) continue
            val chunk = text.substring(pos, m.range.first)
            if (chunk.isNotBlank()) out += box(pos, m.range.first, chunk.trim())
            out += box(m.range.first, m.range.last + 1, m.value)
            pos = m.range.last + 1
        }
        val rest = text.substring(pos)
        if (rest.isNotBlank()) out += box(pos, text.length, rest.trim())
        return out
    }
}
