package com.asalariado.app.data

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKeys
import java.io.File

/**
 * Los PDF de las nóminas se guardan cifrados (AES-256, clave en el Android Keystore del móvil).
 * Para verlos se descifran en una copia temporal que se borra la próxima vez que se abre la app.
 */
object SecureFiles {
    private fun dir(ctx: Context) = File(ctx.filesDir, "nominas").apply { mkdirs() }
    private fun viewDir(ctx: Context) = File(ctx.cacheDir, "ver").apply { mkdirs() }
    private fun name(year: Int, month: Int, extra: Boolean) = "%04d-%02d%s.pdf".format(year, month, if (extra) "-extra" else "")
    private fun key() = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private fun encrypted(ctx: Context, file: File) =
        EncryptedFile.Builder(file, ctx, key(), EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()

    fun payslipFile(ctx: Context, year: Int, month: Int, extra: Boolean) = File(dir(ctx), name(year, month, extra) + ".enc")

    fun exists(ctx: Context, year: Int, month: Int, extra: Boolean) = payslipFile(ctx, year, month, extra).exists()

    fun savePayslip(ctx: Context, year: Int, month: Int, extra: Boolean, bytes: ByteArray) {
        val f = payslipFile(ctx, year, month, extra)
        if (f.exists()) f.delete() // EncryptedFile no sobrescribe
        encrypted(ctx, f).openFileOutput().use { it.write(bytes) }
    }

    /** Descifra el PDF en una copia temporal para abrirlo con el visor. */
    fun decryptForViewing(ctx: Context, year: Int, month: Int, extra: Boolean): File? {
        val f = payslipFile(ctx, year, month, extra)
        if (!f.exists()) return null
        val out = File(viewDir(ctx), name(year, month, extra))
        encrypted(ctx, f).openFileInput().use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    /** Borra las copias temporales y cifra los PDF que guardaron versiones anteriores sin cifrar. */
    fun housekeeping(ctx: Context) {
        viewDir(ctx).listFiles()?.forEach { it.delete() }
        dir(ctx).listFiles { f -> f.name.endsWith(".pdf") }?.forEach { plain ->
            runCatching {
                val target = File(plain.parentFile, plain.name + ".enc")
                if (target.exists()) target.delete()
                encrypted(ctx, target).openFileOutput().use { it.write(plain.readBytes()) }
                plain.delete()
            }
        }
    }
}
