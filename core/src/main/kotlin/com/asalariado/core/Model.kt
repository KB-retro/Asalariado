package com.asalariado.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

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
    /** Día de huelga: no se cobra nada. */
    STRIKE("H", "Huelga", false),
    /** Permiso por nacimiento (paternidad/maternidad): paga la Seguridad Social, no la empresa. */
    PARENTAL("PN", "Permiso nacimiento", false),
    SICK("B", "Baja", false);

    companion object {
        fun fromName(name: String?): ShiftType = entries.firstOrNull { it.name == name } ?: OFF
    }
}

/**
 * Tipo de día según el calendario laboral anual (el de la empresa o el convenio),
 * independiente del turno que hagas ese día.
 */
enum class DayType(val label: String, val code: String, val workDay: Boolean, val activable: Boolean) {
    LABORABLE("Laborable", "", true, false),
    JORNADA_CORTA("Jornada de 6 h", "6h", true, false),
    JORNADA_4H("Jornada de 4 h", "4h", true, false),
    ACTIVABLE_LABORABLE("Activable laborable", "AL", true, false),
    FESTIVO("Domingo o festivo", "F", false, false),
    SABADO_FR("Sábado / festivo recuperable", "S", false, true),
    ACTIVABLE_FR("Activable F.R.", "FR", false, true),
    PUENTE("Puente", "P", false, false),
    VACACIONES("Vacaciones", "V", false, false);

