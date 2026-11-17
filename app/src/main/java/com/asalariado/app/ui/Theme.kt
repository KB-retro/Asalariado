package com.asalariado.app.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asalariado.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.sin

// Temas, fuentes y estilos iguales a los de Baby Registry.

/** Paletas de color (las mismas de Baby Registry). */
enum class Palette(val label: String, val primary: Color, val secondary: Color, val tertiary: Color) {
    LAVANDA("Lavanda", Color(0xFF6C4CD8), Color(0xFFD9577F), Color(0xFF2A9D8C)),
    OCEANO("Océano", Color(0xFF1F6FB8), Color(0xFF2A9DB0), Color(0xFF7A5BC0)),
    CORAL("Coral", Color(0xFFD45A4A), Color(0xFFE68A3C), Color(0xFF4E8F9E)),
    BOSQUE("Bosque", Color(0xFF3B7D4E), Color(0xFF8A9A3C), Color(0xFF3F7FA0)),
    MIEL("Miel", Color(0xFFB7791F), Color(0xFFD9577F), Color(0xFF4F7BC0)),
    GRAFITO("Grafito", Color(0xFF4A5566), Color(0xFF6B7A8F), Color(0xFF8C6F5A)),
    DINAMICO("Material You", Color(0xFF6C4CD8), Color(0xFFD9577F), Color(0xFF2A9D8C)),
}

enum class ThemeMode(val label: String) { SISTEMA("Automático"), CLARO("Claro"), OSCURO("Oscuro") }

/** Estilo de la interfaz (formas, bordes y sombras), aparte de los colores. */
enum class UiStyle(val label: String) { SUAVE("Suave"), BURBUJA("Burbuja"), MINIMALISTA("Minimalista"), GEOMETRICO("Geométrico"), PIXEL("Píxel") }

enum class AppFont(val label: String) {
    SISTEMA("Del sistema"), NUNITO("Nunito"), QUICKSAND("Quicksand"), POPPINS("Poppins"), LORA("Lora"), PIXELIFY("Pixelify (píxel)"),
}

/** Fondos de pantalla de la app. */
enum class Backdrop(val label: String) {
    LISO("Liso"), DEGRADADO("Degradado"), AURORA("Aurora"), ONDAS("Ondas"), PUNTOS("Puntos"),
}

data class UiPrefs(
    val palette: Palette = Palette.LAVANDA,
    val mode: ThemeMode = ThemeMode.SISTEMA,
    val style: UiStyle = UiStyle.SUAVE,
    val font: AppFont = AppFont.NUNITO,
    val backdrop: Backdrop = Backdrop.LISO,
)

/** Preferencias de aspecto, guardadas en el móvil. */
class UiPrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<UiPrefs> = _state

    private inline fun <reified T : Enum<T>> get(key: String, default: T): T =
        prefs.getString(key, null)?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

    // Claves «_v2»: los temas anteriores no se parecen a estos, así que se empieza de cero.
    private fun load() = UiPrefs(
        palette = get("palette_v2", Palette.LAVANDA),
        mode = get("mode", ThemeMode.SISTEMA),
        style = get("style", UiStyle.SUAVE),
        font = get("font", AppFont.NUNITO),
        backdrop = get("backdrop_v2", Backdrop.LISO),
    )

    fun save(p: UiPrefs) {
        prefs.edit().putString("palette_v2", p.palette.name).putString("mode", p.mode.name)
            .putString("style", p.style.name).putString("font", p.font.name)
            .putString("backdrop_v2", p.backdrop.name).apply()
        _state.value = p
    }
}

// ---------- Colores ----------

private fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)

/**
 * Esquema completo a partir de tres colores: los contenedores son versiones suaves (claro) o
 * profundas (oscuro) de cada color y las superficies llevan un ligero tinte del principal.
 * Todos los colores de texto están fijados para que se lean bien sobre su fondo.
 */
