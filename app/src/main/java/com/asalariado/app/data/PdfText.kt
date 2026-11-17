package com.asalariado.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.asalariado.core.TextItem
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Lee el texto de un PDF con su posición (sin OCR, así los importes salen exactos), abriéndolo con
 * la contraseña si la tiene. Si el PDF es una imagen escaneada, devuelve las páginas dibujadas.
 */
object PdfText {
    class WrongPassword : Exception("contraseña incorrecta")

    class Doc(
        /** Texto de cada página con su caja (puntos del PDF). */
        val pages: List<List<TextItem>>,
        /** Páginas dibujadas si el PDF no tiene texto (escaneado). */
        val images: List<Bitmap>,
        /** Copia del PDF sin contraseña, para guardarla en la app. */
        val unlocked: ByteArray,
    )

    fun isPdf(context: Context, uri: Uri): Boolean =
        context.contentResolver.getType(uri) == "application/pdf" || uri.toString().lowercase().endsWith(".pdf")

    fun read(context: Context, uri: Uri, password: String?): Doc {
        PDFBoxResourceLoader.init(context.applicationContext)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("no se pudo abrir el archivo")
        val doc = try {
            PDDocument.load(bytes, password ?: "")
        } catch (e: InvalidPasswordException) {
            throw WrongPassword()
        }
        doc.use {
            val pages = (1..doc.numberOfPages).map { p -> pageText(doc, p) }
            val hasText = pages.any { it.size > 5 }
            val images = if (hasText) emptyList() else {
                val renderer = PDFRenderer(doc)
                (0 until minOf(doc.numberOfPages, 4)).map { renderer.renderImageWithDPI(it, 200f, com.tom_roush.pdfbox.rendering.ImageType.RGB) }
            }
            doc.isAllSecurityToBeRemoved = true
            val out = ByteArrayOutputStream()
            doc.save(out)
            return Doc(pages, images, out.toByteArray())
        }
    }

    /**
     * Para calendarios en PDF: cada página dibujada (para leer los colores) junto con su texto en las
     * mismas coordenadas, sacado del propio PDF (exacto, sin reconocer la imagen). Si el PDF no tiene
     * texto (es una foto escaneada), el texto viene vacío y hay que usar OCR.
     */
    fun pagesWithText(context: Context, uri: Uri, password: String?): List<Pair<Bitmap, List<TextItem>>> {
        PDFBoxResourceLoader.init(context.applicationContext)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("no se pudo abrir el archivo")
        val doc = try { PDDocument.load(bytes, password ?: "") } catch (e: InvalidPasswordException) { throw WrongPassword() }
        doc.use {
            val r = PDFRenderer(doc)
            return (0 until minOf(doc.numberOfPages, 4)).map { i ->
                val width = doc.getPage(i).mediaBox.width
                val scale = 2000f / width
                val bmp = r.renderImage(i, scale, com.tom_roush.pdfbox.rendering.ImageType.RGB)
                bmp to pageText(doc, i + 1, scale)
            }
        }
    }

    /** Dibuja las páginas (máx. 4) de un PDF, con contraseña si la tiene, para leerlas con OCR. */
    fun render(context: Context, uri: Uri, password: String?): List<Bitmap> {
        PDFBoxResourceLoader.init(context.applicationContext)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("no se pudo abrir el archivo")
        val doc = try { PDDocument.load(bytes, password ?: "") } catch (e: InvalidPasswordException) { throw WrongPassword() }
        doc.use {
            val r = PDFRenderer(doc)
            return (0 until minOf(doc.numberOfPages, 4)).map { r.renderImageWithDPI(it, 200f, com.tom_roush.pdfbox.rendering.ImageType.RGB) }
        }
    }

    /**
     * Agrupa las letras de cada línea en trozos separados por huecos grandes: así «Plus disponibilidad
     * sábados y FR» queda junto y cada importe de la columna queda aparte, como hace el OCR.
     */
    private fun pageText(doc: PDDocument, page: Int, scale: Float = 3f): List<TextItem> {
        val items = ArrayList<TextItem>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String?, positions: MutableList<TextPosition>?) {
                val list = positions ?: return
                val cur = StringBuilder()
                var l = 0f; var t = 0f; var r = 0f; var b = 0f
                var lastRight = -1f
                fun flush() {
                    val s = cur.toString().trim()
                    if (s.isNotEmpty()) items += TextItem(s, (l * scale).toInt(), (t * scale).toInt(), (r * scale).toInt(), (b * scale).toInt())
                    cur.setLength(0)
                    lastRight = -1f
                }
                for (tp in list) {
                    val ch = tp.unicode ?: continue
                    val x = tp.xDirAdj
                    val w = tp.widthDirAdj
                    val hgt = max(tp.heightDir, 1f)
                    val y = tp.yDirAdj
                    if (lastRight >= 0 && x - lastRight > hgt * 1.1f) flush()
                    if (ch.isBlank()) {
                        if (cur.isNotEmpty()) cur.append(' ')
                        continue
                    }
                    if (cur.isEmpty()) { l = x; t = y - hgt; r = x + w; b = y } else {
                        r = max(r, x + w); t = min(t, y - hgt); b = max(b, y)
                    }
                    cur.append(ch)
                    lastRight = x + w
                }
                flush()
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = page
        stripper.endPage = page
        stripper.getText(doc)
        return items
    }
}
