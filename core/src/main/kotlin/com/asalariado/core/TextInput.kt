package com.asalariado.core

import java.time.LocalTime

/** Lectura de lo que se escribe en los campos de la app. */
object TextInput {
    /** Acepta 6:30, 06:30, 6.30, 6,30, 6h30, 0630 o 630 (el teclado numérico no siempre tiene «:»). */
    fun parseTime(s: String): LocalTime? {
        val t = s.trim().lowercase()
        if (t.isEmpty()) return null
        val (h, mi) = Regex("""^(\d{1,2})[:.,h ](\d{1,2})?$""").find(t)?.let {
            it.groupValues[1].toInt() to (it.groupValues[2].toIntOrNull() ?: 0)
        } ?: Regex("""^\d{1,4}$""").find(t)?.let {
            when (t.length) {
                1, 2 -> t.toInt() to 0
                3 -> t.take(1).toInt() to t.drop(1).toInt()
                else -> t.take(2).toInt() to t.drop(2).toInt()
            }
        } ?: return null
        return if (h in 0..24 && mi in 0..59) LocalTime.of(h % 24, mi) else null
    }

    /** Número con coma o punto decimal, y con puntos de miles (2.094,02). */
    fun parseNum(s: String): Double? {
        val t = s.trim().replace(" ", "").replace("€", "")
        if (t.isEmpty()) return null
        val normalized = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
        return normalized.toDoubleOrNull()
    }
}
