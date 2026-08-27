package com.hermes.companion.common

/**
 * Short, operator-readable reason for a failure. Never a stack trace, never
 * null: this text is rendered, so it has to say something.
 */
fun Throwable.reason(): String =
    message?.takeIf { it.isNotBlank() } ?: this::class.simpleName ?: "unknown error"
