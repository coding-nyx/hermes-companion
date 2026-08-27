package com.hermes.companion.node.elevated

import android.app.Activity
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.util.concurrent.ConcurrentHashMap

/** Process-wide Shizuku binder + permission lifecycle (mirrors the a11y singleton). */
object ShizukuGateway {

    const val PERMISSION_REQUEST_CODE = 0x5A17

    @Volatile private var bound = false
    private val onBinderReceived = Shizuku.OnBinderReceivedListener { bound = true }
    private val onBinderDead = Shizuku.OnBinderDeadListener { bound = false }
    private val pending = ConcurrentHashMap<Int, (Boolean) -> Unit>()
    private val onPermissionResult = Shizuku.OnRequestPermissionResultListener { code, grant ->
        pending.remove(code)?.invoke(grant == PackageManager.PERMISSION_GRANTED)
    }

    /** Call once from CompanionApp.onCreate (main thread). Idempotent, safe if Shizuku absent. */
    fun install() {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
            Shizuku.addBinderDeadListener(onBinderDead)
            Shizuku.addRequestPermissionResultListener(onPermissionResult)
        }
    }

    fun isBound(): Boolean = bound && runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    fun isPreV11(): Boolean = isBound() && runCatching { Shizuku.isPreV11() }.getOrDefault(false)
    fun isGranted(): Boolean =
        isBound() && !isPreV11() &&
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    fun requestPermission(activity: Activity, onResult: (Boolean) -> Unit) {
        if (!isBound()) { onResult(false); return }
        if (isGranted()) { onResult(true); return }
        pending[PERMISSION_REQUEST_CODE] = onResult
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }.onFailure { onResult(false) }
    }

    /** Elevated process via the hidden Shizuku.newProcess (reflection). */
    fun newProcess(argv: Array<String>, env: Array<String>? = null, dir: String? = null): Process {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        ).apply { isAccessible = true }
        return m.invoke(null, argv, env, dir) as Process
    }
}
