package com.asalariado.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.asalariado.core.DayType
import com.asalariado.core.ShiftType
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val ES: Locale = Locale.forLanguageTag("es-ES")

private val euro = NumberFormat.getCurrencyInstance(ES)
fun money(v: Double?): String = v?.let { euro.format(it) } ?: "—"
fun hours(v: Double): String = if (v % 1.0 == 0.0) "${v.toInt()} h" else String.format(ES, "%.1f h", v)
fun num(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.2f", v)

val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", ES)
val shortFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", ES)
fun monthName(m: Int): String = java.time.Month.of(m).getDisplayName(TextStyle.FULL_STANDALONE, ES).replaceFirstChar { it.uppercase() }
fun LocalTime.hhmm(): String = String.format(Locale.US, "%02d:%02d", hour, minute)

fun parseTime(s: String): LocalTime? {
    val t = s.trim().replace('.', ':').replace('h', ':')
    val m = Regex("""^(\d{1,2})(?::(\d{1,2}))?:?$""").find(t) ?: return null
    val h = m.groupValues[1].toInt()
    val mi = m.groupValues[2].toIntOrNull() ?: 0
    return if (h in 0..24 && mi in 0..59) LocalTime.of(h % 24, mi) else null
}

fun parseNum(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

/** Colores por turno (fondo, texto). */
@Composable
fun shiftColors(s: ShiftType): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base = when (s) {
        ShiftType.MORNING -> Color(0xFFF6C744)
        ShiftType.AFTERNOON -> Color(0xFF4F8FD9)
        ShiftType.NIGHT -> Color(0xFF4B3F8C)
        ShiftType.SPLIT -> Color(0xFF2E9C8A)
        ShiftType.OFF -> if (dark) Color(0xFF3A3A3A) else Color(0xFFE6E6E6)
        ShiftType.VACATION -> Color(0xFF55B86A)
        ShiftType.HOLIDAY -> Color(0xFFE0703A)
        ShiftType.PERSONAL -> Color(0xFFB57BD6)
        ShiftType.SICK -> Color(0xFFD9534F)
        ShiftType.STRIKE -> Color(0xFF37474F)
        ShiftType.PARENTAL -> Color(0xFF26A69A)
    }
    val fg = when (s) {
        ShiftType.MORNING -> Color(0xFF3A2D00)
        ShiftType.OFF -> if (dark) Color(0xFFDDDDDD) else Color(0xFF444444)
        else -> Color.White
    }
    return base to fg
}

/** Color de cada tipo de día del calendario laboral (como en los calendarios de convenio). */
fun dayTypeColor(t: DayType): Color = when (t) {
    DayType.LABORABLE -> Color.Transparent
    DayType.JORNADA_CORTA -> Color(0xFFFFE600)
    DayType.JORNADA_4H -> Color(0xFFF2A33A)
    DayType.ACTIVABLE_LABORABLE -> Color(0xFFB000C0)
    DayType.FESTIVO -> Color(0xFFE53935)
    DayType.SABADO_FR -> Color(0xFF00A050)
    DayType.ACTIVABLE_FR -> Color(0xFFFF8FF0)
    DayType.PUENTE -> Color(0xFF4DD8F0)
    DayType.VACACIONES -> Color(0xFF1E6FD0)
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    val ui = LocalUiStyle.current
    val shape = MaterialTheme.shapes.large
    Card(
        modifier.fillMaxWidth().styleShadow(shape, ui).animateContentSize(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (ui.flatCards) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = ui.cardBorder(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        Modifier.size(34.dp).clip(ui.icon).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Spacer(Modifier.width(10.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

/** Título grande de cada pantalla. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** Selector de año compacto (‹ 2026 ›). */
@Composable
fun YearSwitcher(year: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onChange(year - 1) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Año anterior")
        }
        Text("$year", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
        FilledTonalIconButton(onClick = { onChange(year + 1) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Año siguiente")
        }
    }
}

/** Tarjeta destacada con degradado de color; el texto se pone claro u oscuro según el fondo. */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    colors: List<Color>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ui = LocalUiStyle.current
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    val bg = colors ?: if (dark) listOf(cs.primaryContainer, cs.tertiaryContainer) else listOf(cs.primary, cs.tertiary)
    val avg = lerp(bg.first(), bg.last(), 0.5f)
    val fg = if (colors == null && dark) cs.onPrimaryContainer else readableOn(avg)
    Box(
        modifier.fillMaxWidth().styleShadow(ui.hero, ui).clip(ui.hero)
            .background(Brush.linearGradient(bg))
            .then(ui.cardBorder()?.let { Modifier.border(it, ui.hero) } ?: Modifier)
            .padding(20.dp)
            .animateContentSize(),
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
}

/** Pastilla pequeña con texto; si no se da color de texto, se elige uno legible. */
@Composable
fun Pill(text: String, color: Color, onColor: Color? = null) {
    // Sobre una pastilla translúcida se usa el color del texto de alrededor.
    val fg = onColor ?: if (color.alpha < 0.6f) LocalContentColor.current else readableOn(color)
    Box(Modifier.clip(LocalUiStyle.current.pill).background(color).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun KeyValue(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

/** Campo numérico que mantiene el texto escrito y avisa del valor al cambiar. */
@Composable
fun NumberField(label: String, value: Double, modifier: Modifier = Modifier, suffix: String = "", onChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(num(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t; parseNum(t)?.let(onChange) },
        label = { Text(label) },
        suffix = { if (suffix.isNotEmpty()) Text(suffix) },
        singleLine = true,
        isError = parseNum(text) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
fun TimeField(label: String, value: LocalTime?, modifier: Modifier = Modifier, onChange: (LocalTime?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.hhmm() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t; if (t.isBlank()) onChange(null) else parseTime(t)?.let(onChange) },
        label = { Text(label) },
        placeholder = { Text("hh:mm") },
        singleLine = true,
        isError = text.isNotBlank() && parseTime(text) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateButton(date: LocalDate, modifier: Modifier = Modifier, label: String = "", onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text((if (label.isNotEmpty()) "$label: " else "") + date.format(shortFmt))
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        ) { DatePicker(state) }
    }
}
