package com.asalariado.app.data

import android.content.Context
import com.asalariado.core.Conditions
import com.asalariado.core.ShiftHours
import com.asalariado.core.ShiftType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Guarda las condiciones anuales como JSON en SharedPreferences. */
class ConditionsStore(context: Context) {
    private val prefs = context.getSharedPreferences("conditions", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Conditions> = _state

    fun save(c: Conditions) {
        prefs.edit().putString(KEY, toJson(c).toString()).apply()
        _state.value = c
    }

    private fun load(): Conditions =
        prefs.getString(KEY, null)?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() } ?: Conditions()

    companion object {
        private const val KEY = "conditions_v1"

        fun toJson(c: Conditions): JSONObject = JSONObject().apply {
            c.escalon?.let { put("escalon", it) }
            put("baseMonthly", c.baseMonthly)
            put("bonusMonthly", c.bonusMonthly)
            put("bonusPerHour", c.bonusPerHour)
            put("geroaPercent", c.geroaPercent)
            put("employerMonthlyExtras", c.employerMonthlyExtras)
            put("annualHours", c.annualHours)
            put("payments", c.payments)
            put("extraPayMonths", JSONArray(c.extraPayMonths))
            put("extraPayDays", JSONArray(c.extraPayDays))
            put("proratedExtras", c.proratedExtras)
            put("shiftHours", JSONObject().apply {
                c.shiftHours.forEach { (k, v) -> put(k.name, "${v.start}-${v.end}") }
            })
            put("workDays", JSONArray(c.workDays.map { it.name }))
            put("nightBonusPerHour", c.nightBonusPerHour)
            put("holidayBonusPerHour", c.holidayBonusPerHour)
            put("overtimePrice", c.overtimePrice)
            put("irpfPercent", c.irpfPercent)
            put("ssPercent", c.ssPercent)
            put("vacationDays", c.vacationDays)
            put("personalDays", c.personalDays)
            put("medicalHoursPerYear", c.medicalHoursPerYear)
            put("normalDayHours", c.normalDayHours)
            put("shortDayHours", c.shortDayHours)
            put("activablePricePerHour", c.activablePricePerHour)
            put("maxActivablesPerYear", c.maxActivablesPerYear)
            put("rotation", JSONArray(c.rotation.map { it.name }))
            c.rotationAnchor?.let { put("rotationAnchor", it.toString()) }
        }

        fun fromJson(o: JSONObject): Conditions {
            val d = Conditions()
            fun strings(key: String): List<String>? = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }
            val shiftHours = o.optJSONObject("shiftHours")?.let { sh ->
                sh.keys().asSequence().mapNotNull { k ->
                    val parts = sh.getString(k).split("-")
                    runCatching { ShiftType.valueOf(k) to ShiftHours(LocalTime.parse(parts[0]), LocalTime.parse(parts[1])) }.getOrNull()
                }.toMap()
            } ?: d.shiftHours
            // Versiones anteriores guardaban sólo el bruto anual.
            val payments = o.optInt("payments", d.payments).coerceAtLeast(12)
            val oldGross = o.optDouble("grossAnnualSalary", Double.NaN)
            val base = when {
                o.has("baseMonthly") -> o.optDouble("baseMonthly", d.baseMonthly)
                !oldGross.isNaN() -> oldGross / payments
                else -> d.baseMonthly
            }
            return Conditions(
                escalon = if (o.has("escalon")) o.optInt("escalon") else null,
                baseMonthly = base,
                bonusMonthly = o.optDouble("bonusMonthly", d.bonusMonthly),
                bonusPerHour = o.optDouble("bonusPerHour", d.bonusPerHour),
                geroaPercent = o.optDouble("geroaPercent", d.geroaPercent),
                employerMonthlyExtras = o.optDouble("employerMonthlyExtras", d.employerMonthlyExtras),
                annualHours = o.optDouble("annualHours", d.annualHours),
                payments = o.optInt("payments", d.payments),
                // Antes las extra iban por defecto en junio y diciembre; ahora julio (~20) y diciembre (~15).
                extraPayMonths = o.optJSONArray("extraPayMonths")?.let { a -> (0 until a.length()).map { a.getInt(it) } }
                    ?.takeUnless { !o.has("extraPayDays") && it == listOf(6, 12) } ?: d.extraPayMonths,
                extraPayDays = o.optJSONArray("extraPayDays")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: d.extraPayDays,
                proratedExtras = o.optBoolean("proratedExtras", d.proratedExtras),
                shiftHours = shiftHours,
                workDays = strings("workDays")?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }?.toSet() ?: d.workDays,
                nightBonusPerHour = o.optDouble("nightBonusPerHour", d.nightBonusPerHour),
                holidayBonusPerHour = o.optDouble("holidayBonusPerHour", d.holidayBonusPerHour),
                overtimePrice = o.optDouble("overtimePrice", d.overtimePrice),
                irpfPercent = o.optDouble("irpfPercent", d.irpfPercent),
                ssPercent = o.optDouble("ssPercent", d.ssPercent),
                vacationDays = o.optInt("vacationDays", d.vacationDays),
                personalDays = o.optInt("personalDays", d.personalDays),
                medicalHoursPerYear = o.optDouble("medicalHoursPerYear", d.medicalHoursPerYear),
                normalDayHours = o.optDouble("normalDayHours", d.normalDayHours),
                shortDayHours = o.optDouble("shortDayHours", d.shortDayHours),
                activablePricePerHour = o.optDouble("activablePricePerHour", d.activablePricePerHour),
                maxActivablesPerYear = o.optInt("maxActivablesPerYear", d.maxActivablesPerYear),
                rotation = strings("rotation")?.map { ShiftType.fromName(it) }?.ifEmpty { null } ?: d.rotation,
                rotationAnchor = o.optString("rotationAnchor", "").takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) },
            )
        }
    }
}
