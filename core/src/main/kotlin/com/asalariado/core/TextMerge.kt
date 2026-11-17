package com.asalariado.core

import kotlin.math.max
import kotlin.math.min

/**
 * Junta el texto leído de la imagen entera con el leído por trozos ampliados (los números
 * pequeños de un calendario sólo se leen bien ampliados), quitando lo repetido en los solapes.
 */
object TextMerge {
    private fun area(t: TextItem) = max(0, t.right - t.left).toLong() * max(0, t.bottom - t.top)

    private fun overlaps(a: TextItem, b: TextItem): Boolean {
        val w = min(a.right, b.right) - max(a.left, b.left)
        val h = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (w <= 0 || h <= 0) return false
        val small = min(area(a), area(b)).coerceAtLeast(1)
        return w.toLong() * h > small * 0.4
    }

    /** [base] se queda entero; de [extra] se añade lo que no pisa nada ya puesto (primero lo más grande). */
    fun merge(base: List<TextItem>, extra: List<TextItem>): List<TextItem> {
        val out = ArrayList(base)
        for (e in extra.sortedByDescending { it.right - it.left }) {
            if (e.text.isBlank()) continue
            if (out.none { overlaps(it, e) }) out += e
        }
        return out
    }
}
