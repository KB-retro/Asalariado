package com.asalariado.core

import java.io.File
import java.time.YearMonth
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AnnualCalendarTest {

    /** Imagen sintética: un mes en cuadrícula con algunas casillas coloreadas. */
    private class FakeImage(override val width: Int, override val height: Int) : PixelSource {
        val px = IntArray(width * height) { 0xFFFFFF }
        override fun rgb(x: Int, y: Int) = px[y * width + x]
        fun fill(cx: Int, cy: Int, hw: Int, hh: Int, color: Int) {
            for (y in cy - hh..cy + hh) for (x in cx - hw..cx + hw) px[y * width + x] = color
        }
    }

    @Test
    fun syntheticMonth() {
        // Marzo 2027 empieza en lunes. Columnas cada 40 px desde x=40, filas cada 30 px desde y=80.
        val img = FakeImage(400, 300)
        val ym = YearMonth.of(2027, 3)
        val items = mutableListOf(TextItem("MARZO / MARTXOA", 100, 20, 260, 40))
        val colors = mapOf(1 to 0xC000C0, 5 to 0xFFFF00, 6 to 0x00B050, 7 to 0xFF0000, 24 to 0xFFFF00, 29 to 0xFF0000, 30 to 0x00B050)
        for (d in 1..31) {
            val col = (d - 1) % 7; val row = (d - 1) / 7
            val cx = 40 + col * 40; val cy = 80 + row * 30
            colors[d]?.let { img.fill(cx, cy, 18, 13, it) }
            // El OCR sólo "ve" algunos números (como pasa con los de una cifra).
            if (d >= 10 && d % 3 != 0) items += TextItem("$d", cx - 8, cy - 7, cx + 8, cy + 7)
        }
        val cal = AnnualCalendarParser.parse(items, img, 2027)
        assertNotNull(cal)
        assertEquals(listOf(3), cal.months)
        val m = cal.days
        assertEquals(DayType.ACTIVABLE_LABORABLE, m[ym.atDay(1)])
        assertEquals(DayType.LABORABLE, m[ym.atDay(2)])
        assertEquals(DayType.JORNADA_CORTA, m[ym.atDay(5)])
        assertEquals(DayType.SABADO_FR, m[ym.atDay(6)])
        assertEquals(DayType.FESTIVO, m[ym.atDay(7)])
        assertEquals(DayType.JORNADA_CORTA, m[ym.atDay(24)])
        assertEquals(DayType.SABADO_FR, m[ym.atDay(30)])
        assertEquals(31, m.size)
    }

    @Test
    fun colorClassification() {
        assertEquals(DayType.FESTIVO, AnnualCalendarParser.classifyColor(0xFF0000))
        assertEquals(DayType.SABADO_FR, AnnualCalendarParser.classifyColor(0x00B050))
        assertEquals(DayType.JORNADA_CORTA, AnnualCalendarParser.classifyColor(0xFEFE00))
        assertEquals(DayType.VACACIONES, AnnualCalendarParser.classifyColor(0x0070C0))
        assertEquals(DayType.ACTIVABLE_LABORABLE, AnnualCalendarParser.classifyColor(0xC000C0))
        assertEquals(DayType.ACTIVABLE_FR, AnnualCalendarParser.classifyColor(0xFF99FF))
        assertEquals(DayType.PUENTE, AnnualCalendarParser.classifyColor(0x66FFFF))
        assertEquals(DayType.LABORABLE, AnnualCalendarParser.classifyColor(0xFEFEFE))
    }

    /**
     * Prueba con una captura real (no incluida en el repositorio):
     * -Dreal.image=captura.png -Dreal.ocr=ocr.json
     */
    @Test
    fun realImageIfProvided() {
        val imgPath = System.getProperty("real.image") ?: return
        val ocrPath = System.getProperty("real.ocr") ?: return
        val bi = ImageIO.read(File(imgPath))
        val px = object : PixelSource {
            override val width = bi.width
            override val height = bi.height
            override fun rgb(x: Int, y: Int) = bi.getRGB(x, y) and 0xFFFFFF
        }
        val re = Regex(""""text":\s*"((?:[^"\\]|\\.)*)",\s*"left":\s*(\d+),\s*"top":\s*(\d+),\s*"right":\s*(\d+),\s*"bottom":\s*(\d+)""")
        val items = re.findAll(File(ocrPath).readText()).map {
            TextItem(it.groupValues[1], it.groupValues[2].toInt(), it.groupValues[3].toInt(), it.groupValues[4].toInt(), it.groupValues[5].toInt())
        }.toList()
        val cal = AnnualCalendarParser.parse(items, px, 2027)
        assertNotNull(cal)
        println("Año ${cal.year}, meses ${cal.months}")
        var total = 0.0
        for (m in 1..12) {
            val ym = YearMonth.of(cal.year, m)
            val line = (1..ym.lengthOfMonth()).joinToString(" ") { d -> cal.days[ym.atDay(d)]?.code?.ifEmpty { "." } ?: "?" }
            val days = cal.days.filterKeys { YearMonth.from(it) == ym }.values
            val lab = days.count { it.workDay }
            val h = days.sumOf { if (it == DayType.JORNADA_CORTA) 6.0 else if (it.workDay) 8.0 else 0.0 }
            total += h
            println("%2d lab=%2d h=%5.0f  %s".format(m, lab, h, line))
        }
        println("TOTAL $total")
    }
}
