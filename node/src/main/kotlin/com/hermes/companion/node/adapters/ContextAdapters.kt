package com.hermes.companion.node.adapters

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.Context
import android.database.Cursor
import android.location.LocationManager
import android.provider.CallLog
import android.provider.ContactsContract
import com.hermes.companion.domain.AndroidRequirement
import com.hermes.companion.domain.CapabilityHealth
import com.hermes.companion.domain.NodeCapability
import com.hermes.companion.domain.NodeCommand
import com.hermes.companion.domain.Receipt
import com.hermes.companion.domain.ReceiptStatus
import com.hermes.companion.domain.RequirementKind
import com.hermes.companion.node.CapabilityAdapter
import com.hermes.companion.node.hasPermission
import com.hermes.companion.node.hasUsageStatsAccess
import org.json.JSONArray
import org.json.JSONObject

private fun done(cmd: NodeCommand, detail: String, payload: String) =
    Receipt(cmd.requestId, cmd.capability, ReceiptStatus.Completed, detail, payload, System.currentTimeMillis())

private fun refused(cmd: NodeCommand, detail: String) =
    Receipt(cmd.requestId, cmd.capability, ReceiptStatus.Refused, detail, "{}", System.currentTimeMillis())

/** Foreground app-usage over the last 24h (special "usage access" grant). */
class AppUsageAdapter(private val context: Context) : CapabilityAdapter {
    override val capability = NodeCapability.AppUsage
    override val requires = setOf(AndroidRequirement(RequirementKind.SystemSetting, "usage access"))
    override fun health() = if (context.hasUsageStatsAccess()) CapabilityHealth.Working else CapabilityHealth.PermissionMissing
    override suspend fun invoke(command: NodeCommand): Receipt {
        if (!context.hasUsageStatsAccess()) return refused(command, "usage access not granted")
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return refused(command, "usage stats unavailable")
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 24 * 3600_000L, end)
            .orEmpty().filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }.take(10)
        val arr = JSONArray()
        stats.forEach { arr.put(JSONObject().put("package", it.packageName).put("foregroundMs", it.totalTimeInForeground)) }
        return done(command, "${stats.size} apps", JSONObject().put("apps", arr).toString())
    }
}

/** Last known location (fine or coarse). */
class LocationReadAdapter(private val context: Context) : CapabilityAdapter {
    override val capability = NodeCapability.LocationRead
    override val requires = setOf(AndroidRequirement(RequirementKind.RuntimePermission, Manifest.permission.ACCESS_COARSE_LOCATION))
    private fun granted() = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
        context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    override fun health() = if (granted()) CapabilityHealth.Working else CapabilityHealth.PermissionMissing
    @Suppress("MissingPermission")
    override suspend fun invoke(command: NodeCommand): Receipt {
        if (!granted()) return refused(command, "location permission not granted")
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return refused(command, "location unavailable")
        val loc = runCatching {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull() ?: return done(command, "no fix", "{}")
        val payload = JSONObject().put("lat", loc.latitude).put("lon", loc.longitude).put("accuracy", loc.accuracy)
        return done(command, "fix ±${loc.accuracy}m", payload.toString())
    }
}

/** Contact lookup by number or name. */
class ContactsLookupAdapter(private val context: Context) : CapabilityAdapter {
    override val capability = NodeCapability.ContactsLookup
    override val requires = setOf(AndroidRequirement(RequirementKind.RuntimePermission, Manifest.permission.READ_CONTACTS))
    override fun health() = if (context.hasPermission(Manifest.permission.READ_CONTACTS)) CapabilityHealth.Working else CapabilityHealth.PermissionMissing
    override suspend fun invoke(command: NodeCommand): Receipt {
        if (!context.hasPermission(Manifest.permission.READ_CONTACTS)) return refused(command, "contacts permission not granted")
        val q = JSONObject(runCatching { command.params }.getOrDefault("{}")).optString("query")
        if (q.isBlank()) return refused(command, "query required")
        val uri = android.net.Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, android.net.Uri.encode(q))
        val results = JSONArray()
        context.contentResolver.query(uri, arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        ), null, null, null)?.use { c: Cursor ->
            while (c.moveToNext()) {
                results.put(JSONObject().put("name", c.getString(0)).put("number", c.getString(1)))
            }
        }
        return done(command, "${results.length()} match", JSONObject().put("contacts", results).toString())
    }
}

/** Recent call-log entries (missed/received/dialed), surviving process death. */
class CallsLogAdapter(private val context: Context) : CapabilityAdapter {
    override val capability = NodeCapability.CallsLog
    override val requires = setOf(AndroidRequirement(RequirementKind.RuntimePermission, Manifest.permission.READ_CALL_LOG))
    override fun health() = if (context.hasPermission(Manifest.permission.READ_CALL_LOG)) CapabilityHealth.Working else CapabilityHealth.PermissionMissing
    override suspend fun invoke(command: NodeCommand): Receipt {
        if (!context.hasPermission(Manifest.permission.READ_CALL_LOG)) return refused(command, "call-log permission not granted")
        val arr = JSONArray()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
            null, null, "${CallLog.Calls.DATE} DESC LIMIT 20",
        )?.use { c ->
            while (c.moveToNext()) {
                arr.put(
                    JSONObject()
                        .put("number", c.getString(0))
                        .put("type", c.getInt(1))
                        .put("date", c.getLong(2))
                        .put("durationSec", c.getLong(3)),
                )
            }
        }
        return done(command, "${arr.length()} calls", JSONObject().put("calls", arr).toString())
    }
}
