package com.asalariado.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Barras por mes: [expected] en color suave y [actual] (si lo hay) en color fuerte encima.
 * Sirve para neto esperado/cobrado y para horas hechas/del calendario.
 */
@Composable
fun MonthBars(
    expected: List<Double>,
    actual: List<Double?>,
    expectedLabel: String,
    actualLabel: String,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val soft = cs.primary.copy(alpha = 0.28f)
    val strong = cs.primary
    val grid = cs.outlineVariant
    val max = (expected + actual.filterNotNull()).maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val n = expected.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = slot * 0.62f
            for (i in 1..3) {
                val y = size.height * i / 4
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            expected.forEachIndexed { i, v ->
                val x = slot * i + (slot - barW) / 2
                val h = (v / max * size.height).toFloat()
                drawRoundRect(soft, Offset(x, size.height - h), Size(barW, h), CornerRadius(6f, 6f))
                actual.getOrNull(i)?.let { a ->
                    val ah = (a / max * size.height).toFloat()
                    val inner = barW * 0.5f
                    drawRoundRect(strong, Offset(x + (barW - inner) / 2, size.height - ah), Size(inner, ah), CornerRadius(6f, 6f))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D").take(expected.size).forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(soft, expectedLabel)
            Legend(strong, actualLabel)
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
