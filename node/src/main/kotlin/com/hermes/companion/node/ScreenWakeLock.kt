package com.hermes.companion.node

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/**
 * T6: keep-screen-on wake lock while a node task is active.
 *
 * Acquired when the node enters an active session/run (i.e. when the user
 * has handed a task to Hermes and Hermes is controlling the device), and
 * released when the task ends. A max-duration cap (default 10 minutes)
 * prevents a forgotten release from draining the battery.
 *
 * The wake-lock is a thin wrapper around [PowerManager.WakeLock]. We use
 * [PowerManager.SCREEN_BRIGHT_WAKE_LOCK] (deprecated but still functional)
 * because the alternative [FLAG_KEEP_SCREEN_ON] is window-only and won't
 * survive a background service.
 *
 * [release] is idempotent and safe to call multiple times.
 */
class ScreenWakeLock(
    context: Context,
    private val maxDurationMs: Long = DEFAULT_MAX_DURATION_MS,
) {
    private val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val handler = Handler(Looper.getMainLooper())
    private val tag = "hermes-companion-node"
    private val label = "hermes-node-task"
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var held: Boolean = false
    private val watchdog = Runnable { release() }

    /**
     * Acquire the wake-lock. Idempotent: calling acquire() while already
     * held resets the watchdog timeout (so consecutive acquires don't
     * accidentally expire a long-running task).
     */
    fun acquire() {
        synchronized(this) {
            val existing = wakeLock
            if (existing != null && existing.isHeld) {
                // Refresh the watchdog timeout.
                handler.removeCallbacks(watchdog)
                handler.postDelayed(watchdog, maxDurationMs)
                return
            }
            val lock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "$tag:$label",
            )
            lock.setReferenceCounted(false)
            lock.acquire(maxDurationMs)
            wakeLock = lock
            held = true
            handler.postDelayed(watchdog, maxDurationMs)
            Log.i(tag, "wake-lock acquired (max ${maxDurationMs}ms)")
        }
    }

    /**
     * Release the wake-lock. Safe to call when not held - returns immediately.
     */
    fun release() {
        synchronized(this) {
            handler.removeCallbacks(watchdog)
            val lock = wakeLock ?: return
            if (!held) return
            try {
                if (lock.isHeld) lock.release()
            } catch (t: Throwable) {
                Log.w(tag, "release failed: $t")
            }
            held = false
            wakeLock = null
            Log.i(tag, "wake-lock released")
        }
    }

    fun isHeld(): Boolean = held

    companion object {
        /** 10 minutes - long enough for any realistic node task; short enough
         * that a forgotten release won't drain the battery overnight. */
        const val DEFAULT_MAX_DURATION_MS: Long = 10 * 60 * 1000
    }
}
