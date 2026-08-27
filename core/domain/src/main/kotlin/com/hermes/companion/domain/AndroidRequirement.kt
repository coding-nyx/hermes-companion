package com.hermes.companion.domain

/**
 * What a capability needs from the OS before it can work. The concrete Android
 * permission/role strings live in `:node:adapters`; domain only names the kind
 * so the permission-mapping table, `health()`, and the setup checklist agree.
 */
enum class RequirementKind {
    RuntimePermission,
    AppRole,
    NotificationListener,
    AccessibilityService,
    MediaProjectionConsent,
    SystemSetting,
    ElevatedTier,
}

data class AndroidRequirement(
    val kind: RequirementKind,
    /** e.g. "android.permission.READ_CONTACTS", "ROLE_DIALER", "shizuku". */
    val detail: String,
)
