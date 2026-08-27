package com.hermes.companion.domain

/**
 * Whether a capability can actually be served right now. Derived on demand from
 * live permission/role/service state — never cached optimistically. The Node
 * coverage matrix renders exactly this (`plan/03-android/full-node-mode.md`).
 */
enum class CapabilityHealth { Working, PermissionMissing, OsLimited, Unavailable }
