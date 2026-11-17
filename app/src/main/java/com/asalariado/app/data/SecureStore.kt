package com.asalariado.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/** Datos sensibles (la contraseña de las nóminas) guardados cifrados en el móvil. */
class SecureStore(context: Context) {
    private val prefs: SharedPreferences = runCatching {
        EncryptedSharedPreferences.create(
            "secure",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { context.getSharedPreferences("secure_fallback", Context.MODE_PRIVATE) }

    var payslipPassword: String?
        get() = prefs.getString("payslip_password", null)?.takeIf { it.isNotEmpty() }
        set(v) { prefs.edit().putString("payslip_password", v ?: "").apply() }
}