    companion object {
        fun fromName(name: String?): DayType? = entries.firstOrNull { it.name == name }
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

/** Horas sueltas que afectan al sueldo. */
enum class StoppageKind(val label: String) {
    /** Paro por horas (p. ej. por despidos): esas horas no se cobran. */
    STRIKE("Paro / huelga por horas"),
    /** Asamblea: se cobra el salario base pero no la prima. */
    ASSEMBLY("Asamblea"),
    /** Horas extra / compensadas: se pagan según el día (laborable, sábado o domingo/festivo). */
    OVERTIME("Horas extra / compensadas"),
}

data class Stoppage(val date: LocalDate, val kind: StoppageKind, val hours: Double)

/** Tipo de permiso médico. */
enum class MedicalKind(val label: String, val limited: Boolean) {
    /** Médico de cabecera / general o acompañar a un familiar: tiene bolsa anual de horas. */
    COMMON("Médico general / acompañante", true),
    /** Especialista para uno mismo: sin límite de horas. */
    SPECIALIST("Especialista (propio)", false);

    companion object {
        fun fromName(name: String?): MedicalKind = entries.firstOrNull { it.name == name } ?: COMMON
    }
}

data class MedicalVisit(
    val id: Long = 0,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val reason: String = "",
    /** Con justificante: cuenta como tiempo de trabajo retribuido. */
    val justified: Boolean = true,
    val kind: MedicalKind = MedicalKind.COMMON,
) {
    val hours: Double get() = hoursBetween(start, end)
}

data class Payslip(
    val year: Int,
    val month: Int,
    val gross: Double?,
    val net: Double?,
    val note: String = "",
    /** Porcentaje de IRPF que aplicó esa nómina (la empresa lo regulariza durante el año). */
    val irpfPercent: Double? = null,
)

data class ShiftHours(val start: LocalTime, val end: LocalTime)

/** Condiciones laborales anuales (contrato / convenio). */
data class Conditions(
    /** Escalón salarial elegido de la tabla (null = importes puestos a mano). */
    val escalon: Int? = null,
    /** Salario base mensual (jornal). Las pagas extra son de este importe. */
    val baseMonthly: Double = 1285.71,
    /** Prima mensual (se cobra en 12 pagas, no en las extra). */
    val bonusMonthly: Double = 0.0,
    /** Prima por hora: se descuenta por cada hora de médico, libre disposición o asamblea. */
    val bonusPerHour: Double = 0.0,
    /** Salario por hora para descontar huelgas y paros (0 = jornal × pagas / horas anuales). */
    val salaryPerHour: Double = 0.0,
    /** Antigüedad: fecha desde la que cuenta y lo que se cobra al mes por cada quinquenio (5 años). */
    val seniorityDate: LocalDate? = null,
    val quinquenioMonthly: Double = 0.0,
    /** Los descuentos (médico, asambleas, huelga…) llegan en la nómina del mes siguiente. */
    val discountsNextMonth: Boolean = false,
    /** Precio de la hora compensada / extra según el día. */
    val overtimeWeekdayPrice: Double = 0.0,
    val overtimeSaturdayPrice: Double = 0.0,
    val overtimeSundayPrice: Double = 0.0,
    /** Plus por cada turno de noche de más de 4 horas. */
    val nightShiftBonus: Double = 0.0,
    /** Aportación del trabajador a la EPSV (Geroa); la empresa aporta lo mismo. */
    val geroaPercent: Double = 0.0,
    /** Otros importes que paga la empresa y cotizan (p. ej. seguro de vida), al mes. */
    val employerMonthlyExtras: Double = 0.0,
    val annualHours: Double = 1780.0,
    val payments: Int = 14,
    /** Meses de las pagas extra y día aproximado de cobro de cada una (se cobran aparte de la nómina). */
    val extraPayMonths: List<Int> = listOf(7, 12),
    val extraPayDays: List<Int> = listOf(20, 15),
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
    /** Contingencias comunes + desempleo y FP + MEI del trabajador. */
    val ssPercent: Double = 6.50,
    val vacationDays: Int = 22,
    val personalDays: Int = 2,
    /** Bolsa anual de médico general / acompañante (el especialista propio no tiene límite). */
    val medicalHoursPerYear: Double = 16.0,
    /** Horas de una jornada normal y de una jornada corta (días amarillos del calendario). */
    val normalDayHours: Double = 8.0,
    val shortDayHours: Double = 6.0,
    /** Lo que se paga por hora trabajada en un sábado o festivo activable (precio de sábado). */
    val activablePricePerHour: Double = 0.0,
    /** Días activables que la empresa puede obligarte a trabajar al año. */
    val maxActivablesPerYear: Int = 5,
    /** Ciclo de rotación semanal, p. ej. [Mañana, Tarde]. */
    val rotation: List<ShiftType> = listOf(ShiftType.MORNING, ShiftType.AFTERNOON),
    /** Lunes de una semana en la que se trabaja rotation[0]. */
    val rotationAnchor: LocalDate? = null,
) {
    /** Bruto anual: jornal en todas las pagas + prima en 12. */
    val grossAnnualSalary: Double get() = baseMonthly * maxOf(payments, 12) + bonusMonthly * 12

    /** Quinquenios cumplidos al final de [ym]. */
    fun quinquenios(ym: YearMonth): Int {
        val from = seniorityDate ?: return 0
        val years = java.time.Period.between(from, ym.atEndOfMonth()).years
        return if (years < 0) 0 else years / 5
    }

    /** Antigüedad que se cobra en [ym]. */
    fun seniorityMonthly(ym: YearMonth): Double = quinquenios(ym) * quinquenioMonthly

    /** Salario hora (sin prima) que se descuenta por cada hora de huelga. */
    val effectiveSalaryPerHour: Double get() =
        if (salaryPerHour > 0) salaryPerHour else if (annualHours > 0) baseMonthly * maxOf(payments, 12) / annualHours else 0.0

    val hourlyRate: Double get() = if (annualHours > 0) grossAnnualSalary / annualHours else 0.0

    fun hoursFor(shift: ShiftType): ShiftHours? = shiftHours[shift]

    /** Día aproximado en que se cobra la paga extra de [month] (null si ese mes no hay). */
    fun extraPayDay(month: Int): Int? {
        val i = extraPayMonths.take(maxOf(payments, 12) - 12).indexOf(month)
        return if (i < 0 || proratedExtras) null else extraPayDays.getOrNull(i) ?: 20
    }

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
