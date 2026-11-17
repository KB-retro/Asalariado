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
    suspend fun recognize(context: Context, uri: Uri): List<Result> {
        val bitmaps = withContext(Dispatchers.IO) {
            if (isPdf(context, uri)) renderPdf(context, uri) else listOf(decodeImage(context, uri))
        }
        return bitmaps.map { recognizeBitmap(it) }
    }

    private fun isPdf(context: Context, uri: Uri): Boolean =
        context.contentResolver.getType(uri) == "application/pdf" || uri.toString().lowercase().endsWith(".pdf")

    private fun decodeImage(context: Context, uri: Uri): Bitmap =
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: error("formato de imagen no soportado")

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

    suspend fun recognizeBitmap(bitmap: Bitmap): Result {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = suspendCancellableCoroutine { cont ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            val lines = ArrayList<TextItem>()
            val words = ArrayList<TextItem>()
            for (block in text.textBlocks) for (line in block.lines) {
                line.boundingBox?.let { b -> lines += TextItem(line.text, b.left, b.top, b.right, b.bottom) }
                for (el in line.elements) {
                    el.boundingBox?.let { b -> words += TextItem(el.text, b.left, b.top, b.right, b.bottom) }
                }
            }
            val pixels = withContext(Dispatchers.Default) { BitmapPixels(bitmap) }
            return Result(lines, words, text.text, pixels)
        } finally {
            recognizer.close()
        }
    }
}
