package com.asalariado.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.asalariado.app.data.AppDatabase
import com.asalariado.app.data.ConditionsStore
import com.asalariado.app.data.toEntity
import com.asalariado.app.ui.AppFont
import com.asalariado.app.ui.Palette
import com.asalariado.app.ui.ThemeMode
import com.asalariado.app.ui.UiPrefs
import com.asalariado.app.ui.UiPrefsStore
import com.asalariado.app.ui.UiStyle
import com.asalariado.core.Conditions
import com.asalariado.core.MedicalVisit
import com.asalariado.core.Payslip
import com.asalariado.core.Rotation
import com.asalariado.core.TablaSalarial
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * Capturas de cada pantalla con datos de ejemplo, en claro y oscuro, para revisar el aspecto
 * en cada compilación. Se guardan en la carpeta de la app y el workflow las recoge.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun seed() = runBlocking {
        val today = LocalDate.now()
        val c = TablaSalarial.BELLOTA_2026.aplicar(Conditions(), 10).copy(
            irpfPercent = 17.0, geroaPercent = 2.3, employerMonthlyExtras = 1.33, activablePricePerHour = 12.2194,
            rotationAnchor = Rotation.mondayOf(today),
        )
        ConditionsStore(ctx).save(c)
        val dao = AppDatabase.get(ctx).dao()
        val from = today.withDayOfMonth(1).minusMonths(1)
        dao.upsertDays(Rotation.generate(from, today.plusMonths(2), c).map { it.toEntity() })
        dao.saveMedical(MedicalVisit(date = today.minusDays(3), start = LocalTime.of(9, 0), end = LocalTime.of(11, 0)).toEntity())
        val prev = today.minusMonths(1)
        dao.savePayslip(Payslip(prev.year, prev.monthValue, 2927.04, 2136.13, "Ejemplo", 17.0).toEntity())
    }

    private fun shoot(prefs: UiPrefs, suffix: String) {
        UiPrefsStore(ctx).save(prefs)
        val dir = File(ctx.getExternalFilesDir(null), "screens").apply { mkdirs() }
        fun save(name: String) {
            compose.waitForIdle()
            Thread.sleep(800)
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "$name-$suffix.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            // Primera captura nada más abrir (sirve también para ver qué hay si algo falla).
            save("inicio")
            val tabs = listOf("Calendario", "Médico", "Nómina", "Condiciones", "Ajustes")
            for (t in tabs) {
                val byDesc = compose.onAllNodes(hasContentDescription(t), useUnmergedTree = true)
                val target = if (byDesc.fetchSemanticsNodes().isNotEmpty()) byDesc.onFirst()
                else compose.onAllNodes(hasText(t), useUnmergedTree = true).onFirst()
                runCatching { target.performClick() }
                save(t.lowercase())
            }
        }
    }

    @Test
    fun light() = shoot(UiPrefs(palette = Palette.LAVANDA, mode = ThemeMode.CLARO), "claro")

    @Test
    fun dark() = shoot(UiPrefs(palette = Palette.LAVANDA, mode = ThemeMode.OSCURO), "oscuro")

    @Test
    fun pixelOcean() = shoot(
        UiPrefs(palette = Palette.OCEANO, mode = ThemeMode.CLARO, style = UiStyle.PIXEL, font = AppFont.PIXELIFY), "pixel",
    )
}
