package com.asalariado.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asalariado.app.R

/** Pantalla que tapa la app hasta que se desbloquea con la huella o el PIN del móvil. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // El icono adaptativo no se puede pintar directamente: se usa su capa delantera sobre el azul del icono.
        Box(
            Modifier.size(104.dp).clip(MaterialTheme.shapes.extraLarge)
                .background(Brush.verticalGradient(listOf(Color(0xFF028EFE), Color(0xFF0048C0)))),
            contentAlignment = Alignment.Center,
        ) { Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.size(150.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("Asalariado está bloqueada", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Desbloquéala con tu huella o con el PIN, patrón o contraseña del móvil.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onUnlock) {
            Icon(Icons.Default.Fingerprint, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Desbloquear")
        }
    }
}
