package com.asalariado.core

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextInputTest {
    @Test
    fun times() {
        assertEquals(LocalTime.of(6, 30), TextInput.parseTime("06:30"))
        assertEquals(LocalTime.of(6, 30), TextInput.parseTime("6.30"))
        assertEquals(LocalTime.of(6, 30), TextInput.parseTime("6,30"))
        assertEquals(LocalTime.of(6, 30), TextInput.parseTime("0630"))
        assertEquals(LocalTime.of(6, 30), TextInput.parseTime("630"))
        assertEquals(LocalTime.of(14, 0), TextInput.parseTime("14"))
        assertEquals(LocalTime.of(14, 0), TextInput.parseTime("14:"))
        assertEquals(LocalTime.of(0, 0), TextInput.parseTime("24:00"))
        assertNull(TextInput.parseTime("25:00"))
        assertNull(TextInput.parseTime("6:75"))
        assertNull(TextInput.parseTime("abc"))
    }

    @Test
    fun numbers() {
        assertEquals(2094.02, TextInput.parseNum("2094,02"))
        assertEquals(2094.02, TextInput.parseNum("2.094,02"))
        assertEquals(2094.02, TextInput.parseNum("2094.02"))
        assertEquals(5.0, TextInput.parseNum("5,"))
        assertEquals(0.5, TextInput.parseNum("0,5"))
        assertNull(TextInput.parseNum(""))
        assertNull(TextInput.parseNum("abc"))
    }
}