private fun schemeFrom(p: Palette, dark: Boolean): ColorScheme {
    val white = Color.White
    val black = Color(0xFF0E0D12)
    return if (!dark) {
        lightColorScheme(
            primary = p.primary,
            onPrimary = white,
            primaryContainer = mix(p.primary, white, 0.82f),
            onPrimaryContainer = mix(p.primary, black, 0.65f),
            secondary = p.secondary,
            onSecondary = white,
            secondaryContainer = mix(p.secondary, white, 0.82f),
            onSecondaryContainer = mix(p.secondary, black, 0.65f),
            tertiary = p.tertiary,
            onTertiary = white,
            tertiaryContainer = mix(p.tertiary, white, 0.82f),
            onTertiaryContainer = mix(p.tertiary, black, 0.65f),
            background = mix(p.primary, white, 0.97f),
            onBackground = Color(0xFF1C1A22),
            surface = mix(p.primary, white, 0.97f),
            onSurface = Color(0xFF1C1A22),
            surfaceVariant = mix(p.primary, white, 0.88f),
            onSurfaceVariant = mix(p.primary, Color(0xFF44414D), 0.8f),
            surfaceContainerLowest = white,
            surfaceContainerLow = mix(p.primary, white, 0.955f),
            surfaceContainer = mix(p.primary, white, 0.935f),
            surfaceContainerHigh = mix(p.primary, white, 0.91f),
            surfaceContainerHighest = mix(p.primary, white, 0.885f),
            outline = mix(p.primary, Color(0xFF7D7889), 0.8f),
            outlineVariant = mix(p.primary, white, 0.78f),
        )
    } else {
        darkColorScheme(
            primary = mix(p.primary, white, 0.55f),
            onPrimary = mix(p.primary, black, 0.6f),
            primaryContainer = mix(p.primary, black, 0.35f),
            onPrimaryContainer = mix(p.primary, white, 0.85f),
            secondary = mix(p.secondary, white, 0.5f),
            onSecondary = mix(p.secondary, black, 0.6f),
            secondaryContainer = mix(p.secondary, black, 0.45f),
            onSecondaryContainer = mix(p.secondary, white, 0.85f),
            tertiary = mix(p.tertiary, white, 0.5f),
            onTertiary = mix(p.tertiary, black, 0.6f),
            tertiaryContainer = mix(p.tertiary, black, 0.45f),
            onTertiaryContainer = mix(p.tertiary, white, 0.85f),
            background = mix(p.primary, black, 0.92f),
            onBackground = Color(0xFFE6E0EE),
            surface = mix(p.primary, black, 0.92f),
            onSurface = Color(0xFFE6E0EE),
            surfaceVariant = mix(p.primary, black, 0.72f),
            onSurfaceVariant = mix(p.primary, Color(0xFFCBC3DA), 0.85f),
            surfaceContainerLowest = mix(p.primary, black, 0.96f),
            surfaceContainerLow = mix(p.primary, black, 0.89f),
            surfaceContainer = mix(p.primary, black, 0.86f),
            surfaceContainerHigh = mix(p.primary, black, 0.82f),
            surfaceContainerHighest = mix(p.primary, black, 0.77f),
            outline = mix(p.primary, Color(0xFF948DA3), 0.8f),
            outlineVariant = mix(p.primary, black, 0.7f),
        )
    }
}

// Lavanda es el tema original de Baby Registry, ajustado a mano.
private val LavenderLight = lightColorScheme(
    primary = Color(0xFF6C4CD8), onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE2FF), onPrimaryContainer = Color(0xFF23005C),
    secondary = Color(0xFFD9577F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE1EA), onSecondaryContainer = Color(0xFF3E0019),
    tertiary = Color(0xFF2A9D8C), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD2F5EE), onTertiaryContainer = Color(0xFF00201B),
    background = Color(0xFFFBF8FF), onBackground = Color(0xFF1C1A22),
    surface = Color(0xFFFBF8FF), onSurface = Color(0xFF1C1A22),
    surfaceVariant = Color(0xFFE7E0F2), onSurfaceVariant = Color(0xFF5D5670),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F2FD),
    surfaceContainer = Color(0xFFF1ECFA), surfaceContainerHigh = Color(0xFFEBE6F6),
    surfaceContainerHighest = Color(0xFFE5E0F1),
    outline = Color(0xFF8E87A0), outlineVariant = Color(0xFFD6CFE4),
)

