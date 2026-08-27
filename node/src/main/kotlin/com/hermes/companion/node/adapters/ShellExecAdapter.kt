package com.hermes.companion.node.adapters

import com.hermes.companion.domain.AndroidRequirement
import com.hermes.companion.domain.CapabilityHealth
import com.hermes.companion.domain.NodeCapability
import com.hermes.companion.domain.NodeCommand
import com.hermes.companion.domain.Receipt
import com.hermes.companion.domain.ReceiptStatus
import com.hermes.companion.domain.RequirementKind
import com.hermes.companion.node.CapabilityAdapter
import com.hermes.companion.node.elevated.ElevatedRoute
import com.hermes.companion.node.elevated.ElevatedShell
import com.hermes.companion.node.elevated.RootDetector
import com.hermes.companion.node.elevated.ShellAllowlist
import com.hermes.companion.node.elevated.ShizukuGateway
import org.json.JSONArray
import org.json.JSONObject

/**
 * The elevated tier (`system.shell`). Runs an allowlisted command via Shizuku
 * (ADB uid) when authorized, else via su/libsu on a rooted device. Never
 * available to an ordinary install — health is OsLimited with no elevated route.
 */
class ShellExecAdapter : CapabilityAdapter {
    override val capability = NodeCapability.ShellExec
    override val requires = setOf(AndroidRequirement(RequirementKind.ElevatedTier, "shizuku"))

    override fun health(): CapabilityHealth = when {
        ShizukuGateway.isGranted() || RootDetector.isRootGranted() -> CapabilityHealth.Working
        ShizukuGateway.isBound() || RootDetector.hasSuBinary() -> CapabilityHealth.PermissionMissing
        else -> CapabilityHealth.OsLimited
    }

    override suspend fun invoke(command: NodeCommand): Receipt {
        val argv = parseArgv(command.params)
            ?: return refuse(command, "params must be {\"argv\":[...]} or {\"cmd\":\"...\"}")
        ShellAllowlist.check(argv)?.let { return refuse(command, it) }
        if (ElevatedShell.route() == ElevatedRoute.None) {
            return refuse(command, "no elevated route (Shizuku not authorized, not rooted)")
        }

        val r = ElevatedShell.run(argv)
        val payload = JSONObject()
            .put("route", ElevatedShell.route().name)
            .put("code", r.code)
            .put("stdout", JSONArray(r.out))
            .put("stderr", JSONArray(r.err))
            .toString()
        return Receipt(
            requestId = command.requestId,
            capability = capability.family,
            status = if (r.ok) ReceiptStatus.Completed else ReceiptStatus.Failed,
            detail = if (r.ok) "exit 0 via ${ElevatedShell.route().name}" else "exit ${r.code}: ${r.err.firstOrNull().orEmpty()}",
            payload = payload,
            at = System.currentTimeMillis(),
        )
    }

    private fun parseArgv(params: String): List<String>? {
        val o = runCatching { JSONObject(params) }.getOrNull() ?: return null
        o.optJSONArray("argv")?.let { arr -> return (0 until arr.length()).map { arr.getString(it) } }
        val cmd = o.optString("cmd").trim()
        return if (cmd.isEmpty()) null else cmd.split(Regex("\\s+"))
    }

    private fun refuse(c: NodeCommand, d: String) =
        Receipt(c.requestId, c.capability, ReceiptStatus.Refused, d, "{}", System.currentTimeMillis())
}
