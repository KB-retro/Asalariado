package com.asalariado.app

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Bloqueo de la app con el bloqueo del propio móvil: huella, cara, PIN, patrón o contraseña.
 * No hay una clave aparte: se usa el sistema de seguridad de Android.
 */
object AppLock {
    /** Tiempo fuera de la app tras el que se vuelve a pedir el desbloqueo. */
    const val RELOCK_AFTER_MS = 60_000L

    private fun prefs(c: Context) = c.getSharedPreferences("lock", Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean("enabled", on).apply()
    fun hideInRecents(c: Context) = prefs(c).getBoolean("hide_recents", false)
    fun setHideInRecents(c: Context, on: Boolean) = prefs(c).edit().putBoolean("hide_recents", on).apply()

    // Antes de Android 11 no se puede pedir «huella fuerte o PIN» a la vez: se usa la débil.
    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** ¿El móvil tiene algún bloqueo (huella, PIN…) con el que desbloquear? */
    fun available(c: Context): Boolean =
        BiometricManager.from(c).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onFail: (String) -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear Asalariado")
                .setSubtitle("Usa tu huella o el PIN del móvil")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }
}
