package com.asalariado.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class PayslipParserTest {
    private fun t(text: String, top: Int, left: Int, right: Int) = TextItem(text, left, top, right, top + 18)

    /** Nómina de ejemplo con los importes del escalón 10 (texto como lo devuelve el OCR). */
    private val sample = listOf(
        t("ANTIGUEDAD", 537, 544, 631), t("01/04/2024", 575, 548, 635),
        t("DEL01/10/2026AL31/10/2026", 627, 112, 344), t("ESCALON10", 627, 796, 901),
        t("Jornal", 750, 109, 162), t("2.094,02", 750, 740, 809),
        t("Prima", 770, 110, 160), t("745,38", 770, 754, 811),
        t("Plus disponibilidad sabadosyFR", 792, 111, 353), t("8,00", 792, 516, 553), t("12,2194", 792, 623, 687), t("97,76", 792, 763, 811),
        t("Dto.prima licencia retribuida", 812, 113, 322), t("1,00", 812, 516, 554), t("-5,0620", 812, 627, 687), t("-5,06", 812, 767, 811),
        t("Dto.asamblea", 833, 111, 221), t("1,00", 833, 515, 554), t("-5,0620", 833, 628, 686), t("-5,06", 833, 768, 812),
        t("A.-Total Devengos", 853, 137, 278), t("2.927,04", 853, 947, 1016),
        t("RetencionI.R.P.F.", 875, 112, 246), t("17,00", 875, 507, 554), t("-497,60", 875, 750, 811),
        t("S.S.Contingencias Comunes", 896, 110, 330), t("4,70", 896, 515, 553), t("-157,58", 896, 749, 810),
        t("S.S.Desempleo y F.P.", 916, 111, 279), t("1,65", 916, 516, 553), t("-55,32", 916, 759, 811),
        t("E.P.S.V.- Geroa", 936, 110, 237), t("2,30", 936, 515, 553), t("-75,38", 936, 759, 810),
        t("Cotizacion MEl", 956, 110, 225), t("0,15", 956, 515, 553), t("-5,03", 956, 768, 811),
        t("B.-Total Deducciones", 978, 139, 300), t("-790,91", 978, 955, 1015),
        t("Seguro de vida", 1292, 112, 226), t("1,33", 1292, 236, 275),
        t("TOTALLiQUIDOE", 1350, 871, 985),
        t("B.TOTAL MES", 1361, 111, 205), t("PAGAEXTRA", 1361, 207, 295),
        t("3.003,75", 1405, 115, 185), t("349,00", 1404, 217, 274), t("2.136,13", 1405, 944, 1013),
    )

    @Test
    fun parsesSample() {
        val p = PayslipParser.parse(sample)
        assertEquals(2026, p.year)
        assertEquals(10, p.month)
        assertEquals(10, p.escalon)
        assertEquals(2094.02, p.jornal)
        assertEquals(745.38, p.prima)
        assertEquals(8.0, p.activableHours)
        assertEquals(12.2194, p.activablePrice)
        assertEquals(2.0, p.bonusDiscountHours)
        assertEquals(5.062, p.bonusHourPrice)
        assertEquals(2927.04, p.totalDevengos)
        assertEquals(790.91, p.totalDeducciones)
        assertEquals(2136.13, p.liquido)
        assertEquals(17.0, p.irpfPercent)
        assertEquals(6.5, p.ssPercent)
        assertEquals(2.3, p.geroaPercent)
        assertEquals(1.33, p.seguroVida)
        assertEquals(349.0, p.pagaExtra)
        assertEquals(java.time.LocalDate.of(2024, 4, 1), p.seniorityDate)
        assertEquals(null, p.antiguedad)
    }

    @Test
    fun detectsExtraPay() {
        val extra = listOf(
            t("DEL01/07/2026AL31/07/2026", 627, 112, 344),
            t("Paga extra verano", 750, 109, 260), t("2.094,02", 750, 740, 809),
            t("A.-Total Devengos", 853, 137, 278), t("2.094,02", 853, 947, 1016),
            t("RetencionI.R.P.F.", 875, 112, 246), t("17,00", 875, 507, 554), t("-355,98", 875, 750, 811),
            t("TOTALLiQUIDOE", 1350, 871, 985), t("1.738,04", 1405, 944, 1013),
        )
        val p = PayslipParser.parse(extra)
        assertEquals(true, p.isExtra)
        assertEquals(7, p.month)
        assertEquals(2094.02, p.totalDevengos)
        assertEquals(1738.04, p.liquido)
        assertEquals(false, PayslipParser.parse(sample).isExtra)
    }

    /** Con una nómina real (no incluida en el repositorio): -Dreal.payslip=ocr.json */
    @Test
    fun realIfProvided() {
        val path = System.getProperty("real.payslip") ?: return
        val re = Regex(""""text":\s*"((?:[^"\\]|\\.)*)",\s*"left":\s*(\d+),\s*"top":\s*(\d+),\s*"right":\s*(\d+),\s*"bottom":\s*(\d+)""")
        val items = re.findAll(File(path).readText()).map {
            TextItem(it.groupValues[1], it.groupValues[2].toInt(), it.groupValues[3].toInt(), it.groupValues[4].toInt(), it.groupValues[5].toInt())
        }.toList()
        println(PayslipParser.parse(items))
    }
}
