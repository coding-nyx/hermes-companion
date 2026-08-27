package com.hermes.companion.node.elevated

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class ShellResult(val code: Int, val out: List<String>, val err: List<String>) {
    val ok: Boolean get() = code == 0
}

enum class ElevatedRoute { Shizuku, Root, None }

/** Runs allowlisted commands on the best available elevated route (Shizuku preferred). */
object ElevatedShell {

    fun route(): ElevatedRoute = when {
        ShizukuGateway.isGranted() -> ElevatedRoute.Shizuku
        RootDetector.isRootGranted() -> ElevatedRoute.Root
        else -> ElevatedRoute.None
    }

    suspend fun run(argv: List<String>, timeoutMs: Long = 10_000): ShellResult = withContext(Dispatchers.IO) {
        when (route()) {
            ElevatedRoute.Shizuku -> runShizuku(argv, timeoutMs)
            ElevatedRoute.Root -> runRoot(argv)
            ElevatedRoute.None -> ShellResult(-1, emptyList(), listOf("no elevated route"))
        }
    }

    private fun runShizuku(argv: List<String>, timeoutMs: Long): ShellResult = runCatching {
        val p = ShizukuGateway.newProcess(argv.toTypedArray())
        val out = p.inputStream.bufferedReader().readLines()
        val err = p.errorStream.bufferedReader().readLines()
        val finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) { p.destroy(); ShellResult(-2, out, listOf("timeout")) }
        else ShellResult(p.exitValue(), out, err)
    }.getOrElse { ShellResult(-3, emptyList(), listOf(it.message ?: "shizuku exec failed")) }

    private fun runRoot(argv: List<String>): ShellResult = runCatching {
        val res = Shell.cmd(argv.joinToString(" ")).exec()
        ShellResult(res.code, res.out, res.err)
    }.getOrElse { ShellResult(-3, emptyList(), listOf(it.message ?: "su exec failed")) }
}
