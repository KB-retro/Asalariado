package com.asalariado.app.data

import android.content.Context
import android.net.Uri
import com.asalariado.core.TextItem
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reconoce el texto de una imagen con ML Kit (en el dispositivo, sin conexión). */
object Ocr {
    data class Result(val items: List<TextItem>, val fullText: String)

    suspend fun recognize(context: Context, uri: Uri): Result {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            val items = text.textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val b = line.boundingBox ?: return@mapNotNull null
                    TextItem(line.text, b.left, b.top, b.right, b.bottom)
                }
            }
            return Result(items, text.text)
        } finally {
            recognizer.close()
        }
    }
}
