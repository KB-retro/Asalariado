package com.asalariado.core

/** Un escalón de la tabla salarial: importes mensuales y por hora. */
data class Escalon(
    val numero: Int,
    /** Salario (jornal) mensual; también el importe de cada paga extra. */
    val salarioMes: Double,
    /** Prima mensual (12 pagas). */
    val primaMes: Double,
    val salarioHora: Double,
    val primaHora: Double,
    val salarioAnual: Double,
    val primaAnual: Double,
) {
    val totalAnual: Double get() = salarioAnual + primaAnual
}

data class TablaSalarial(
    val nombre: String,
    val escalones: List<Escalon>,
    /** Horas de la jornada anual en que se basa la tabla. */
    val jornadaAnual: Double,
    /** Plus de nocturnidad: porcentaje sobre el salario hora. */
    val nocturnidadPct: Double,
) {
    fun escalon(n: Int?): Escalon? = escalones.firstOrNull { it.numero == n }

    /**
     * Aplica el escalón a las condiciones: jornal, prima, prima por hora, plus de
     * nocturnidad (porcentaje del salario hora), pagas (14) y jornada anual.
     */
    fun aplicar(c: Conditions, n: Int): Conditions {
        val e = escalon(n) ?: return c
        return c.copy(
            escalon = n,
            baseMonthly = e.salarioMes,
            bonusMonthly = e.primaMes,
            bonusPerHour = e.primaHora,
            salaryPerHour = e.salarioHora,
            payments = 14,
            proratedExtras = false,
            extraPayMonths = listOf(7, 12),
            extraPayDays = listOf(20, 15),
            nightBonusPerHour = Math.round(e.salarioHora * nocturnidadPct) / 100.0,
            annualHours = jornadaAnual,
        )
    }

    companion object {
        /** Bellota Herramientas – Tabla 1, personal MOD, vigencia 1.1.2026. */
        val BELLOTA_2026 = TablaSalarial(
            nombre = "Bellota MOD 2026",
            jornadaAnual = 1592.0,
            nocturnidadPct = 20.0,
            escalones = listOf(
                Escalon(9, 2059.81, 715.93, 13.6149, 4.8621, 28837.40, 8591.14),
                Escalon(10, 2094.02, 745.38, 13.8411, 5.0621, 29316.22, 8944.53),
                Escalon(11, 2128.13, 778.28, 14.0667, 5.2856, 29793.78, 9339.37),
                Escalon(12, 2165.76, 811.51, 14.3156, 5.5113, 30320.66, 9738.17),
                Escalon(13, 2203.65, 847.44, 14.5662, 5.7553, 30851.14, 10169.24),
                Escalon(14, 2245.04, 883.90, 14.8399, 6.0029, 31430.52, 10606.74),
                Escalon(15, 2286.68, 924.16, 15.1153, 6.2763, 32013.50, 11089.93),
                Escalon(16, 2331.92, 962.77, 15.4145, 6.5385, 32646.85, 11553.22),
                Escalon(17, 2379.14, 1006.94, 15.7268, 6.8385, 33307.94, 12083.23),
                Escalon(18, 2429.47, 1051.01, 16.0597, 7.1378, 34012.63, 12612.08),
                Escalon(19, 2481.87, 1096.80, 16.4062, 7.4488, 34746.12, 13161.60),
                Escalon(20, 2536.09, 1148.04, 16.7648, 7.7968, 35505.21, 13776.44),
                Escalon(21, 2595.83, 1200.37, 17.1599, 8.1522, 36341.58, 14404.41),
            ),
        )
    }
}
