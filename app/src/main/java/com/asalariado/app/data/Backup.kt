package com.asalariado.app.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/** Copia de seguridad de todos los datos en un JSON que se puede guardar y volver a cargar. */
object Backup {
    private fun JSONObject.optIntOrNull(k: String) = if (isNull(k) || !has(k)) null else getInt(k)
    private fun JSONObject.optDoubleOrNull(k: String) = if (isNull(k) || !has(k)) null else getDouble(k)

    suspend fun export(db: AppDatabase, conditions: JSONObject): String {
        val dao = db.dao()
        val root = JSONObject()
        root.put("app", "Asalariado")
        root.put("version", 1)
        root.put("conditions", conditions)
        root.put("days", JSONArray(dao.daysOnce().map {
            JSONObject().put("d", it.epochDay).put("shift", it.shift).put("s", it.startMin ?: JSONObject.NULL)
                .put("e", it.endMin ?: JSONObject.NULL).put("hw", it.holidayWorked).put("note", it.note).put("src", it.source)
        }))
        root.put("calendar", JSONArray(dao.calendarOnce().map { JSONObject().put("d", it.epochDay).put("t", it.type) }))
        root.put("medical", JSONArray(dao.medicalOnce().map {
            JSONObject().put("d", it.epochDay).put("s", it.startMin).put("e", it.endMin).put("reason", it.reason)
                .put("just", it.justified).put("kind", it.kind)
        }))
        root.put("payslips", JSONArray(dao.payslipsOnce().map {
            JSONObject().put("y", it.year).put("m", it.month).put("g", it.gross ?: JSONObject.NULL)
                .put("n", it.net ?: JSONObject.NULL).put("note", it.note)
        }))
        root.put("extraPayslips", JSONArray(dao.extraPayslipsOnce().map {
            JSONObject().put("y", it.year).put("m", it.month).put("g", it.gross ?: JSONObject.NULL)
                .put("n", it.net ?: JSONObject.NULL).put("note", it.note)
        }))
        root.put("stoppages", JSONArray(dao.stoppagesOnce().map {
            JSONObject().put("d", it.epochDay).put("k", it.kind).put("h", it.hours)
        }))
        return root.toString(1)
    }

    /** Sustituye todos los datos por los de la copia. Devuelve las condiciones guardadas en ella. */
    suspend fun import(db: AppDatabase, json: String): JSONObject? {
        val root = JSONObject(json)
        require(root.optString("app") == "Asalariado") { "el archivo no es una copia de Asalariado" }
        val dao = db.dao()
        fun arr(k: String) = root.optJSONArray(k) ?: JSONArray()
        fun objs(k: String) = arr(k).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        db.withTransaction {
            dao.clearDays(); dao.clearMedical(); dao.clearPayslips(); dao.clearCalendar(); dao.clearExtraPayslips(); dao.clearStoppages()
            dao.insertStoppages(objs("stoppages").map { StoppageEntity(it.getLong("d"), it.getString("k"), it.getDouble("h")) })
            dao.insertExtraPayslips(objs("extraPayslips").map {
                ExtraPayslipEntity(it.getInt("y"), it.getInt("m"), it.optDoubleOrNull("g"), it.optDoubleOrNull("n"), it.optString("note"))
            })
            dao.upsertDays(objs("days").map {
                DayEntity(it.getLong("d"), it.getString("shift"), it.optIntOrNull("s"), it.optIntOrNull("e"),
                    it.optBoolean("hw"), it.optString("note"), it.optString("src", "MANUAL"))
            })
            dao.upsertCalendarDays(objs("calendar").map { CalendarDayEntity(it.getLong("d"), it.getString("t")) })
            dao.insertMedical(objs("medical").map {
                MedicalEntity(0, it.getLong("d"), it.getInt("s"), it.getInt("e"), it.optString("reason"),
                    it.optBoolean("just", true), it.optString("kind", "COMMON"))
            })
            dao.insertPayslips(objs("payslips").map {
                PayslipEntity(it.getInt("y"), it.getInt("m"), it.optDoubleOrNull("g"), it.optDoubleOrNull("n"), it.optString("note"))
            })
        }
        return root.optJSONObject("conditions")
    }
}
