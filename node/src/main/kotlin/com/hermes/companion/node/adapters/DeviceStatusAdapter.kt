package com.hermes.companion.node.adapters

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.hermes.companion.domain.AndroidRequirement
import com.hermes.companion.domain.CapabilityHealth
import com.hermes.companion.domain.NodeCapability
import com.hermes.companion.domain.NodeCommand
import com.hermes.companion.domain.Receipt
import com.hermes.companion.domain.ReceiptStatus
import com.hermes.companion.node.CapabilityAdapter

/** Battery, network and charging state. No permission required. */
class DeviceStatusAdapter(private val context: Context) : CapabilityAdapter {
    override val capability = NodeCapability.DeviceStatus
    override val requires: Set<AndroidRequirement> = emptySet()

    override fun health(): CapabilityHealth = CapabilityHealth.Working

    override suspend fun invoke(command: NodeCommand): Receipt {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val charging = bm?.isCharging ?: false
        val net = networkKind()
        val payload = """{"battery":$level,"charging":$charging,"network":"$net"}"""
        return Receipt(
            requestId = command.requestId,
            capability = capability.family,
            status = ReceiptStatus.Completed,
            detail = "battery $level% · $net",
            payload = payload,
            at = System.currentTimeMillis(),
        )
    }

    fun snapshot(): DeviceStatus {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return DeviceStatus(
            batteryPercent = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1,
            charging = bm?.isCharging ?: false,
            network = networkKind(),
        )
    }

    private fun networkKind(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "unknown"
        val caps = try { cm.getNetworkCapabilities(cm.activeNetwork) } catch (e: SecurityException) { null } ?: return "offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
    }

    data class DeviceStatus(val batteryPercent: Int, val charging: Boolean, val network: String)
}
