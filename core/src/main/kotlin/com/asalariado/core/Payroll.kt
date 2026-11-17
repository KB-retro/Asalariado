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
    val baseGross: Double,
    val extraPay: Double,
    val nightPlus: Double,
    val holidayPlus: Double,
    val expectedGross: Double,
    val ssDeduction: Double,
    val irpfDeduction: Double,
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
    val medicalHoursUsed: Double,
    val medicalHoursAllowed: Double,
    val vacationUsed: Int,
    val vacationAllowed: Int,
    val personalUsed: Int,
    val personalAllowed: Int,
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
    ): MonthSummary {
        val inMonth = days.filter { YearMonth.from(it.date) == ym }
        val med = medical.filter { YearMonth.from(it.date) == ym && it.justified }
        val work = inMonth.filter { it.shift.isWork }

        val workedHours = work.sumOf { it.workedHours(c) }
        val nightHours = work.sumOf { it.nightHours(c) }
        val holidayHours = work.filter { it.holidayWorked }.sumOf { it.workedHours(c) }
        val medicalHours = med.sumOf { it.hours }

        val prorate = c.payments <= 12 || c.proratedExtras
        val payments = max(c.payments, 1)
        val monthlyBase = if (prorate) c.grossAnnualSalary / 12.0 else c.grossAnnualSalary / payments
        val extraCount = if (prorate) 0 else (payments - 12).coerceAtLeast(0)
        val extraMonths = c.extraPayMonths.take(extraCount)
        val extraPay = if (ym.monthValue in extraMonths) monthlyBase * extraMonths.count { it == ym.monthValue } else 0.0

        val nightPlus = nightHours * c.nightBonusPerHour
        val holidayPlus = holidayHours * c.holidayBonusPerHour
        val gross = monthlyBase + extraPay + nightPlus + holidayPlus

        // La base de cotización incluye las pagas extra prorrateadas, no la paga extra del mes.
        val ssBase = c.grossAnnualSalary / 12.0 + nightPlus + holidayPlus
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
            personalDays = inMonth.count { it.shift == ShiftType.PERSONAL },
            sickDays = inMonth.count { it.shift == ShiftType.SICK },
            shiftCounts = inMonth.groupingBy { it.shift }.eachCount(),
            baseGross = monthlyBase,
            extraPay = extraPay,
            nightPlus = nightPlus,
            holidayPlus = holidayPlus,
            expectedGross = gross,
            ssDeduction = ss,
            irpfDeduction = irpf,
            expectedNet = gross - ss - irpf,
            payslip = payslip,
        )
    }

    fun year(
        year: Int,
        days: Collection<DayEntry>,
        medical: Collection<MedicalVisit>,
        payslips: Collection<Payslip>,
        c: Conditions,
    ): YearSummary {
        val yDays = days.filter { it.date.year == year }
        val yMed = medical.filter { it.date.year == year }
        val months = (1..12).map { m ->
            month(YearMonth.of(year, m), yDays, yMed, c, payslips.firstOrNull { it.year == year && it.month == m })
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
            medicalHoursUsed = yMed.sumOf { it.hours },
            medicalHoursAllowed = c.medicalHoursPerYear,
            vacationUsed = months.sumOf { it.vacationDays },
            vacationAllowed = c.vacationDays,
            personalUsed = months.sumOf { it.personalDays },
            personalAllowed = c.personalDays,
            overtimeHours = overtime,
            overtimeValue = overtime * otPrice,
            expectedGross = months.sumOf { it.expectedGross },
            receivedGross = withSlip.sumOf { it.payslip?.gross ?: 0.0 },
            expectedNet = months.sumOf { it.expectedNet },
            receivedNet = withSlip.sumOf { it.payslip?.net ?: 0.0 },
            monthsWithPayslip = withSlip.size,
        )
    }

    /** Horas que, proporcionalmente, deberían llevarse hechas hasta [today]. */
    fun expectedHoursToDate(year: Int, today: LocalDate, c: Conditions): Double {
        if (today.year < year) return 0.0
        if (today.year > year) return c.annualHours
        val len = today.lengthOfYear().toDouble()
        return c.annualHours * today.dayOfYear / len
    }
}
