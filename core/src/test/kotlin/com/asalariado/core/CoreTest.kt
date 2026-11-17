package com.asalariado.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RotationTest {
    @Test
    fun alternatesWeeks() {
        val c = Conditions(rotationAnchor = LocalDate.of(2026, 11, 2)) // lunes
        val days = Rotation.generate(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 22), c).associateBy { it.date }
        assertEquals(ShiftType.MORNING, days[LocalDate.of(2026, 11, 4)]!!.shift)
        assertEquals(ShiftType.OFF, days[LocalDate.of(2026, 11, 8)]!!.shift) // domingo
        assertEquals(ShiftType.AFTERNOON, days[LocalDate.of(2026, 11, 10)]!!.shift)
        assertEquals(ShiftType.MORNING, days[LocalDate.of(2026, 11, 20)]!!.shift)
        // Antes del ancla también funciona.
        assertEquals(ShiftType.AFTERNOON, Rotation.shiftForWeek(LocalDate.of(2026, 10, 28), c.rotationAnchor!!, c.rotation))
    }

    @Test
    fun mergeKeepsManual() {
        val d = LocalDate.of(2026, 11, 3)
        val existing = mapOf(d to DayEntry(d, ShiftType.VACATION, source = Source.MANUAL))
        val gen = listOf(DayEntry(d, ShiftType.MORNING, source = Source.PATTERN))
        assertTrue(Rotation.merge(existing, gen, overwrite = false).isEmpty())
        assertEquals(1, Rotation.merge(existing, gen, overwrite = true).size)
    }
}

class HoursTest {
    @Test
    fun nightHours() {
        assertEquals(8.0, hoursBetween(LocalTime.of(22, 0), LocalTime.of(6, 0)))
        assertEquals(8.0, nightHoursBetween(LocalTime.of(22, 0), LocalTime.of(6, 0)))
        assertEquals(0.0, nightHoursBetween(LocalTime.of(6, 0), LocalTime.of(14, 0)))
        assertEquals(1.0, nightHoursBetween(LocalTime.of(15, 0), LocalTime.of(23, 0)))
        assertEquals(1.0, nightHoursBetween(LocalTime.of(5, 0), LocalTime.of(13, 0)))
    }
}

class PayrollTest {
    private val c = Conditions(baseMonthly = 1500.0, payments = 14, irpfPercent = 10.0, ssPercent = 6.0, nightBonusPerHour = 2.0)

    @Test
    fun extraPayIsSeparate() {
        val june = Payroll.month(YearMonth.of(2026, 6), emptyList(), emptyList(), c, null)
        val july = Payroll.month(YearMonth.of(2026, 7), emptyList(), emptyList(), c, null, extraPayslip = Payslip(2026, 7, 1300.0, 1300.0))
        // La nómina de julio no lleva la extra: va en su propio recibo hacia el día 20.
        assertEquals(null, june.extra)
        assertEquals(1500.0, july.expectedGross, 0.001)
        // SS sobre base con la extra prorrateada: 1500 + 250 = 1750
        assertEquals(105.0, july.ssDeduction, 0.001)
        assertEquals(1500.0 - 105.0 - 150.0, july.expectedNet, 0.001)
        val extra = july.extra!!
        assertEquals(20, extra.day)
        assertEquals(1500.0, extra.gross, 0.001)
        assertEquals(1350.0, extra.net, 0.001)
        assertEquals(-50.0, extra.diffNet!!, 0.001)
        assertEquals(15, Payroll.month(YearMonth.of(2026, 12), emptyList(), emptyList(), c, null).extra!!.day)
        val y = Payroll.year(2026, emptyList(), emptyList(), emptyList(), c)
        assertEquals(1500.0 * 14, y.expectedGross, 0.001)
    }

    @Test
    fun prorated() {
        val m = Payroll.month(YearMonth.of(2026, 6), emptyList(), emptyList(), c.copy(proratedExtras = true), null)
        assertEquals(1750.0, m.expectedGross, 0.001)
    }

