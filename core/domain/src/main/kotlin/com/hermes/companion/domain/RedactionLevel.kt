package com.hermes.companion.domain

/**
 * How much of a node event leaves the device (`plan/07-privacy/privacy-model.md`).
 * OTP / banking / health default to [MetadataOnly] regardless of per-source rule,
 * and a per-source rule can never raise them.
 */
enum class RedactionLevel { Full, Redacted, MetadataOnly }
