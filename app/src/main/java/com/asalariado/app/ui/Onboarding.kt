package com.asalariado.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** Un paso de la guía de primeros pasos. */
data class Step(val title: String, val hint: String, val done: Boolean, val action: () -> Unit)

private fun prefs(c: Context) = c.getSharedPreferences("onboarding", Context.MODE_PRIVATE)

/** Guía de primeros pasos: qué hay que rellenar para que los cálculos salgan bien. */
@Composable
fun OnboardingCard(steps: List<Step>) {
    val ctx = LocalContext.current
    var hidden by remember { mutableStateOf(prefs(ctx).getBoolean("hidden", false)) }
    val done = steps.count { it.done }
    if (hidden || done == steps.size) return
    SectionCard("Primeros pasos · $done de ${steps.size}") {
        LinearProgressIndicator(progress = { done.toFloat() / steps.size }, modifier = Modifier.fillMaxWidth())
        steps.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !s.done, onClick = s.action).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (s.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                    Modifier.size(22.dp),
                    tint = if (s.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        s.title, style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (s.done) TextDecoration.LineThrough else null,
                        color = if (s.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (!s.done) Text(s.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton(onClick = { prefs(ctx).edit().putBoolean("hidden", true).apply(); hidden = true }) { Text("Ocultar la guía") }
    }
}
