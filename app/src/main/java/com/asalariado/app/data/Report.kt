package com.asalariado.app.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.asalariado.core.Conditions
import com.asalariado.core.YearSummary
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** Informe anual en PDF con lo esperado, lo cobrado y las diferencias, para enseñarlo si algo no cuadra. */
object Report {
    private val es = Locale.forLanguageTag("es-ES")
    private val euro = NumberFormat.getCurrencyInstance(es)
    private fun m(v: Double?) = v?.let { euro.format(it) } ?: "—"
    private fun h(v: Double) = String.format(es, "%.1f h", v)

    fun yearPdf(context: Context, y: YearSummary, c: Conditions): File {
        val doc = PdfDocument()
        val w = 595; val hgt = 842
        val title = Paint().apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val bold = Paint().apply { textSize = 10f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val text = Paint().apply { textSize = 10f; isAntiAlias = true }
        val red = Paint(text).apply { color = Color.rgb(198, 40, 40) }
        val green = Paint(text).apply { color = Color.rgb(46, 125, 50) }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.8f }

        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, hgt, 1).create())
        var cv = page.canvas
        var yy = 50f
        fun newPageIfNeeded(need: Float) {
            if (yy + need > hgt - 40) {
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(w, hgt, doc.pages.size + 1).create())
                cv = page.canvas
                yy = 50f
            }
        }
        cv.drawText("Informe de nómina ${y.year}", 40f, yy, title); yy += 18
        cv.drawText("Generado con Asalariado el ${LocalDate.now()}", 40f, yy, text); yy += 22

        cv.drawText("Condiciones", 40f, yy, bold); yy += 14
        val cond = listOfNotNull(
            c.escalon?.let { "Escalón $it" },
            "Jornal ${m(c.baseMonthly)} · prima ${m(c.bonusMonthly)} · ${c.payments} pagas",
            "Jornada anual ${h(c.annualHours)} · IRPF ${c.irpfPercent} % · SS ${c.ssPercent} %",
        )
        cond.forEach { cv.drawText(it, 40f, yy, text); yy += 13 }
        yy += 10

        // Tabla mensual.
        val cols = floatArrayOf(40f, 120f, 200f, 280f, 350f, 430f, 510f)
        val heads = listOf("Mes", "Bruto esp.", "Bruto nómina", "Dif.", "Neto esp.", "Neto nómina", "Dif.")
        heads.forEachIndexed { i, s -> cv.drawText(s, cols[i], yy, bold) }
        yy += 6; cv.drawLine(40f, yy, w - 40f, yy, line); yy += 12
        fun diffPaint(d: Double?) = when { d == null -> text; d < -1 -> red; d > 1 -> green; else -> text }
        for (ms in y.months) {
            newPageIfNeeded(30f)
            val name = Month.of(ms.month).getDisplayName(TextStyle.FULL_STANDALONE, es).replaceFirstChar { it.uppercase() }
            cv.drawText(name, cols[0], yy, text)
            cv.drawText(m(ms.expectedGross), cols[1], yy, text)
            cv.drawText(m(ms.payslip?.gross), cols[2], yy, text)
            cv.drawText(ms.diffGross?.let { m(it) } ?: "", cols[3], yy, diffPaint(ms.diffGross))
            cv.drawText(m(ms.expectedNet), cols[4], yy, text)
            cv.drawText(m(ms.payslip?.net), cols[5], yy, text)
            cv.drawText(ms.diffNet?.let { m(it) } ?: "", cols[6], yy, diffPaint(ms.diffNet))
            yy += 14
            ms.extra?.let { e ->
                cv.drawText("  Paga extra", cols[0], yy, text)
                cv.drawText(m(e.gross), cols[1], yy, text)
                cv.drawText(m(e.payslip?.gross), cols[2], yy, text)
                cv.drawText(e.diffGross?.let { m(it) } ?: "", cols[3], yy, diffPaint(e.diffGross))
                cv.drawText(m(e.net), cols[4], yy, text)
                cv.drawText(m(e.payslip?.net), cols[5], yy, text)
                cv.drawText(e.diffNet?.let { m(it) } ?: "", cols[6], yy, diffPaint(e.diffNet))
                yy += 14
            }
        }
        cv.drawLine(40f, yy - 8, w - 40f, yy - 8, line)
        yy += 6
        cv.drawText("Total", cols[0], yy, bold)
        cv.drawText(m(y.expectedGross), cols[1], yy, bold)
        cv.drawText(m(y.receivedGross), cols[2], yy, bold)
        cv.drawText(m(y.diffGross), cols[3], yy, diffPaint(y.diffGross))
        cv.drawText(m(y.expectedNet), cols[4], yy, bold)
        cv.drawText(m(y.receivedNet), cols[5], yy, bold)
        cv.drawText(m(y.diffNet), cols[6], yy, diffPaint(y.diffNet))
        yy += 26

        newPageIfNeeded(120f)
        cv.drawText("Jornada y permisos", 40f, yy, bold); yy += 14
        listOf(
            "Horas de convenio: ${h(y.annualHours)} · en calendario (trabajo + médico): ${h(y.countedHours)}",
            "Exceso de jornada: ${h(y.overtimeHours)} (${m(y.overtimeValue)})",
            "Vacaciones: ${y.vacationUsed} de ${y.vacationAllowed} días · libre disposición: ${y.personalUsed} de ${y.personalAllowed}",
            "Médico general/acompañante: ${h(y.medicalHoursUsed)} de ${h(y.medicalHoursAllowed)} · especialista: ${h(y.specialistHours)}",
            "Activables trabajados: ${y.activableUsed} (obligatorios: ${y.maxActivables})",
        ).forEach { cv.drawText(it, 40f, yy, text); yy += 13 }
        yy += 12
        cv.drawText("Diferencia negativa = cobrado de menos. Cálculo orientativo según las condiciones introducidas.", 40f, yy, text)

        doc.finishPage(page)
        val dir = File(context.cacheDir, "informes").apply { mkdirs() }
        val file = File(dir, "informe-nomina-${y.year}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