    @Test
    fun nightPlusAndDiff() {
        val days = listOf(DayEntry(LocalDate.of(2026, 3, 2), ShiftType.NIGHT), DayEntry(LocalDate.of(2026, 3, 3), ShiftType.NIGHT))
        val slip = Payslip(2026, 3, gross = 1500.0, net = null)
        val m = Payroll.month(YearMonth.of(2026, 3), days, emptyList(), c, slip)
        assertEquals(16.0, m.nightHours, 0.001)
        assertEquals(1532.0, m.expectedGross, 0.001)
        assertEquals(-32.0, m.diffGross!!, 0.001)
    }

    @Test
    fun yearOvertime() {
        val cc = c.copy(annualHours = 16.0)
        val days = (2..4).map { DayEntry(LocalDate.of(2026, 2, it), ShiftType.MORNING) }
        val med = listOf(
            MedicalVisit(date = LocalDate.of(2026, 2, 5), start = LocalTime.of(9, 0), end = LocalTime.of(11, 0)),
            MedicalVisit(date = LocalDate.of(2026, 2, 6), start = LocalTime.of(9, 0), end = LocalTime.of(10, 0), kind = MedicalKind.SPECIALIST),
        )
        val y = Payroll.year(2026, days, med, emptyList(), cc)
        assertEquals(27.0, y.countedHours, 0.001)
        assertEquals(11.0, y.overtimeHours, 0.001)
        assertEquals(2.0, y.medicalHoursUsed, 0.001)
        assertEquals(1.0, y.specialistHours, 0.001)
    }

    @Test
    fun strikeAndAssembly() {
        val cc = TablaSalarial.BELLOTA_2026.aplicar(Conditions(), 10)
        val ym = YearMonth.of(2026, 3)
        val days = listOf(
            DayEntry(LocalDate.of(2026, 3, 2), ShiftType.STRIKE),
            DayEntry(LocalDate.of(2026, 3, 3), ShiftType.MORNING),
        )
        val stops = listOf(
            Stoppage(LocalDate.of(2026, 3, 3), StoppageKind.STRIKE, 2.0),
            Stoppage(LocalDate.of(2026, 3, 4), StoppageKind.ASSEMBLY, 0.5),
        )
        val base = Payroll.month(ym, emptyList(), emptyList(), cc, null)
        val m = Payroll.month(ym, days, emptyList(), cc, null, stoppages = stops)
        // Huelga: 8 h del día + 2 h de paro a (13,8411 + 5,0621) €/h; asamblea: 0,5 h de prima.
        assertEquals(10.0, m.strikeHours, 0.001)
        assertEquals(10 * (13.8411 + 5.0621), m.strikeDeduction, 0.001)
        assertEquals(0.5 * 5.0621, m.assemblyDiscount, 0.001)
        assertEquals(base.expectedGross - m.strikeDeduction - m.assemblyDiscount, m.expectedGross, 0.001)
        assertEquals(6.0, m.workedHours, 0.001)
    }

