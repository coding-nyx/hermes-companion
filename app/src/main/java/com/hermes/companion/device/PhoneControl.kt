package com.hermes.companion.device

import android.app.NotificationManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import com.hermes.companion.node.NodePrefs
import com.hermes.companion.node.UsageAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PhoneSnapshot(
    val name: String = Build.MODEL,
    val model: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val battery: Int = 0,
    val charging: Boolean = false,
    val brightness: Int = 50,
    val volume: Int = 40,
    val ringer: Int = 70,
    val wifi: Boolean = true,
    val bluetooth: Boolean = true,
    val flashlight: Boolean = false,
    val dnd: Boolean = false,
    val airplane: Boolean = false,
    val orientationLock: Boolean = false,
    val nightLight: Boolean = false,
    val locationSharing: Boolean = true,
    val currentApp: String = "Hermes Companion",
    val clipboard: String = "",
    val mediaTitle: String = "Night Drive",
    val mediaArtist: String = "Kiasmos",
    val mediaPlaying: Boolean = false,
)

object PhoneControl {
    private val _state = MutableStateFlow(PhoneSnapshot())
    val state: StateFlow<PhoneSnapshot> = _state.asStateFlow()

    @Volatile
    private var torchId: String? = null

    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val brightness = try {
            ((Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f) * 100).toInt()
        } catch (_: Exception) {
            _state.value.brightness
        }
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val maxRing = am.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
        val current = UsageAccess.current(app)
        val airplane = Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        val bt = try {
            android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        } catch (_: Exception) {
            _state.value.bluetooth
        }
        _state.update {
            it.copy(
                name = NodePrefs.deviceName(app).ifBlank { Build.MODEL },
                battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100),
                charging = bm.isCharging,
                brightness = brightness.coerceIn(0, 100),
                volume = ((am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f) / maxVol).toInt(),
                ringer = ((am.getStreamVolume(AudioManager.STREAM_RING) * 100f) / maxRing).toInt(),
                wifi = wifi?.isWifiEnabled == true,
                bluetooth = bt,
                dnd = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE ||
                    nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS,
                airplane = airplane,
                currentApp = current?.pkg?.substringAfterLast('.') ?: it.currentApp,
            )
        }
    }

    fun patch(ctx: Context, transform: (PhoneSnapshot) -> PhoneSnapshot) {
        val next = transform(_state.value)
        apply(ctx, next)
        _state.value = next
    }

    fun setName(ctx: Context, name: String) {
        NodePrefs.setDeviceName(ctx, name)
        _state.update { it.copy(name = name) }
    }

    private fun apply(ctx: Context, next: PhoneSnapshot) {
        val app = ctx.applicationContext
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching {
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (next.volume / 100f * maxVol).toInt(), 0)
        }
        runCatching {
            val maxRing = am.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
            am.setStreamVolume(AudioManager.STREAM_RING, (next.ringer / 100f * maxRing).toInt(), 0)
        }
        if (nm.isNotificationPolicyAccessGranted) {
            runCatching {
                nm.setInterruptionFilter(
                    if (next.dnd) NotificationManager.INTERRUPTION_FILTER_NONE
                    else NotificationManager.INTERRUPTION_FILTER_ALL,
                )
            }
        }
        if (Settings.System.canWrite(app)) {
            runCatching {
                Settings.System.putInt(
                    app.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    (next.brightness / 100f * 255).toInt().coerceIn(1, 255),
                )
            }
        }
        runCatching { setTorch(app, next.flashlight) }
        runCatching {
            val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifi.isWifiEnabled = next.wifi
        }
    }

    private fun setTorch(ctx: Context, on: Boolean) {
        val cam = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = torchId ?: cam.cameraIdList.firstOrNull { cid ->
            cam.getCameraCharacteristics(cid).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }?.also { torchId = it } ?: return
        cam.setTorchMode(id, on)
    }
}
