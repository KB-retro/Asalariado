package com.asalariado.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    suspend fun recognize(context: Context, uri: Uri): Result {
        val bitmap = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
        } ?: error("formato de imagen no soportado")

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
