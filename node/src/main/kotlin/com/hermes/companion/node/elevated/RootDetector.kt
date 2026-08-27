package com.hermes.companion.node.elevated

import com.topjohnwu.superuser.Shell
import java.io.File

/** Rooted-device detection. libsu's cached grant flag is authoritative; a binary probe is the fallback. */
object RootDetector {
    private val SU_PATHS = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/system/sbin/su", "/vendor/bin/su", "/data/local/xbin/su", "/data/local/bin/su",
    )

    fun isRootGranted(): Boolean = runCatching { Shell.isAppGrantedRoot() == true }.getOrDefault(false)
    fun hasSuBinary(): Boolean = SU_PATHS.any { runCatching { File(it).exists() }.getOrDefault(false) }
    /** Blocking; call from Dispatchers.IO. May trigger the su prompt. */
    fun probeRoot(): Boolean = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
}
