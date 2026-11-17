package com.asalariado.core

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

data class MonthSummary(
    val year: Int,
    val month: Int,
    val workedDays: Int,
    val workedHours: Double,
    val nightHours: Double,
    val holidayHours: Double,
    val medicalHours: Double,
    val vacationDays: Int,
    val personalDays: Int,
    val sickDays: Int,
    val shiftCounts: Map<ShiftType, Int>,
    /** Sábados / festivos activables trabajados y lo que se cobra por ellos. */
    val activableDays: Int,
    val activableHours: Double,
    val activablePlus: Double,
    /** Horas que marca el calendario laboral para el mes (0 si no hay calendario). */
    val calendarHours: Double,
    val baseGross: Double,
    /** Prima del mes y lo que se descuenta por horas de médico / libre disposición. */
    val bonus: Double,
    val unpaidBonusHours: Double,
    val bonusDiscount: Double,
    val extraPay: Double,
    val nightPlus: Double,
    val holidayPlus: Double,
    val expectedGross: Double,
    val ssDeduction: Double,
    val irpfDeduction: Double,
    val geroaDeduction: Double,
    /** Base de cotización a la Seguridad Social. */
    val ssBase: Double,
    val expectedNet: Double,
    val payslip: Payslip?,
) {
    /** Recibido - esperado. Negativo = te han pagado de menos. */
    val diffGross: Double? get() = payslip?.gross?.let { it - expectedGross }
    val diffNet: Double? get() = payslip?.net?.let { it - expectedNet }
    /** Horas computables (trabajadas + médico justificado). */
    val countedHours: Double get() = workedHours + medicalHours
}

data class YearSummary(
    val year: Int,
    val months: List<MonthSummary>,
    val annualHours: Double,
    val countedHours: Double,
    /** Horas de médico general / acompañante (con límite) y de especialista propio (sin límite). */
    val medicalHoursUsed: Double,
    val medicalHoursAllowed: Double,
    val specialistHours: Double,
    val vacationUsed: Int,
    val vacationAllowed: Int,
    val personalUsed: Int,
    val personalAllowed: Int,
    val activableUsed: Int,
    val maxActivables: Int,
    /** Horas de trabajo según el calendario laboral importado (0 si no hay). */
    val calendarHours: Double,
    val overtimeHours: Double,
    val overtimeValue: Double,
    val expectedGross: Double,
    val receivedGross: Double,
    val expectedNet: Double,
    val receivedNet: Double,
    val monthsWithPayslip: Int,
) {
    /** Diferencia acumulada sólo en los meses con nómina introducida. */
    val diffGross: Double get() = months.mapNotNull { it.diffGross }.sum()
    val diffNet: Double get() = months.mapNotNull { it.diffNet }.sum()
}

object Payroll {

