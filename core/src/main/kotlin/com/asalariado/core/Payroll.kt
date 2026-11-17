package com.asalariado.core

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

/** Paga extra, que se cobra aparte de la nómina del mes. */
data class ExtraPaySummary(
    val year: Int,
    val month: Int,
    /** Día aproximado de cobro. */
    val day: Int,
    val gross: Double,
    val irpf: Double,
    val net: Double,
    val payslip: Payslip?,
    /** Días del periodo de devengo que no generan paga (permiso de nacimiento, huelga). */
    val lostDays: Int = 0,
    val periodDays: Int = 0,
) {
    val diffGross: Double? get() = payslip?.gross?.let { it - gross }
    val diffNet: Double? get() = payslip?.net?.let { it - net }
}

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
    /** Huelga: días y horas sin cobrar nada; asamblea: horas sin prima. */
    val strikeDays: Int,
    val strikeHours: Double,
    val strikeDeduction: Double,
    val assemblyHours: Double,
    val assemblyDiscount: Double,
    /** Horas extra / compensadas y lo que se cobra por ellas; turnos de noche con plus. */
    val overtimeHours: Double,
    val overtimePay: Double,
    val nightShifts: Int,
    val nightShiftPlus: Double,
    /** Antigüedad (quinquenios) del mes. */
    val seniority: Double,
    /** IRPF aplicado (el de la última nómina importada si lo hay). */
    val irpfPercent: Double,
    /** Permiso por nacimiento: días del mes y lo que deja de pagar la empresa esos días. */
    val parentalDays: Int,
    val parentalDeduction: Double,
    /** Paga extra de este mes (va en un recibo aparte), o null. */
    val extra: ExtraPaySummary?,
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
    val diffGross: Double get() = months.mapNotNull { it.diffGross }.sum() + months.mapNotNull { it.extra?.diffGross }.sum()
    val diffNet: Double get() = months.mapNotNull { it.diffNet }.sum() + months.mapNotNull { it.extra?.diffNet }.sum()
}

object Payroll {

