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
     * Los días no laborables quedan como Libre.
     */
    fun generate(from: LocalDate, to: LocalDate, c: Conditions): List<DayEntry> {
        val anchor = c.rotationAnchor ?: mondayOf(from)
        val cycle = c.rotation.ifEmpty { listOf(ShiftType.MORNING) }
        val out = ArrayList<DayEntry>()
        var d = from
        while (!d.isAfter(to)) {
            val shift = if (d.dayOfWeek in c.workDays) shiftForWeek(d, anchor, cycle) else ShiftType.OFF
            out += DayEntry(date = d, shift = shift, source = Source.PATTERN)
            d = d.plusDays(1)
        }
        return out
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