    @Test
    fun seniorityOvertimeNightAndIrpf() {
        val cc = TablaSalarial.BELLOTA_2026.aplicar(Conditions(), 10).copy(
            seniorityDate = LocalDate.of(2016, 4, 1), quinquenioMonthly = 30.0, irpfPercent = 15.0,
        )
        // 10 años en abril de 2026 = 2 quinquenios.
        assertEquals(1, cc.quinquenios(YearMonth.of(2026, 3)))
        assertEquals(2, cc.quinquenios(YearMonth.of(2026, 4)))
        assertEquals(26.15, cc.overtimeSaturdayPrice, 0.01)
        assertEquals(24.57, cc.overtimeWeekdayPrice, 0.01)
        val days = listOf(
            DayEntry(LocalDate.of(2026, 5, 4), ShiftType.NIGHT),
            DayEntry(LocalDate.of(2026, 5, 5), ShiftType.MORNING),
        )
        val stops = listOf(
            Stoppage(LocalDate.of(2026, 5, 9), StoppageKind.OVERTIME, 4.0), // sábado
            Stoppage(LocalDate.of(2026, 5, 10), StoppageKind.OVERTIME, 2.0), // domingo
        )
        val base = Payroll.month(YearMonth.of(2026, 5), emptyList(), emptyList(), cc, null)
        val m = Payroll.month(YearMonth.of(2026, 5), days, emptyList(), cc, null, stoppages = stops)
        assertEquals(60.0, m.seniority, 0.001)
        assertEquals(1, m.nightShifts)
        assertEquals(7.92, m.nightShiftPlus, 0.001)
        assertEquals(4 * 26.15 + 2 * cc.overtimeSundayPrice, m.overtimePay, 0.01)
        assertEquals(22.16, m.nightPlus, 0.01)
        assertEquals(base.expectedGross + m.overtimePay + 7.92 + m.nightPlus, m.expectedGross, 0.01)
        // La paga extra incluye la antigüedad.
        assertEquals(2094.02 + 60.0, Payroll.month(YearMonth.of(2026, 7), emptyList(), emptyList(), cc, null).extra!!.gross, 0.01)
        // IRPF: el de la última nómina importada se usa para los meses siguientes.
        val slips = listOf(Payslip(2026, 3, 3000.0, 2200.0, irpfPercent = 18.0))
        val y = Payroll.year(2026, emptyList(), emptyList(), slips, cc)
        assertEquals(15.0, y.months[1].irpfPercent, 0.001)
        assertEquals(18.0, y.months[2].irpfPercent, 0.001)
        assertEquals(18.0, y.months[8].irpfPercent, 0.001)
    }

    @Test
    fun unjustifiedMedicalAndOvertimeNotInAnnual() {
        val cc = TablaSalarial.BELLOTA_2026.aplicar(Conditions(), 10)
        val med = listOf(MedicalVisit(date = LocalDate.of(2026, 9, 10), start = LocalTime.of(8, 0), end = LocalTime.of(10, 0), justified = false))
        val m = Payroll.month(YearMonth.of(2026, 9), emptyList(), med, cc, null)
        assertEquals(0.0, m.bonusDiscount, 0.001)
        assertEquals(2 * (13.8411 + 5.0621), m.strikeDeduction, 0.001)
        val days = listOf(DayEntry(LocalDate.of(2026, 9, 7), ShiftType.MORNING))
        val stops = listOf(Stoppage(LocalDate.of(2026, 9, 7), StoppageKind.OVERTIME, 3.0))
        val m2 = Payroll.month(YearMonth.of(2026, 9), days, emptyList(), cc, null, stoppages = stops)
        assertEquals(8.0, m2.workedHours, 0.001)
        assertEquals(3.0, m2.overtimeHours, 0.001)
    }

    @Test
    fun discountsNextMonth() {
        val cc = TablaSalarial.BELLOTA_2026.aplicar(Conditions(), 10).copy(discountsNextMonth = true)
        val med = listOf(MedicalVisit(date = LocalDate.of(2026, 9, 10), start = LocalTime.of(8, 0), end = LocalTime.of(9, 0)))
        val stops = listOf(Stoppage(LocalDate.of(2026, 9, 11), StoppageKind.ASSEMBLY, 1.0))
        val sep = Payroll.month(YearMonth.of(2026, 9), emptyList(), med, cc, null, stoppages = stops)
        val oct = Payroll.month(YearMonth.of(2026, 10), emptyList(), med, cc, null, stoppages = stops)
        assertEquals(0.0, sep.bonusDiscount + sep.assemblyDiscount, 0.001)
        assertEquals(2 * 5.0621, oct.bonusDiscount + oct.assemblyDiscount, 0.001)
    }

