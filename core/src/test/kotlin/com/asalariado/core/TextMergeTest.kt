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