private val LavenderDark = darkColorScheme(
    primary = Color(0xFFCDBDFF), onPrimary = Color(0xFF36198F),
    primaryContainer = Color(0xFF4E33B5), onPrimaryContainer = Color(0xFFEAE2FF),
    secondary = Color(0xFFFFB1C6), onSecondary = Color(0xFF5F1131),
    secondaryContainer = Color(0xFF7D2947), onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFF7CDCC9), onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005045), onTertiaryContainer = Color(0xFFA8F2E3),
    background = Color(0xFF13111A), onBackground = Color(0xFFE6E0EE),
    surface = Color(0xFF13111A), onSurface = Color(0xFFE6E0EE),
    surfaceVariant = Color(0xFF49454F), onSurfaceVariant = Color(0xFFCBC3DA),
    surfaceContainerLowest = Color(0xFF0E0C14), surfaceContainerLow = Color(0xFF1B1823),
    surfaceContainer = Color(0xFF201D28), surfaceContainerHigh = Color(0xFF2A2733),
    surfaceContainerHighest = Color(0xFF35313E),
    outline = Color(0xFF948DA3), outlineVariant = Color(0xFF49454F),
)

/** Material You (colores del fondo de pantalla) sólo existe desde Android 12. */
val dynamicColorAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun colorSchemeFor(p: Palette, dark: Boolean): ColorScheme = when {
    p == Palette.DINAMICO && dynamicColorAvailable -> {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    }
    p == Palette.LAVANDA || p == Palette.DINAMICO -> if (dark) LavenderDark else LavenderLight
    else -> schemeFrom(p, dark)
}

// ---------- Fuentes ----------

val AppFont.family: FontFamily
    get() = when (this) {
        AppFont.SISTEMA -> FontFamily.Default
        AppFont.NUNITO -> family(R.font.nunito_regular, R.font.nunito_medium, R.font.nunito_semibold, R.font.nunito_bold)
        AppFont.QUICKSAND -> family(R.font.quicksand_regular, R.font.quicksand_medium, R.font.quicksand_semibold, R.font.quicksand_bold)
        AppFont.POPPINS -> family(R.font.poppins_regular, R.font.poppins_medium, R.font.poppins_semibold, R.font.poppins_bold)
        AppFont.LORA -> family(R.font.lora_regular, R.font.lora_medium, R.font.lora_semibold, R.font.lora_bold)
        AppFont.PIXELIFY -> family(R.font.pixelify_regular, R.font.pixelify_medium, R.font.pixelify_semibold, R.font.pixelify_bold)
    }

private fun family(regular: Int, medium: Int, semibold: Int, bold: Int) = FontFamily(
    Font(regular, FontWeight.Normal),
    Font(medium, FontWeight.Medium),
    Font(semibold, FontWeight.SemiBold),
    Font(bold, FontWeight.Bold),
)

