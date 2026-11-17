package com.asalariado.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TextMergeTest {
    @Test
    fun dropsDuplicatesAndCutPieces() {
        val base = listOf(TextItem("ENERO", 100, 10, 200, 30))
        val extra = listOf(
            TextItem("ENER", 100, 10, 180, 30), // trozo cortado del mismo título
            TextItem("21", 300, 100, 320, 115),
            TextItem("1", 311, 100, 320, 115), // el mismo número cortado en el borde del trozo
            TextItem("22", 330, 100, 350, 115),
        )
        val out = TextMerge.merge(base, extra).map { it.text }
        assertEquals(listOf("ENERO", "21", "22"), out)
    }

    @Test
    fun yearNextToLetters() {
        val lines = listOf(TextItem("OEeod2027 BH", 0, 0, 300, 60), TextItem("9:06", 0, 0, 40, 10))
        assertEquals(2027, AnnualCalendarParser.detectYear(lines))
    }
}


class SummaryTableTest {
    @Test
    fun readsHoursPerMonth() {
        val names = listOf("ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO", "JULIO", "AGOSTO")
        val hours = listOf(148, 152, 136, 152, 152, 168, 120, 46)
        val words = ArrayList<TextItem>()
        names.forEachIndexed { i, n ->
            val y = 1000 + i * 22
            words += TextItem(n, 75, y, 150, y + 20)
            if (i != 7) words += TextItem("${18 + i % 3}", 510, y, 534, y + 20) // laborables (el 6 de agosto no se lee)
            words += TextItem("${hours[i]}", 611, y, 641, y + 20)
        }
        // Un título de la cuadrícula no tiene números en su línea y no debe molestar.
        words += TextItem("ENERO/URTARRILA", 100, 100, 300, 120)
        val got = AnnualCalendarParser.summaryHours(words)
        assertEquals(names.indices.associate { it + 1 to hours[it] }, got)
    }
}
