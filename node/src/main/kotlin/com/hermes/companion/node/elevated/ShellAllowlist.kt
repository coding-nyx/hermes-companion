package com.hermes.companion.node.elevated

/**
 * The elevated tier only ever runs a fixed, purpose-built set of binaries with no
 * shell metacharacters. Everything else is refused before it reaches a shell.
 */
object ShellAllowlist {
    val ALLOWED = setOf("pm", "appops", "cmd", "settings", "dumpsys", "input", "am", "wm", "svc", "getprop", "id")
    val DENIED = setOf("su", "sh", "rm", "mount", "dd", "reboot", "magisk")
    private val META = Regex("""[;&|<>`${'$'}(){}\n\r\\]""")

    fun check(argv: List<String>): String? = when {
        argv.isEmpty() -> "empty command"
        argv[0] in DENIED -> "denied binary: ${argv[0]}"
        argv[0] !in ALLOWED -> "not allowlisted: ${argv[0]}"
        argv.any { META.containsMatchIn(it) } -> "shell metacharacters not allowed"
        else -> null
    }
}
