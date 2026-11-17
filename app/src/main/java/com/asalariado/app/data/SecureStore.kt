package com.asalariado.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.io.File

/**
 * Datos sensibles (la contraseña de las nóminas) guardados cifrados (AES-256, clave en el Android
 * Keystore). Si el almacén cifrado no se puede abrir, se borra y se crea de nuevo; nunca se guarda en claro.
 */
class SecureStore(private val context: Context) {
    private val prefs: SharedPreferences? = open() ?: run { reset(); open() }

    /** Si no hay almacén cifrado, la contraseña sólo se recuerda mientras la app está abierta. */
    private var memory: String? = null

    init {
        // Las versiones anteriores podían dejar una copia sin cifrar: se borra.
        context.deleteSharedPreferences("secure_fallback")
    }

    private fun open(): SharedPreferences? = runCatching {
        EncryptedSharedPreferences.create(
            "secure",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    private fun reset() {
        context.deleteSharedPreferences("secure")
        File(context.filesDir.parentFile, "shared_prefs/secure.xml").delete()
    }

    var payslipPassword: String?
        get() = prefs?.getString("payslip_password", null)?.takeIf { it.isNotEmpty() } ?: memory
        set(v) {
            if (prefs != null) prefs.edit().putString("payslip_password", v ?: "").apply() else memory = v
        }
}
