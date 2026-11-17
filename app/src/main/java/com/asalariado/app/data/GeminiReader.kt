package com.asalariado.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.asalariado.core.AiCalendar
import com.asalariado.core.AnnualCalendar
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lee el calendario laboral con Gemini (IA de Google) usando la clave del propio usuario.
 * Sólo se manda el calendario; las nóminas nunca salen del móvil.
 */
object GeminiReader {
    class AiError(msg: String) : Exception(msg)

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    /** Se prueban en orden; si Google los retira, se busca el «flash» más nuevo disponible. */
    private val PREFERRED = listOf("gemini-flash-latest", "gemini-2.5-flash")
    /** Modelos más ligeros, por si los anteriores están saturados. */
    private val FALLBACK = listOf("gemini-flash-lite-latest", "gemini-2.5-flash-lite")

    fun readCalendar(context: Context, uri: Uri, key: String, pdfPassword: String?): AnnualCalendar {
        val (mime, bytes) = payload(context, uri, pdfPassword)
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("inline_data", JSONObject().put("mime_type", mime)
                    .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))))
                .put(JSONObject().put("text", AiCalendar.PROMPT)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0))
            .toString()

        var lastError: AiError? = null
        val models = (PREFERRED + listOfNotNull(runCatching { newestFlash(key) }.getOrNull()) + FALLBACK).distinct()
        for (model in models) {
            // Si Google está saturado (503/500), se reintenta un par de veces y luego se prueba otro modelo.
            for (attempt in 0..2) {
                val (code, text) = http("$BASE/models/$model:generateContent", key, body)
                when {
                    code == 200 -> return parse(text)
                    code == 404 -> { lastError = AiError("modelo $model no disponible"); break }
                    code == 400 && text.contains("API_KEY", ignoreCase = true) ->
                        throw AiError("la clave de Gemini no es válida (revísala en Ajustes)")
                    code == 403 -> throw AiError("la clave de Gemini no tiene permiso (revísala en Ajustes)")
                    code == 429 -> { lastError = AiError("se ha superado el límite gratuito de Gemini; prueba más tarde"); break }
                    code >= 500 -> {
                        lastError = AiError("los servidores de Gemini están saturados ahora mismo; prueba en unos minutos")
                        if (attempt < 2) Thread.sleep(if (attempt == 0) 2_000L else 6_000L)
                    }
                    else -> throw AiError("Gemini respondió con error $code: ${googleMessage(text)}")
                }
            }
        }
        throw lastError ?: AiError("no hay ningún modelo de Gemini disponible")
    }

    private fun googleMessage(text: String): String =
        runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault("").take(160)

    /** El PDF tal cual (sin contraseña) o la imagen reducida a JPEG. */
    private fun payload(context: Context, uri: Uri, pdfPassword: String?): Pair<String, ByteArray> {
        if (PdfText.isPdf(context, uri)) {
            val doc = PdfText.read(context, uri, pdfPassword)
            return "application/pdf" to doc.unlocked
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 3000) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw AiError("no se pudo abrir la imagen")
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        return "image/jpeg" to out.toByteArray()
    }

    private fun http(url: String, key: String, body: String?): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 120_000
            conn.setRequestProperty("x-goog-api-key", key)
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } catch (e: java.io.IOException) {
            throw AiError("sin conexión con Google (${e.javaClass.simpleName})")
        } finally {
            conn.disconnect()
        }
    }

    /** El modelo «flash» más reciente que la clave puede usar. */
    private fun newestFlash(key: String): String? {
        val (code, text) = http("$BASE/models?pageSize=200", key, null)
        if (code != 200) return null
        val arr = JSONObject(text).optJSONArray("models") ?: return null
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { m ->
                val name = m.optString("name")
                val methods = m.optJSONArray("supportedGenerationMethods")
                name.contains("flash") && !name.contains("lite") && !name.contains("image") && !name.contains("tts") &&
                    !name.contains("live") && methods != null && (0 until methods.length()).any { methods.getString(it) == "generateContent" }
            }
            .map { it.optString("name").removePrefix("models/") }
            .maxByOrNull { name -> Regex("""\d+(\.\d+)?""").find(name)?.value?.toDoubleOrNull() ?: 0.0 }
    }

    private fun parse(response: String): AnnualCalendar {
        val parts = JSONObject(response).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: throw AiError("Gemini no devolvió respuesta")
        val text = (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
            .trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = runCatching { JSONObject(text) }.getOrElse { throw AiError("la respuesta de Gemini no se entiende") }
        val year = json.optInt("year", 0)
        val months = json.optJSONArray("months") ?: throw AiError("Gemini no encontró los meses")
        val map = HashMap<Int, Map<String, List<Int>>>()
        for (i in 0 until months.length()) {
            val m = months.optJSONObject(i) ?: continue
            val month = m.optInt("month", 0)
            val types = HashMap<String, List<Int>>()
            for (k in m.keys()) {
                val arr = m.optJSONArray(k) ?: continue
                types[k] = (0 until arr.length()).mapNotNull { arr.optInt(it, -1).takeIf { d -> d > 0 } }
            }
            map[month] = types
        }
        return AiCalendar.build(year, map) ?: throw AiError("Gemini no encontró los meses")
    }
}
