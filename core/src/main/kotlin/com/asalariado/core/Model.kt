package com.asalariado.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Tipos de jornada que puede tener un día en el calendario. */
enum class ShiftType(val code: String, val label: String, val isWork: Boolean) {
    MORNING("M", "Mañana", true),
    AFTERNOON("T", "Tarde", true),
    NIGHT("N", "Noche", true),
    SPLIT("P", "Partido", true),
    OFF("L", "Libre", false),
    VACATION("V", "Vacaciones", false),
    HOLIDAY("F", "Festivo", false),
    PERSONAL("AP", "Asuntos propios", false),
    SICK("B", "Baja", false);

    companion object {
        fun fromName(name: String?): ShiftType = entries.firstOrNull { it.name == name } ?: OFF
    }
}

/** Origen del dato de un día: generado por rotación, editado a mano o importado de una captura. */
enum class Source { PATTERN, MANUAL, OCR }

data class DayEntry(
    val date: LocalDate,
    val shift: ShiftType,
    /** Hora de entrada personalizada; si es null se usa la del turno en las condiciones. */
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    /** El día era festivo y se ha trabajado (cobra plus de festivo). */
    val holidayWorked: Boolean = false,
    val note: String = "",
    val source: Source = Source.MANUAL,
)

data class MedicalVisit(
    val id: Long = 0,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val reason: String = "",
    /** Con justificante: cuenta como tiempo de trabajo retribuido. */
    val justified: Boolean = true,
) {
    val hours: Double get() = hoursBetween(start, end)
}

data class Payslip(
    val year: Int,
    val month: Int,
    val gross: Double?,
    val net: Double?,
    val note: String = "",
)

data class ShiftHours(val start: LocalTime, val end: LocalTime)

/** Condiciones laborales anuales (contrato / convenio). */
data class Conditions(
    val grossAnnualSalary: Double = 18000.0,
    val annualHours: Double = 1780.0,
    val payments: Int = 14,
    val extraPayMonths: List<Int> = listOf(6, 12),
    val proratedExtras: Boolean = false,
    val shiftHours: Map<ShiftType, ShiftHours> = defaultShiftHours,
    val workDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    ),
    val nightBonusPerHour: Double = 0.0,
    val holidayBonusPerHour: Double = 0.0,
    /** Precio de la hora extra; 0 = usar el precio hora ordinario. */
    val overtimePrice: Double = 0.0,
    val irpfPercent: Double = 12.0,
    val ssPercent: Double = 6.48,
    val vacationDays: Int = 22,
    val personalDays: Int = 2,
    val medicalHoursPerYear: Double = 20.0,
    /** Ciclo de rotación semanal, p. ej. [Mañana, Tarde]. */
    val rotation: List<ShiftType> = listOf(ShiftType.MORNING, ShiftType.AFTERNOON),
    /** Lunes de una semana en la que se trabaja rotation[0]. */
    val rotationAnchor: LocalDate? = null,
) {
    val hourlyRate: Double get() = if (annualHours > 0) grossAnnualSalary / annualHours else 0.0

    fun hoursFor(shift: ShiftType): ShiftHours? = shiftHours[shift]

    companion object {
        val defaultShiftHours: Map<ShiftType, ShiftHours> = mapOf(
            ShiftType.MORNING to ShiftHours(LocalTime.of(6, 0), LocalTime.of(14, 0)),
            ShiftType.AFTERNOON to ShiftHours(LocalTime.of(14, 0), LocalTime.of(22, 0)),
            ShiftType.NIGHT to ShiftHours(LocalTime.of(22, 0), LocalTime.of(6, 0)),
            ShiftType.SPLIT to ShiftHours(LocalTime.of(9, 0), LocalTime.of(18, 0)),
        )
    }
}

/** Horas entre dos horas del día; si end <= start se asume que cruza medianoche. */
fun hoursBetween(start: LocalTime, end: LocalTime): Double {
    var minutes = Duration.between(start, end).toMinutes()
    if (minutes <= 0) minutes += 24 * 60
    return minutes / 60.0
}

/** Horas nocturnas (22:00–06:00) dentro de un tramo. */
fun nightHoursBetween(start: LocalTime, end: LocalTime): Double {
    val s = start.toSecondOfDay() / 60
    var e = end.toSecondOfDay() / 60
    if (e <= s) e += 24 * 60
    // Ventanas nocturnas en minutos sobre un eje de 48 h.
    val windows = listOf(0 to 6 * 60, 22 * 60 to 30 * 60, 46 * 60 to 48 * 60)
    var total = 0
    for ((ws, we) in windows) {
        val o = minOf(e, we) - maxOf(s, ws)
        if (o > 0) total += o
    }
    return total / 60.0
}

fun DayEntry.effectiveHours(c: Conditions): ShiftHours? {
    if (!shift.isWork) return null
    val def = c.hoursFor(shift)
    val s = start ?: def?.start ?: return null
    val e = end ?: def?.end ?: return null
    return ShiftHours(s, e)
}

fun DayEntry.workedHours(c: Conditions): Double = effectiveHours(c)?.let { hoursBetween(it.start, it.end) } ?: 0.0

fun DayEntry.nightHours(c: Conditions): Double = effectiveHours(c)?.let { nightHoursBetween(it.start, it.end) } ?: 0.0