    fun month(
        ym: YearMonth,
        days: Collection<DayEntry>,
        medical: Collection<MedicalVisit>,
        c: Conditions,
        payslip: Payslip?,
        calendar: Map<LocalDate, DayType> = emptyMap(),
        extraPayslip: Payslip? = null,
        stoppages: Collection<Stoppage> = emptyList(),
        irpfOverride: Double? = null,
    ): MonthSummary {
        val irpfPct = irpfOverride ?: payslip?.irpfPercent ?: c.irpfPercent
        // Mes del que vienen los descuentos (el anterior si la empresa los aplica con un mes de retraso).
        val dm = if (c.discountsNextMonth) ym.minusMonths(1) else ym
        val seniority = c.seniorityMonthly(ym)
        val inMonth = days.filter { YearMonth.from(it.date) == ym }
        val med = medical.filter { YearMonth.from(it.date) == ym && it.justified }
        val work = inMonth.filter { it.shift.isWork }

        // Las horas de paro no cuentan como trabajadas.
        val workedHours = work.sumOf { it.workedHours(c) } -
            stoppages.filter { YearMonth.from(it.date) == ym && it.kind == StoppageKind.STRIKE }.sumOf { it.hours } +
            stoppages.filter { YearMonth.from(it.date) == ym && it.kind == StoppageKind.OVERTIME }.sumOf { it.hours }
        val nightHours = work.sumOf { it.nightHours(c) }
        val holidayHours = work.filter { it.holidayWorked }.sumOf { it.workedHours(c) }
        val medicalHours = med.sumOf { it.hours }

        val payments = max(c.payments, 12)
        val extraCount = payments - 12
        val prorate = extraCount == 0 || c.proratedExtras
        // Paga extra prorrateada (para la base de cotización) y paga extra del mes, si toca.
        val extraAmount = c.baseMonthly + seniority
        val prorata = extraAmount * extraCount / 12.0
        val monthlyBase = if (prorate) c.baseMonthly + prorata else c.baseMonthly
        // La paga extra (jornal sin prima) se cobra en un recibo aparte, sin Seguridad Social
        // (ya va prorrateada en la base de cada mes); sólo lleva IRPF.
        // Se genera por semestres (julio: enero–junio; diciembre: julio–diciembre) y se pierde la parte
        // de los días de permiso de nacimiento (la paga la Seguridad Social) y de huelga.
        val extraDay = c.extraPayDay(ym.monthValue)
        val extra = extraDay?.let { day ->
            val from = if (ym.monthValue <= 9) LocalDate.of(ym.year, 1, 1) else LocalDate.of(ym.year, 7, 1)
            val to = if (ym.monthValue <= 9) LocalDate.of(ym.year, 6, 30) else LocalDate.of(ym.year, 12, 31)
            val periodDays = (to.toEpochDay() - from.toEpochDay() + 1).toInt()
            val lost = days.count { it.date in from..to && (it.shift == ShiftType.PARENTAL || it.shift == ShiftType.STRIKE) }
            val g = extraAmount * (periodDays - lost).coerceAtLeast(0) / periodDays
            val irpf = g * irpfPct / 100.0
            ExtraPaySummary(ym.year, ym.monthValue, day, g, irpf, g - irpf, extraPayslip, lost, periodDays)
        }

        // La prima no se cobra en las horas de médico (cualquier tipo) ni en los días de libre disposición.
        val allMedicalHours = medical.filter { YearMonth.from(it.date) == dm }.sumOf { it.hours }
        val personalDays = inMonth.count { it.shift == ShiftType.PERSONAL }
        val personalDaysDiscounted = days.count { YearMonth.from(it.date) == dm && it.shift == ShiftType.PERSONAL }
        val unpaidBonusHours = allMedicalHours + personalDaysDiscounted * c.normalDayHours
        val bonusDiscount = unpaidBonusHours * c.bonusPerHour

        // Huelga: el día entero (las horas que tocaban según el calendario) y los paros por horas
        // no se cobran ni de salario ni de prima. Asamblea: se cobra el salario, no la prima.
        val stops = stoppages.filter { YearMonth.from(it.date) == dm }
        val strikeDayList = days.filter { YearMonth.from(it.date) == dm && it.shift == ShiftType.STRIKE }
        val strikeDayHours = strikeDayList.sumOf { d -> calendar[d.date]?.let { plannedHours(it, c) }?.takeIf { it > 0 } ?: c.normalDayHours }
        val strikeHours = stops.filter { it.kind == StoppageKind.STRIKE }.sumOf { it.hours }
        val strikeDeduction = (strikeDayHours + strikeHours) * (c.effectiveSalaryPerHour + c.bonusPerHour)
        val assemblyHours = stops.filter { it.kind == StoppageKind.ASSEMBLY }.sumOf { it.hours }
        val assemblyDiscount = assemblyHours * c.bonusPerHour

        // Permiso por nacimiento: la empresa sólo paga los días que no lo son (mes de 30 días).
        val parentalDays = inMonth.count { it.shift == ShiftType.PARENTAL }.coerceAtMost(30)
        val fixedMonthly = monthlyBase + seniority + c.bonusMonthly

        // Horas extra / compensadas (se pagan en el mes en que se hacen) y plus de turno de noche.
        val overtime = stoppages.filter { YearMonth.from(it.date) == ym && it.kind == StoppageKind.OVERTIME }
        val overtimeHours = overtime.sumOf { it.hours }
        val overtimePay = overtime.sumOf { it.hours * overtimePrice(it.date, calendar[it.date], c) }
        val nightShifts = work.count { it.shift == ShiftType.NIGHT && it.workedHours(c) > 4.0 }
        val nightShiftPlus = nightShifts * c.nightShiftBonus
        val parentalDeduction = fixedMonthly * parentalDays / 30.0

        val nightPlus = nightHours * c.nightBonusPerHour
        val holidayPlus = holidayHours * c.holidayBonusPerHour
        val activable = work.filter { calendar[it.date]?.activable == true }
        val activableHours = activable.sumOf { it.workedHours(c) }
        val activablePlus = activableHours * c.activablePricePerHour
        val calendarHours = calendar.filter { YearMonth.from(it.key) == ym }.values.sumOf { plannedHours(it, c) }
        val gross = monthlyBase + seniority + c.bonusMonthly + overtimePay + nightShiftPlus - parentalDeduction - bonusDiscount - strikeDeduction - assemblyDiscount + nightPlus + holidayPlus + activablePlus

        // Base de cotización: lo devengado sin la paga extra del mes, más la prorrata de las extra,
        // lo que paga la empresa (seguro…) y su aportación a la EPSV (igual a la del trabajador).
        val g = c.geroaPercent / 100.0
        val workedShare = (30 - parentalDays) / 30.0
        val geroaBase = gross + (if (prorate) 0.0 else prorata * workedShare) + c.employerMonthlyExtras * workedShare
        val geroa = geroaBase * g
        val ssBase = geroaBase * (1 + g)
        val ss = ssBase * c.ssPercent / 100.0
        val irpf = gross * irpfPct / 100.0

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
            strikeDays = strikeDayList.size,
            strikeHours = strikeDayHours + strikeHours,
            strikeDeduction = strikeDeduction,
            assemblyHours = assemblyHours,
            assemblyDiscount = assemblyDiscount,
            overtimeHours = overtimeHours,
            overtimePay = overtimePay,
            nightShifts = nightShifts,
            nightShiftPlus = nightShiftPlus,
            seniority = seniority,
            irpfPercent = irpfPct,
            parentalDays = parentalDays,
            parentalDeduction = parentalDeduction,
            extra = extra,
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
        extraPayslips: Collection<Payslip> = emptyList(),
        stoppages: Collection<Stoppage> = emptyList(),
    ): YearSummary {
        val yMed = medical.filter { it.date.year == year }
        // El IRPF de cada mes es el de su nómina o, si no hay, el de la última nómina anterior del año.
        val yDays2 = days.filter { it.date.year == year || it.date.year == year - 1 }
        var lastIrpf: Double? = null
        val months = (1..12).map { m ->
            val slip = payslips.firstOrNull { it.year == year && it.month == m }
            slip?.irpfPercent?.let { lastIrpf = it }
            month(YearMonth.of(year, m), yDays2, medical, c, slip, calendar,
                extraPayslips.firstOrNull { it.year == year && it.month == m }, stoppages, slip?.irpfPercent ?: lastIrpf)
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
            expectedGross = months.sumOf { it.expectedGross + (it.extra?.gross ?: 0.0) },
            receivedGross = withSlip.sumOf { it.payslip?.gross ?: 0.0 } + months.sumOf { it.extra?.payslip?.gross ?: 0.0 },
            expectedNet = months.sumOf { it.expectedNet + (it.extra?.net ?: 0.0) },
            receivedNet = withSlip.sumOf { it.payslip?.net ?: 0.0 } + months.sumOf { it.extra?.payslip?.net ?: 0.0 },
            monthsWithPayslip = withSlip.size,
        )
    }

    /** Precio de la hora extra / compensada según el día: domingo o festivo, sábado o laborable. */
    fun overtimePrice(date: LocalDate, type: DayType?, c: Conditions): Double {
        val sunday = date.dayOfWeek == java.time.DayOfWeek.SUNDAY || type == DayType.FESTIVO
        val saturday = date.dayOfWeek == java.time.DayOfWeek.SATURDAY || type == DayType.SABADO_FR || type == DayType.ACTIVABLE_FR
        val fallback = if (c.overtimePrice > 0) c.overtimePrice else c.hourlyRate
        return when {
            sunday -> c.overtimeSundayPrice.takeIf { it > 0 } ?: fallback
            saturday -> c.overtimeSaturdayPrice.takeIf { it > 0 } ?: fallback
            else -> c.overtimeWeekdayPrice.takeIf { it > 0 } ?: fallback
        }
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