    fun month(
        ym: YearMonth,
        days: Collection<DayEntry>,
        medical: Collection<MedicalVisit>,
        c: Conditions,
        payslip: Payslip?,
        calendar: Map<LocalDate, DayType> = emptyMap(),
    ): MonthSummary {
        val inMonth = days.filter { YearMonth.from(it.date) == ym }
        val med = medical.filter { YearMonth.from(it.date) == ym && it.justified }
        val work = inMonth.filter { it.shift.isWork }

        val workedHours = work.sumOf { it.workedHours(c) }
        val nightHours = work.sumOf { it.nightHours(c) }
        val holidayHours = work.filter { it.holidayWorked }.sumOf { it.workedHours(c) }
        val medicalHours = med.sumOf { it.hours }

        val payments = max(c.payments, 12)
        val extraCount = payments - 12
        val prorate = extraCount == 0 || c.proratedExtras
        // Paga extra prorrateada (para la base de cotización) y paga extra del mes, si toca.
        val prorata = c.baseMonthly * extraCount / 12.0
        val monthlyBase = if (prorate) c.baseMonthly + prorata else c.baseMonthly
        val extraMonths = if (prorate) emptyList() else c.extraPayMonths.take(extraCount)
        val extraPay = c.baseMonthly * extraMonths.count { it == ym.monthValue }

        // La prima no se cobra en las horas de médico (cualquier tipo) ni en los días de libre disposición.
        val allMedicalHours = medical.filter { YearMonth.from(it.date) == ym }.sumOf { it.hours }
        val personalDays = inMonth.count { it.shift == ShiftType.PERSONAL }
        val unpaidBonusHours = allMedicalHours + personalDays * c.normalDayHours
        val bonusDiscount = unpaidBonusHours * c.bonusPerHour

        val nightPlus = nightHours * c.nightBonusPerHour
        val holidayPlus = holidayHours * c.holidayBonusPerHour
        val activable = work.filter { calendar[it.date]?.activable == true }
        val activableHours = activable.sumOf { it.workedHours(c) }
        val activablePlus = activableHours * c.activablePricePerHour
        val calendarHours = calendar.filter { YearMonth.from(it.key) == ym }.values.sumOf { plannedHours(it, c) }
        val gross = monthlyBase + c.bonusMonthly - bonusDiscount + extraPay + nightPlus + holidayPlus + activablePlus

        // Base de cotización: lo devengado sin la paga extra del mes, más la prorrata de las extra,
        // lo que paga la empresa (seguro…) y su aportación a la EPSV (igual a la del trabajador).
        val g = c.geroaPercent / 100.0
        val geroaBase = gross - extraPay + (if (prorate) 0.0 else prorata) + c.employerMonthlyExtras
        val geroa = geroaBase * g
        val ssBase = geroaBase * (1 + g)
        val ss = ssBase * c.ssPercent / 100.0
        val irpf = gross * c.irpfPercent / 100.0

        return MonthSummary(
            year = ym.year,
            month = ym.monthValue,
            workedDays = work.size,
            workedHours = workedHours,
            nightHours = nightHours,
            holidayHours = holidayHours,
            medicalHours = medicalHours,
            vacationDays = inMonth.count { it.shift == ShiftType.VACATION },
            personalDays = personalDays,
            sickDays = inMonth.count { it.shift == ShiftType.SICK },
            shiftCounts = inMonth.groupingBy { it.shift }.eachCount(),
            activableDays = activable.size,
            activableHours = activableHours,
            activablePlus = activablePlus,
            calendarHours = calendarHours,
            baseGross = monthlyBase,
            bonus = c.bonusMonthly,
            unpaidBonusHours = unpaidBonusHours,
            bonusDiscount = bonusDiscount,
            extraPay = extraPay,
            nightPlus = nightPlus,
            holidayPlus = holidayPlus,
            expectedGross = gross,
            ssDeduction = ss,
            irpfDeduction = irpf,
            geroaDeduction = geroa,
            ssBase = ssBase,
            expectedNet = gross - ss - irpf - geroa,
            payslip = payslip,
        )
    }

    fun year(
        year: Int,
        days: Collection<DayEntry>,
        medical: Collection<MedicalVisit>,
        payslips: Collection<Payslip>,
        c: Conditions,
        calendar: Map<LocalDate, DayType> = emptyMap(),
    ): YearSummary {
        val yDays = days.filter { it.date.year == year }
        val yMed = medical.filter { it.date.year == year }
        val months = (1..12).map { m ->
            month(YearMonth.of(year, m), yDays, yMed, c, payslips.firstOrNull { it.year == year && it.month == m }, calendar)
        }
        val counted = months.sumOf { it.countedHours }
        val overtime = (counted - c.annualHours).coerceAtLeast(0.0)
        val otPrice = if (c.overtimePrice > 0) c.overtimePrice else c.hourlyRate
        val withSlip = months.filter { it.payslip != null }
        return YearSummary(
            year = year,
            months = months,
            annualHours = c.annualHours,
            countedHours = counted,
            medicalHoursUsed = yMed.filter { it.kind.limited }.sumOf { it.hours },
            specialistHours = yMed.filter { !it.kind.limited }.sumOf { it.hours },
            medicalHoursAllowed = c.medicalHoursPerYear,
            vacationUsed = months.sumOf { it.vacationDays },
            vacationAllowed = c.vacationDays,
            personalUsed = months.sumOf { it.personalDays },
            personalAllowed = c.personalDays,
            activableUsed = months.sumOf { it.activableDays },
            maxActivables = c.maxActivablesPerYear,
            calendarHours = months.sumOf { it.calendarHours },
            overtimeHours = overtime,
            overtimeValue = overtime * otPrice,
            expectedGross = months.sumOf { it.expectedGross },
            receivedGross = withSlip.sumOf { it.payslip?.gross ?: 0.0 },
            expectedNet = months.sumOf { it.expectedNet },
            receivedNet = withSlip.sumOf { it.payslip?.net ?: 0.0 },
            monthsWithPayslip = withSlip.size,
        )
    }

    /** Horas de trabajo que corresponden a un tipo de día del calendario laboral. */
    fun plannedHours(t: DayType, c: Conditions): Double = when {
        t == DayType.JORNADA_CORTA -> c.shortDayHours
        t == DayType.JORNADA_4H -> 4.0
        t.workDay -> c.normalDayHours
        else -> 0.0
    }

    /** Horas que, proporcionalmente, deberían llevarse hechas hasta [today]. */
    fun expectedHoursToDate(year: Int, today: LocalDate, c: Conditions): Double {
        if (today.year < year) return 0.0
        if (today.year > year) return c.annualHours
        val len = today.lengthOfYear().toDouble()
        return c.annualHours * today.dayOfYear / len
    }
}
