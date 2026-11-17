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
            put("grossAnnualSalary", c.grossAnnualSalary)
            put("annualHours", c.annualHours)
            put("payments", c.payments)
            put("extraPayMonths", JSONArray(c.extraPayMonths))
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
            return Conditions(
                grossAnnualSalary = o.optDouble("grossAnnualSalary", d.grossAnnualSalary),
                annualHours = o.optDouble("annualHours", d.annualHours),
                payments = o.optInt("payments", d.payments),
                extraPayMonths = o.optJSONArray("extraPayMonths")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: d.extraPayMonths,
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
                rotation = strings("rotation")?.map { ShiftType.fromName(it) }?.ifEmpty { null } ?: d.rotation,
                rotationAnchor = o.optString("rotationAnchor", "").takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) },
            )
        }
    }
}
