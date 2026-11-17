package com.asalariado.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.asalariado.core.PixelSource
import com.asalariado.core.TextItem
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reconoce el texto de una imagen con ML Kit (en el dispositivo, sin conexión). */
object Ocr {
    data class Result(
        /** Líneas de texto con su caja. */
        val lines: List<TextItem>,
        /** Palabras sueltas con su caja (mejor para cuadrículas de números). */
        val words: List<TextItem>,
        val fullText: String,
        /** Píxeles de la imagen para leer los colores de las casillas. */
        val pixels: PixelSource,
    )

    private class BitmapPixels(bmp: Bitmap) : PixelSource {
        override val width = bmp.width
        override val height = bmp.height
        private val data = IntArray(width * height).also { bmp.getPixels(it, 0, width, 0, 0, width, height) }
        override fun rgb(x: Int, y: Int): Int = data[y * width + x] and 0xFFFFFF
    }

    /** Reconoce una imagen o un PDF (cada página es un resultado). */
    suspend fun recognize(context: Context, uri: Uri, password: String? = null): List<Result> {
        if (isPdf(context, uri)) {
            // PDF con texto (como el del calendario): se lee el texto del propio PDF, exacto y sin OCR,
            // y los colores de la página dibujada. Sólo si es una foto escaneada se usa OCR.
            val pages = withContext(Dispatchers.IO) { PdfText.pagesWithText(context, uri, password) }
            return pages.map { (bmp, items) ->
                if (items.size > 40) {
                    val words = com.asalariado.core.AnnualCalendarParser.splitWords(items)
                    Result(items, words, items.joinToString("\n") { it.text }, withContext(Dispatchers.Default) { BitmapPixels(bmp) })
                } else {
                    recognizeBitmap(context, bmp)
                }
            }
        }
        val bmp = withContext(Dispatchers.IO) { decodeImage(context, uri) }
        return listOf(recognizeBitmap(context, bmp))
    }

    private fun isPdf(context: Context, uri: Uri): Boolean =
        context.contentResolver.getType(uri) == "application/pdf" || uri.toString().lowercase().endsWith(".pdf")

    /** Abre la imagen reduciéndola si es muy grande (una foto de 12 MP agotaría la memoria). */
    private fun decodeImage(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2600) sample *= 2
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sample
            })
        } ?: error("formato de imagen no soportado")
    }

    /** Dibuja las páginas del PDF (máx. 4) a unos 2000 px de ancho sobre fondo blanco. */
    private fun renderPdf(context: Context, uri: Uri): List<Bitmap> {
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: error("no se pudo abrir el PDF")
        return fd.use {
            val renderer = PdfRenderer(it)
            try {
                (0 until minOf(renderer.pageCount, 4)).map { i ->
                    val page = renderer.openPage(i)
                    try {
                        val scale = 2000f / page.width
                        val h = (page.height * scale).toInt().coerceIn(1, 4000)
                        val bmp = Bitmap.createBitmap(2000, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        val m = Matrix().apply { setScale(scale, scale) }
                        page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    } finally {
                        page.close()
                    }
                }
            } finally {
                renderer.close()
            }
        }
    }

    /** Error claro cuando el lector de texto de Google aún no está descargado. */
    class ModelNotReady : Exception(
        "El lector de texto se está descargando de Google (hace falta internet la primera vez). Vuelve a intentarlo en un minuto.",
    )

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }

    /** Se asegura de que el modelo de OCR de Google Play Services está descargado (espera hasta 90 s). */
    private suspend fun ensureModel(context: Context, recognizer: TextRecognizer) {
        val client = ModuleInstall.getClient(context)
        val ok = runCatching { client.areModulesAvailable(recognizer).awaitTask().areModulesAvailable() }.getOrDefault(true)
        if (ok) return
        runCatching { client.installModules(ModuleInstallRequest.newBuilder().addApi(recognizer).build()).awaitTask() }
        repeat(45) {
            delay(2000)
            if (runCatching { client.areModulesAvailable(recognizer).awaitTask().areModulesAvailable() }.getOrDefault(false)) return
        }
        throw ModelNotReady()
    }

    private suspend fun process(recognizer: TextRecognizer, bmp: Bitmap) = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /** Líneas y palabras de un resultado, llevadas a coordenadas de la imagen entera. */
    private fun collect(
        text: com.google.mlkit.vision.text.Text, dx: Int, dy: Int, scale: Float,
        lines: MutableList<TextItem>, words: MutableList<TextItem>,
    ) {
        fun map(t: String, b: android.graphics.Rect) = TextItem(
            t, dx + (b.left / scale).toInt(), dy + (b.top / scale).toInt(),
            dx + (b.right / scale).toInt(), dy + (b.bottom / scale).toInt(),
        )
        for (block in text.textBlocks) for (line in block.lines) {
            line.boundingBox?.let { lines += map(line.text, it) }
            for (el in line.elements) el.boundingBox?.let { words += map(el.text, it) }
        }
    }

    suspend fun recognizeBitmap(context: Context, bitmap: Bitmap): Result {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            ensureModel(context, recognizer)
            val full = process(recognizer, bitmap)
            val lines = ArrayList<TextItem>()
            val words = ArrayList<TextItem>()
            collect(full, 0, 0, 1f, lines, words)

            // Los números pequeños (p. ej. los días de un calendario en una captura del móvil) se
            // le escapan al lector en la imagen entera: se vuelve a leer por trozos ampliados.
            val w = bitmap.width
            val h = bitmap.height
            val tile = (minOf(w, h) / 2).coerceAtLeast(300)
            val cols = (w + tile - 1) / tile
            val rows = (h + tile - 1) / tile
            val tw = w / cols
            val th = h / rows
            val pad = (tile * 0.12f).toInt()
            val tLines = ArrayList<TextItem>()
            val tWords = ArrayList<TextItem>()
            if (cols * rows in 2..24) for (r in 0 until rows) for (c in 0 until cols) {
                val x0 = (c * tw - pad).coerceAtLeast(0)
                val y0 = (r * th - pad).coerceAtLeast(0)
                val x1 = ((c + 1) * tw + pad).coerceAtMost(w)
                val y1 = ((r + 1) * th + pad).coerceAtMost(h)
                val scale = (1600f / (x1 - x0)).coerceIn(1f, 3f)
                val piece = withContext(Dispatchers.Default) {
                    val m = Matrix().apply { setScale(scale, scale) }
                    Bitmap.createBitmap(bitmap, x0, y0, x1 - x0, y1 - y0, m, true)
                }
                try {
                    collect(process(recognizer, piece), x0, y0, scale, tLines, tWords)
                } finally {
                    if (piece !== bitmap) piece.recycle()
                }
            }
            val allLines = com.asalariado.core.TextMerge.merge(lines, tLines)
            val allWords = com.asalariado.core.TextMerge.merge(words, tWords)
            val pixels = withContext(Dispatchers.Default) { BitmapPixels(bitmap) }
            return Result(allLines, allWords, allLines.joinToString("\n") { it.text }, pixels)
        } finally {
            recognizer.close()
        }
    }
}
