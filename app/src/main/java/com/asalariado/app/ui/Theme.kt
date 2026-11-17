package com.asalariado.app.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.sin

/** Paletas de color de la app. */
enum class Palette(val label: String, val primary: Color, val secondary: Color, val tertiary: Color) {
    BOSQUE("Bosque", Color(0xFF1E6B5A), Color(0xFFB7791F), Color(0xFF3F6E9E)),
    OCEANO("Océano", Color(0xFF1565C0), Color(0xFF00897B), Color(0xFF7E57C2)),
    LAVANDA("Lavanda", Color(0xFF6750A4), Color(0xFFB4558F), Color(0xFF2E7D8C)),
    CORAL("Coral", Color(0xFFD9480F), Color(0xFF1C7C9C), Color(0xFF8E5BB5)),
    CEREZA("Cereza", Color(0xFFB0124A), Color(0xFF5E60CE), Color(0xFFE09F3E)),
    GRAFITO("Grafito", Color(0xFF455A64), Color(0xFFEF8F00), Color(0xFF26A69A)),
    DINAMICO("Del fondo de pantalla", Color(0xFF6750A4), Color(0xFF625B71), Color(0xFF7D5260)),
}

enum class ThemeMode(val label: String) { SISTEMA("Automático"), CLARO("Claro"), OSCURO("Oscuro") }

/** Fondos de pantalla de la app. */
enum class Backdrop(val label: String) {
    LISO("Liso"), DEGRADADO("Degradado"), AURORA("Aurora"), ONDAS("Ondas"), PUNTOS("Puntos"),
}

data class UiPrefs(
    val palette: Palette = Palette.BOSQUE,
    val mode: ThemeMode = ThemeMode.SISTEMA,
    val backdrop: Backdrop = Backdrop.AURORA,
)

/** Preferencias de aspecto, guardadas en el móvil. */
class UiPrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<UiPrefs> = _state

    private fun load() = UiPrefs(
        palette = runCatching { Palette.valueOf(prefs.getString("palette", null)!!) }.getOrDefault(Palette.BOSQUE),
        mode = runCatching { ThemeMode.valueOf(prefs.getString("mode", null)!!) }.getOrDefault(ThemeMode.SISTEMA),
        backdrop = runCatching { Backdrop.valueOf(prefs.getString("backdrop", null)!!) }.getOrDefault(Backdrop.AURORA),
    )

    fun save(p: UiPrefs) {
        prefs.edit().putString("palette", p.palette.name).putString("mode", p.mode.name)
            .putString("backdrop", p.backdrop.name).apply()
        _state.value = p
    }
}

fun schemeFor(p: Palette, dark: Boolean): ColorScheme {
    val white = Color.White
    val black = Color(0xFF101418)
    return if (!dark) lightColorScheme(
        primary = p.primary,
        onPrimary = white,
        primaryContainer = lerp(p.primary, white, 0.82f),
        onPrimaryContainer = lerp(p.primary, black, 0.6f),
        secondary = p.secondary,
        onSecondary = white,
        secondaryContainer = lerp(p.secondary, white, 0.82f),
        onSecondaryContainer = lerp(p.secondary, black, 0.6f),
        tertiary = p.tertiary,
        tertiaryContainer = lerp(p.tertiary, white, 0.82f),
        background = lerp(p.primary, white, 0.96f),
        surface = lerp(p.primary, white, 0.97f),
        surfaceVariant = lerp(p.primary, white, 0.88f),
        surfaceContainer = lerp(p.primary, white, 0.93f),
        surfaceContainerHigh = lerp(p.primary, white, 0.90f),
        surfaceContainerLow = lerp(p.primary, white, 0.95f),
        outline = lerp(p.primary, Color.Gray, 0.6f),
    ) else darkColorScheme(
        primary = lerp(p.primary, white, 0.45f),
        onPrimary = lerp(p.primary, black, 0.7f),
        primaryContainer = lerp(p.primary, black, 0.45f),
        onPrimaryContainer = lerp(p.primary, white, 0.85f),
        secondary = lerp(p.secondary, white, 0.4f),
        onSecondary = lerp(p.secondary, black, 0.7f),
        secondaryContainer = lerp(p.secondary, black, 0.5f),
        onSecondaryContainer = lerp(p.secondary, white, 0.85f),
        tertiary = lerp(p.tertiary, white, 0.4f),
        tertiaryContainer = lerp(p.tertiary, black, 0.5f),
        background = lerp(p.primary, black, 0.9f),
        surface = lerp(p.primary, black, 0.88f),
        surfaceVariant = lerp(p.primary, black, 0.75f),
        surfaceContainer = lerp(p.primary, black, 0.82f),
        surfaceContainerHigh = lerp(p.primary, black, 0.78f),
        surfaceContainerLow = lerp(p.primary, black, 0.86f),
        outline = lerp(p.primary, Color.Gray, 0.5f),
    )
}

