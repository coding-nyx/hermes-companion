package com.hermes.companion.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.hermes.companion.common.BiometricGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Biometric/credential gate via androidx.biometric. When no authenticator is
 * enrolled (or there is no foreground activity, e.g. the background node), it
 * allows — the gate protects the UI surface, it must not brick a device without
 * biometrics. A cryptographic (CryptoObject-bound) upgrade is a device-only
 * follow-up noted in the security spec.
 */
class AndroidBiometricGate @Inject constructor(
    private val current: CurrentActivityHolder,
) : BiometricGate {

    private val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    override suspend fun require(gate: BiometricGate.Gate): Boolean {
        val activity = current.fragmentActivity() ?: return true
        if (BiometricManager.from(activity).canAuthenticate(authenticators)
            != BiometricManager.BIOMETRIC_SUCCESS
        ) return true
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            if (cont.isActive) cont.resume(true)
                        }
                        override fun onAuthenticationError(code: Int, msg: CharSequence) {
                            if (cont.isActive) cont.resume(false)
                        }
                        override fun onAuthenticationFailed() { /* transient; prompt stays up */ }
                    },
                )
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(gate.title)
                    .setAllowedAuthenticators(authenticators)
                    .build()
                runCatching { prompt.authenticate(info) }.onFailure { if (cont.isActive) cont.resume(true) }
            }
        }
    }
}
