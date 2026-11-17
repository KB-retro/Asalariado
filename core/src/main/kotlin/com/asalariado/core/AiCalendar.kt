package com.asalariado.core

import java.time.YearMonth

/**
 * Lectura del calendario laboral con IA (Gemini): las instrucciones que se le mandan con la
 * imagen y cómo se convierte su respuesta en un [AnnualCalendar].
 */
object AiCalendar {
    /** Tipos que se le piden a la IA (los días que no aparecen son laborables normales). */
    val KEYS = listOf(
        DayType.FESTIVO, DayType.SABADO_FR, DayType.VACACIONES, DayType.PUENTE, DayType.JORNADA_CORTA,
        DayType.JORNADA_4H, DayType.ACTIVABLE_LABORABLE, DayType.ACTIVABLE_FR,
    )

    val PROMPT = """
Eres un lector de calendarios laborales anuales de empresa (España). La imagen o PDF adjunto es un
calendario de 12 meses en el que cada día tiene un color de fondo según su tipo. Lee la leyenda del
propio calendario si la tiene. Si no, usa estos colores habituales:
- Rojo: domingos y festivos -> FESTIVO
- Verde (oscuro o medio): sábados y festivos recuperables (F.R.) -> SABADO_FR
- Azul oscuro: vacaciones -> VACACIONES
- Verde lima/chillón o azul celeste claro: puentes -> PUENTE
- Amarillo: jornadas de 6 horas -> JORNADA_CORTA
- Naranja: jornadas de 4 horas -> JORNADA_4H
- Morado/magenta intenso: activables laborables -> ACTIVABLE_LABORABLE
- Rosa claro: activables festivos recuperables -> ACTIVABLE_FR
- Blanco o sin color: laborable normal (no lo pongas en la lista)

Responde SOLO con JSON con esta forma exacta:
{"year": 2027, "months": [{"month": 1, "FESTIVO": [1, 3], "SABADO_FR": [2, 9], "VACACIONES": [], "PUENTE": [],
"JORNADA_CORTA": [5], "JORNADA_4H": [], "ACTIVABLE_LABORABLE": [], "ACTIVABLE_FR": []}, ...]}
- "year": el año del calendario (el título, p. ej. «2027»).
- Un objeto por cada mes que aparezca (month de 1 a 12), con los números de día de cada tipo.
- Fíjate bien en el color de CADA casilla, día a día; no deduzcas por el día de la semana.
- Ignora la tabla resumen de horas que pueda haber debajo.
""".trim()

    /**
     * @param months mes -> (nombre de tipo -> días). Los días de cada mes que no estén en ninguna
     * lista se toman como laborables.
     */
    fun build(year: Int, months: Map<Int, Map<String, List<Int>>>): AnnualCalendar? {
        if (year !in 2000..2100) return null
        val days = LinkedHashMap<java.time.LocalDate, DayType>()
        val found = months.keys.filter { it in 1..12 }.sorted()
        for (m in found) {
            val ym = YearMonth.of(year, m)
            for (d in 1..ym.lengthOfMonth()) days[ym.atDay(d)] = DayType.LABORABLE
            for ((key, list) in months.getValue(m)) {
                val type = DayType.fromName(key.trim().uppercase()) ?: continue
                for (d in list) if (d in 1..ym.lengthOfMonth()) days[ym.atDay(d)] = type
            }
        }
        return if (found.isEmpty()) null else AnnualCalendar(year, days, found)
    }
}