    @Test
    fun parentalLeave() {
        val cc = Conditions(baseMonthly = 2000.0, bonusMonthly = 700.0, payments = 14, irpfPercent = 0.0, ssPercent = 0.0)
        // 16 semanas desde el 1 de marzo de 2026 (112 días: 1 mar – 20 jun).
        val start = LocalDate.of(2026, 3, 1)
        val leave = (0L until 112L).map { DayEntry(start.plusDays(it), ShiftType.PARENTAL) }
        val march = Payroll.month(YearMonth.of(2026, 3), leave, emptyList(), cc, null)
        assertEquals(30, march.parentalDays)
        assertEquals(0.0, march.expectedGross, 0.001)
        val june = Payroll.month(YearMonth.of(2026, 6), leave, emptyList(), cc, null)
        assertEquals(20, june.parentalDays)
        assertEquals(2700.0 * 10 / 30, june.expectedGross, 0.01)
        // Paga extra de julio: se pierde la parte de los 112 días de permiso (enero–junio: 181 días).
        val july = Payroll.month(YearMonth.of(2026, 7), leave, emptyList(), cc, null)
        assertEquals(112, july.extra!!.lostDays)
        assertEquals(2000.0 * (181 - 112) / 181, july.extra!!.gross, 0.01)
        // La de diciembre no se ve afectada.
        assertEquals(2000.0, Payroll.month(YearMonth.of(2026, 12), leave, emptyList(), cc, null).extra!!.gross, 0.01)
    }

    /**
     * Ejemplo con la tabla Bellota 2026, escalón 10: 8 h de sábado activable a 12,2194 €/h,
     * 2 h sin prima, IRPF 17 %, SS 6,50 %, EPSV 2,30 %, seguro de vida 1,33 €.
     */
    @Test
    fun escalon10Example() {
        val cc = TablaSalarial.BELLOTA_2026.aplicar(Conditions(), 10).copy(
            irpfPercent = 17.0, ssPercent = 6.5, geroaPercent = 2.3, employerMonthlyExtras = 1.33,
            activablePricePerHour = 12.2194,
        )
        val d = LocalDate.of(2026, 10, 3)
        val cal = mapOf(d to DayType.SABADO_FR)
        val days = listOf(DayEntry(d, ShiftType.MORNING))
        val med = listOf(MedicalVisit(date = LocalDate.of(2026, 10, 7), start = LocalTime.of(8, 0), end = LocalTime.of(10, 0)))
        val m = Payroll.month(YearMonth.of(2026, 10), days, med, cc, null, cal)
        assertEquals(97.76, m.activablePlus, 0.01)
        assertEquals(10.12, m.bonusDiscount, 0.01)
        assertEquals(2927.04, m.expectedGross, 0.01)
        assertEquals(3352.75, m.ssBase, 0.01)
        assertEquals(75.38, m.geroaDeduction, 0.01)
        assertEquals(497.60, m.irpfDeduction, 0.01)
        assertEquals(2136.13, m.expectedNet, 0.01)
        assertEquals(2.77, cc.nightBonusPerHour, 0.001)
    }
}

class ParserTest {
    private fun line(text: String, top: Int, left: Int = 10, right: Int = left + text.length * 10) =
        TextItem(text, left, top, right, top + 20)

    @Test
    fun listLayout() {
        val items = listOf(
            line("Noviembre 2026", 0),
            line("Lunes 2  Mañana", 40),
            line("Martes 3  Tarde", 70),
            line("04/11 14:00-22:00", 100),
            line("Jue 5 Libre", 130),
            line("6 nov Noche", 160),
        )
        val r = ScheduleParser.parse(items, 2025, 1)
        assertEquals(2026, r.detectedYear)
        assertEquals(11, r.detectedMonth)
        val map = r.days.associate { it.date.dayOfMonth to it.shift }
        assertEquals(ShiftType.MORNING, map[2])
        assertEquals(ShiftType.AFTERNOON, map[3])
        assertEquals(ShiftType.AFTERNOON, map[4])
        assertEquals(ShiftType.OFF, map[5])
        assertEquals(ShiftType.NIGHT, map[6])
        val d4 = r.days.first { it.date.dayOfMonth == 4 }
        assertEquals(LocalTime.of(14, 0), d4.start)
    }

