package com.hermes.companion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hermes.companion.CompanionApp
import com.hermes.companion.MainActivity
import com.hermes.companion.R
import com.hermes.companion.data.repo.FleetStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Owns every gateway connection. Nothing about a connection lives in a
 * ViewModel any more: this service is why a run keeps streaming after you
 * leave Chat, and why the node will keep answering once it exists.
 *
 * See `plan/10-architecture/runtime.md`.
 */
class CompanionConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var supervision: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Post something immediately: the platform requires a notification
        // within a few seconds of the service starting.
        startForeground(NOTIFICATION_ID, notification(FleetStatus()))

        val data = CompanionApp.get().data
        supervision = data.supervisor.start(scope)
        scope.launch {
            data.supervisor.status.collectLatest { status ->
                notificationManager().notify(NOTIFICATION_ID, notification(status))
            }
        }
    }

    // Sticky: on a system-initiated restart the service rehydrates entirely
    // from the database and resumes. It never asks the UI for anything.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        supervision?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_connection),
            // Low: this is a persistent status, not an interruption. Agent
            // pings get their own channel when delivery lands.
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        notificationManager().createNotificationChannel(channel)
    }

    /** Reports counts, because "running" tells the operator nothing. */
    private fun notification(status: FleetStatus): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title(status))
            .setContentText(detail(status))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun title(status: FleetStatus): String = when {
        status.gateways == 0 -> getString(R.string.status_no_gateways)
        status.allDown -> getString(R.string.status_all_unreachable, status.gateways)
        status.unreachable > 0 -> getString(R.string.status_partial, status.live, status.gateways)
        else -> getString(R.string.status_connected, status.live)
    }

    private fun detail(status: FleetStatus): String =
        getString(R.string.status_detail, status.profiles, status.openRuns)

    companion object {
        private const val CHANNEL_ID = "companion.connection"
        private const val NOTIFICATION_ID = 1

        /**
         * Must be called while the app is visible: Android 12+ refuses a
         * background foreground-service start. Boot-time start arrives with the
         * node work, which needs a receiver anyway.
         */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CompanionConnectionService::class.java),
            )
        }
    }
}
