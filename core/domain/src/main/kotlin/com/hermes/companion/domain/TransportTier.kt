package com.hermes.companion.domain

/**
 * The privilege ceiling a connection may ever be granted, from its transport
 * (`plan/04-connection/gateway-registry.md`). Recomputed on every connect.
 *
 * - [Full]: TLS, or cleartext to loopback / a `.local` host / an emulator —
 *   eligible for operator access and a node session.
 * - [Limited]: cleartext to anything else — chat and read-only inspection only,
 *   never a node session, grant, or write-scoped token.
 */
enum class TransportTier { Full, Limited }
