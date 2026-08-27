package com.hermes.companion.common

/**
 * A biometric/credential gate applied at the REPOSITORY boundary, so a screen
 * cannot forget one (`plan/10-architecture/security.md`). [require] suspends
 * until the user authenticates and returns false on cancel/error.
 */
interface BiometricGate {
    suspend fun require(gate: Gate): Boolean

    enum class Gate(val title: String) {
        APP_LAUNCH("Unlock Hermes Companion"),
        APPROVAL_DECISION("Confirm approval decision"),
        CAPABILITY_CHANGE("Confirm capability change"),
        REVEAL_SECRET("Reveal sensitive detail"),
    }
}

/** No-op gate for tests and for builds/devices without any enrolled authenticator. */
object AllowAllGate : BiometricGate {
    override suspend fun require(gate: BiometricGate.Gate): Boolean = true
}