    @Test
    fun gridLayout() {
        // Cabecera de días, fila de números y fila de turnos en columnas.
        val cols = listOf(0, 100, 200, 300, 400, 500, 600)
        val items = ArrayList<TextItem>()
        listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, s -> items += TextItem(s, cols[i], 0, cols[i] + 20, 20) }
        (16..22).forEachIndexed { i, d -> items += TextItem("$d", cols[i], 40, cols[i] + 20, 60) }
        listOf("M", "M", "M", "M", "M", "L", "L").forEachIndexed { i, s -> items += TextItem(s, cols[i], 80, cols[i] + 20, 100) }
        (23..29).forEachIndexed { i, d -> items += TextItem("$d", cols[i], 120, cols[i] + 20, 140) }
        listOf("T", "T", "T", "T", "T", "L", "L").forEachIndexed { i, s -> items += TextItem(s, cols[i], 160, cols[i] + 20, 180) }

        val r = ScheduleParser.parse(items, 2026, 11)
        val map = r.days.associate { it.date.dayOfMonth to it.shift }
        assertEquals(14, map.size, "días: $map")
        assertEquals(ShiftType.MORNING, map[16])
        assertEquals(ShiftType.MORNING, map[20])
        assertEquals(ShiftType.OFF, map[21])
        assertEquals(ShiftType.AFTERNOON, map[23])
        assertEquals(ShiftType.OFF, map[29])
    }

    @Test
    fun gridMergedLines() {
        // ML Kit suele unir una fila de la tabla en una sola línea.
        val items = listOf(
            TextItem("1 2 3 4 5", 0, 0, 450, 20),
            TextItem("M M T T L", 0, 40, 450, 60),
        )
        val r = ScheduleParser.parse(items, 2026, 12)
        val map = r.days.associate { it.date.dayOfMonth to it.shift }
        assertEquals(mapOf(1 to ShiftType.MORNING, 2 to ShiftType.MORNING, 3 to ShiftType.AFTERNOON, 4 to ShiftType.AFTERNOON, 5 to ShiftType.OFF), map)
    }

    @Test
    fun classifyByHour() {
        assertEquals(ShiftType.MORNING, ScheduleParser.classify(LocalTime.of(7, 0), Conditions()))
        assertEquals(ShiftType.AFTERNOON, ScheduleParser.classify(LocalTime.of(15, 0), Conditions()))
        assertEquals(ShiftType.NIGHT, ScheduleParser.classify(LocalTime.of(23, 0), Conditions()))
    }
}

class CalendarIntegrationTest {
    @Test
    fun rotationFollowsCalendarAndActivablesArePaid() {
        val c = Conditions(rotationAnchor = LocalDate.of(2027, 1, 4), activablePricePerHour = 20.0)
        val cal = mapOf(
            LocalDate.of(2027, 1, 4) to DayType.LABORABLE,
            LocalDate.of(2027, 1, 5) to DayType.JORNADA_CORTA,
            LocalDate.of(2027, 1, 6) to DayType.FESTIVO,
            LocalDate.of(2027, 1, 9) to DayType.SABADO_FR,
        )
        val days = Rotation.generate(LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 9), c, cal).associateBy { it.date }
        assertEquals(ShiftType.MORNING, days[LocalDate.of(2027, 1, 4)]!!.shift)
        assertEquals(6.0, days[LocalDate.of(2027, 1, 5)]!!.workedHours(c))
        assertEquals(ShiftType.HOLIDAY, days[LocalDate.of(2027, 1, 6)]!!.shift)
        assertEquals(ShiftType.OFF, days[LocalDate.of(2027, 1, 9)]!!.shift)

        // Te activan el sábado 9: 8 h pagadas como sábado.
        val worked = days.values.map { if (it.date.dayOfMonth == 9) it.copy(shift = ShiftType.MORNING) else it }
        val m = Payroll.month(YearMonth.of(2027, 1), worked, emptyList(), c, null, cal)
        assertEquals(1, m.activableDays)
        assertEquals(160.0, m.activablePlus, 0.001)
        assertEquals(14.0, m.calendarHours, 0.001)
        assertEquals(1, Payroll.year(2027, worked, emptyList(), emptyList(), c, cal).activableUsed)
    }
}