private val appShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val appTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun AsalariadoTheme(prefs: UiPrefs = UiPrefs(), content: @Composable () -> Unit) {
    val dark = when (prefs.mode) {
        ThemeMode.SISTEMA -> isSystemInDarkTheme()
        ThemeMode.CLARO -> false
        ThemeMode.OSCURO -> true
    }
    val scheme = if (prefs.palette == Palette.DINAMICO && Build.VERSION.SDK_INT >= 31) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        schemeFor(prefs.palette, dark)
    }
    MaterialTheme(colorScheme = scheme, shapes = appShapes, typography = appTypography) {
        AppBackground(prefs.backdrop) { content() }
    }
}

/** Dibuja el fondo elegido detrás de toda la app. */
@Composable
fun AppBackground(backdrop: Backdrop, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.background)) {
        when (backdrop) {
            Backdrop.LISO -> {}
            Backdrop.DEGRADADO -> Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(cs.primaryContainer.copy(alpha = 0.9f), cs.background, cs.secondaryContainer.copy(alpha = 0.5f)),
                    ),
                ),
            )
            Backdrop.AURORA -> Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    Brush.radialGradient(listOf(cs.primary.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.05f), radius = size.width * 0.9f),
                    radius = size.width * 0.9f, center = Offset(size.width * 0.1f, size.height * 0.05f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(cs.tertiary.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.45f), radius = size.width * 0.8f),
                    radius = size.width * 0.8f, center = Offset(size.width * 0.95f, size.height * 0.45f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(cs.secondary.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.95f), radius = size.width * 0.9f),
                    radius = size.width * 0.9f, center = Offset(size.width * 0.2f, size.height * 0.95f),
                )
            }
            Backdrop.ONDAS -> Canvas(Modifier.fillMaxSize()) {
                fun wave(baseY: Float, amp: Float, phase: Float, color: Color, fromTop: Boolean) {
                    val path = Path()
                    val w = size.width
                    path.moveTo(0f, if (fromTop) 0f else size.height)
                    var x = 0f
                    while (x <= w) {
                        val y = baseY + amp * sin((x / w) * 2 * PI.toFloat() + phase)
                        path.lineTo(x, y)
                        x += 8f
                    }
                    path.lineTo(w, if (fromTop) 0f else size.height)
                    path.close()
                    drawPath(path, color)
                }
                wave(size.height * 0.16f, 28f, 0f, cs.primary.copy(alpha = 0.12f), true)
                wave(size.height * 0.11f, 22f, 1.6f, cs.tertiary.copy(alpha = 0.10f), true)
                wave(size.height * 0.88f, 26f, 0.8f, cs.secondary.copy(alpha = 0.10f), false)
                wave(size.height * 0.93f, 20f, 2.4f, cs.primary.copy(alpha = 0.10f), false)
            }
            Backdrop.PUNTOS -> Canvas(Modifier.fillMaxSize()) {
                val step = 28.dp.toPx()
                val r = 1.6.dp.toPx()
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) {
                        drawCircle(cs.primary.copy(alpha = 0.16f), r, Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
        }
        content()
    }
}