private fun typography(font: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.with(weight: FontWeight? = null) = copy(fontFamily = font, fontWeight = weight ?: fontWeight)
    return Typography(
        displayLarge = base.displayLarge.with(),
        displayMedium = base.displayMedium.with(),
        displaySmall = base.displaySmall.with(FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.with(FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.with(FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.with(FontWeight.SemiBold),
        titleLarge = base.titleLarge.with(FontWeight.SemiBold),
        titleMedium = base.titleMedium.with(FontWeight.SemiBold),
        titleSmall = base.titleSmall.with(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.with(),
        bodyMedium = base.bodyMedium.with(),
        bodySmall = base.bodySmall.with(),
        labelLarge = base.labelLarge.with(FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = font, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
        labelSmall = base.labelSmall.with(),
    )
}

// ---------- Estilos (formas, bordes y sombras) ----------

@Immutable
data class UiStyleSpec(
    val shapes: Shapes,
    /** Forma de las tarjetas destacadas. */
    val hero: Shape,
    /** Píldoras de estado y etiquetas. */
    val pill: Shape,
    /** Fondo de los iconos. */
    val icon: Shape,
    /** Grosor del borde de las tarjetas (0 = sin borde). */
    val border: Dp = 0.dp,
    /** Borde con el color del texto (más marcado) en vez del contorno suave. */
    val strongBorder: Boolean = false,
    /** Sombra sólida desplazada, al estilo de los juegos antiguos (0 = sin ella). */
    val hardShadow: Dp = 0.dp,
    /** Tarjetas del color del fondo, separadas sólo por el borde. */
    val flatCards: Boolean = false,
    /** La sombra sólida usa el color principal en vez del color del texto. */
    val coloredShadow: Boolean = false,
)

private fun rounded(vararg dp: Int) = Shapes(
    extraSmall = RoundedCornerShape(dp[0].dp),
    small = RoundedCornerShape(dp[1].dp),
    medium = RoundedCornerShape(dp[2].dp),
    large = RoundedCornerShape(dp[3].dp),
    extraLarge = RoundedCornerShape(dp[4].dp),
)

fun uiStyleSpec(style: UiStyle): UiStyleSpec = when (style) {
    UiStyle.SUAVE -> UiStyleSpec(
        shapes = rounded(8, 12, 20, 28, 32),
        hero = RoundedCornerShape(32.dp),
        pill = RoundedCornerShape(50),
        icon = CircleShape,
    )
    UiStyle.BURBUJA -> UiStyleSpec(
        shapes = rounded(14, 20, 30, 38, 44),
        hero = RoundedCornerShape(44.dp),
        pill = RoundedCornerShape(50),
        icon = CircleShape,
    )
    UiStyle.MINIMALISTA -> UiStyleSpec(
        shapes = rounded(4, 6, 8, 10, 12),
        hero = RoundedCornerShape(12.dp),
        pill = RoundedCornerShape(6.dp),
        icon = RoundedCornerShape(10.dp),
        border = 1.dp,
        flatCards = true,
    )
    UiStyle.GEOMETRICO -> UiStyleSpec(
        shapes = Shapes(
            extraSmall = CutCornerShape(4.dp),
            small = CutCornerShape(6.dp),
            medium = CutCornerShape(12.dp),
            large = CutCornerShape(16.dp),
            extraLarge = CutCornerShape(20.dp),
        ),
        hero = CutCornerShape(24.dp),
        pill = CutCornerShape(10.dp),
        icon = CutCornerShape(12.dp),
    )
    UiStyle.PIXEL -> UiStyleSpec(
        shapes = Shapes(
            extraSmall = PixelShape(2.dp, 1),
            small = PixelShape(3.dp, 2),
            medium = PixelShape(4.dp, 2),
            large = PixelShape(4.dp, 3),
            extraLarge = PixelShape(5.dp, 3),
        ),
        hero = PixelShape(6.dp, 3),
        pill = PixelShape(3.dp, 2),
        icon = PixelShape(3.dp, 2),
        border = 3.dp,
        strongBorder = true,
        hardShadow = 5.dp,
        coloredShadow = true,
    )
}

/**
 * Rectángulo con las esquinas en escalera, como los marcos de los juegos de 8 bits: cada esquina
 * baja [steps] peldaños de [step] de lado.
 */
class PixelShape(
    private val step: Dp,
    private val steps: Int,
) : CornerBasedShape(
    topStart = CornerSize(step * steps),
    topEnd = CornerSize(step * steps),
    bottomEnd = CornerSize(step * steps),
    bottomStart = CornerSize(step * steps),
) {
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        val n = steps
        val s = minOf(bottomEnd, bottomStart) / n
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (s > 0f) {
                moveTo(0f, n * s)
                for (i in n downTo 1) { lineTo((n - i) * s, i * s); lineTo((n - i + 1) * s, i * s) }
                lineTo(n * s, 0f)
                lineTo(w - n * s, 0f)
                for (i in 1..n) { lineTo(w - (n - i + 1) * s, i * s); lineTo(w - (n - i) * s, i * s) }
            } else {
                moveTo(0f, 0f)
                lineTo(w, 0f)
            }
            lineTo(w, h - n * s)
            for (i in n downTo 1) { lineTo(w - (n - i) * s, h - i * s); lineTo(w - (n - i + 1) * s, h - i * s) }
            lineTo(w - n * s, h)
            lineTo(n * s, h)
            for (i in 1..n) { lineTo((n - i + 1) * s, h - i * s); lineTo((n - i) * s, h - i * s) }
            close()
        }
        return Outline.Generic(path)
    }

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize): CornerBasedShape =
        PixelShape(step, steps)
}

val LocalUiStyle = staticCompositionLocalOf { uiStyleSpec(UiStyle.SUAVE) }

/** Borde de las tarjetas según el estilo, o null si no lleva. */
@Composable
fun UiStyleSpec.cardBorder(): BorderStroke? = if (border == 0.dp) {
    null
} else {
    BorderStroke(border, if (strongBorder) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else MaterialTheme.colorScheme.outlineVariant)
}

/** Sombra sólida desplazada del estilo Píxel (no hace nada en los demás). */
@Composable
fun Modifier.styleShadow(shape: Shape, spec: UiStyleSpec = LocalUiStyle.current): Modifier {
    if (spec.hardShadow == 0.dp) return this
    val colors = MaterialTheme.colorScheme
    val shadow = if (spec.coloredShadow) colors.primary.copy(alpha = 0.55f) else colors.onSurface.copy(alpha = 0.8f)
    val background = colors.background
    return drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val offset = spec.hardShadow.toPx()
        translate(offset, offset) { drawOutline(outline, shadow) }
        drawOutline(outline, background)
    }
}

/** Color de texto legible sobre [bg]: oscuro sobre colores claros y blanco sobre oscuros. */
fun readableOn(bg: Color): Color = if (bg.luminance() > 0.45f) Color(0xFF1C1A22) else Color.White

// ---------- Tema ----------

@Composable
fun AsalariadoTheme(prefs: UiPrefs = UiPrefs(), content: @Composable () -> Unit) {
    val dark = when (prefs.mode) {
        ThemeMode.SISTEMA -> isSystemInDarkTheme()
        ThemeMode.CLARO -> false
        ThemeMode.OSCURO -> true
    }
    val ui = uiStyleSpec(prefs.style)
    MaterialTheme(
        colorScheme = colorSchemeFor(prefs.palette, dark),
        shapes = ui.shapes,
        typography = typography(prefs.font.family),
    ) {
        CompositionLocalProvider(LocalUiStyle provides ui) {
            AppBackground(prefs.backdrop) { content() }
        }
    }
}

/** Dibuja el fondo elegido detrás de toda la app y fija el color del texto para ese fondo. */
@Composable
fun AppBackground(backdrop: Backdrop, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.background)) {
        when (backdrop) {
            Backdrop.LISO -> {}
            Backdrop.DEGRADADO -> Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(cs.primaryContainer.copy(alpha = 0.6f), cs.background, cs.background)),
                ),
            )
            Backdrop.AURORA -> Canvas(Modifier.fillMaxSize()) {
                fun blob(x: Float, y: Float, r: Float, c: Color) = drawCircle(
                    Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(x, y), radius = r),
                    radius = r, center = Offset(x, y),
                )
                blob(size.width * 0.1f, size.height * 0.05f, size.width * 0.9f, cs.primary.copy(alpha = 0.16f))
                blob(size.width * 0.95f, size.height * 0.5f, size.width * 0.8f, cs.tertiary.copy(alpha = 0.12f))
                blob(size.width * 0.2f, size.height * 0.95f, size.width * 0.9f, cs.secondary.copy(alpha = 0.10f))
            }
            Backdrop.ONDAS -> Canvas(Modifier.fillMaxSize()) {
                fun wave(baseY: Float, amp: Float, phase: Float, color: Color, fromTop: Boolean) {
                    val path = Path()
                    val w = size.width
                    path.moveTo(0f, if (fromTop) 0f else size.height)
                    var x = 0f
                    while (x <= w) {
                        path.lineTo(x, baseY + amp * sin((x / w) * 2 * PI.toFloat() + phase))
                        x += 8f
                    }
                    path.lineTo(w, if (fromTop) 0f else size.height)
                    path.close()
                    drawPath(path, color)
                }
                wave(size.height * 0.14f, 26f, 0f, cs.primary.copy(alpha = 0.08f), true)
                wave(size.height * 0.10f, 20f, 1.6f, cs.tertiary.copy(alpha = 0.07f), true)
                wave(size.height * 0.92f, 22f, 0.8f, cs.secondary.copy(alpha = 0.07f), false)
            }
            Backdrop.PUNTOS -> Canvas(Modifier.fillMaxSize()) {
                val step = 28.dp.toPx()
                val r = 1.5.dp.toPx()
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) {
                        drawCircle(cs.onBackground.copy(alpha = 0.08f), r, Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
        }
        // Sin esto el texto saldría negro (el color por defecto) aunque el fondo sea oscuro.
        CompositionLocalProvider(LocalContentColor provides cs.onBackground) { content() }
    }
}
