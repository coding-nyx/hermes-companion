package com.hermes.companion.domain

/**
 * An exclusive-capability lease. The lease table's primary key IS the capability,
 * so mutual exclusion is a uniqueness constraint, not a lock
 * (`plan/10-architecture/capabilities.md`).
 */
data class Lease(
    val capability: String,
    val gatewayId: String,
    val profileId: String,
    val requestId: String,
    val acquiredAt: Long,
    val expiresAt: Long,
)

sealed interface LeaseResult {
    data class Acquired(val lease: Lease) : LeaseResult
    /** Fails closed with a receipt naming the current holder and remaining time. */
    data class Held(val by: Lease, val until: Long) : LeaseResult
}
