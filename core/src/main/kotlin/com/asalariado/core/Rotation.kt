package com.asalariado.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object Rotation {

    fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Turno de la semana que contiene [date] según el ciclo y la semana ancla. */
    fun shiftForWeek(date: LocalDate, anchor: LocalDate, cycle: List<ShiftType>): ShiftType {
        require(cycle.isNotEmpty())
        val weeks = ChronoUnit.WEEKS.between(mondayOf(anchor), mondayOf(date))
        return cycle[Math.floorMod(weeks, cycle.size.toLong()).toInt()]
    }

    /**
     * Genera los días entre [from] y [to] (ambos incluidos) siguiendo la rotación.
     * Si hay calendario laboral para un día, manda él: festivos, vacaciones y puentes no se trabajan
     * y las jornadas cortas duran [Conditions.shortDayHours]. Sin calendario se usan los días laborables.
     */
    fun generate(
        from: LocalDate,
        to: LocalDate,
        c: Conditions,
        calendar: Map<LocalDate, DayType> = emptyMap(),
    ): List<DayEntry> {
        val anchor = c.rotationAnchor ?: mondayOf(from)
        val cycle = c.rotation.ifEmpty { listOf(ShiftType.MORNING) }
        val out = ArrayList<DayEntry>()
        var d = from
        while (!d.isAfter(to)) {
            out += plannedDay(d, shiftForWeek(d, anchor, cycle), c, calendar[d])
            d = d.plusDays(1)
        }
        return out
    }

    /** Día planificado para un turno teniendo en cuenta el tipo de día del calendario laboral. */
    fun plannedDay(d: LocalDate, shift: ShiftType, c: Conditions, type: DayType?): DayEntry = when {
        type == null ->
            DayEntry(d, if (d.dayOfWeek in c.workDays) shift else ShiftType.OFF, source = Source.PATTERN)
        type.workDay -> {
            val def = c.hoursFor(shift)
            if ((type == DayType.JORNADA_CORTA || type == DayType.JORNADA_4H) && def != null) {
                val end = def.start.plusMinutes((Payroll.plannedHours(type, c) * 60).toLong())
                DayEntry(d, shift, def.start, end, source = Source.PATTERN)
            } else {
                DayEntry(d, shift, source = Source.PATTERN)
            }
        }
        type == DayType.VACACIONES -> DayEntry(d, ShiftType.VACATION, source = Source.PATTERN)
        type == DayType.FESTIVO -> DayEntry(d, ShiftType.HOLIDAY, source = Source.PATTERN)
        else -> DayEntry(d, ShiftType.OFF, source = Source.PATTERN)
    }

    /**
     * Fusiona la rotación generada con lo existente: los días editados a mano o importados
     * se respetan salvo que [overwrite] sea true.
     */
    fun merge(existing: Map<LocalDate, DayEntry>, generated: List<DayEntry>, overwrite: Boolean): List<DayEntry> =
        generated.filter { g ->
            val e = existing[g.date]
            e == null || overwrite || e.source == Source.PATTERN
        }
}
