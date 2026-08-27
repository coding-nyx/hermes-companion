package com.hermes.companion.node.adapters

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.hermes.companion.domain.AndroidRequirement
import com.hermes.companion.domain.CapabilityHealth
import com.hermes.companion.domain.NodeCapability
import com.hermes.companion.domain.NodeCommand
import com.hermes.companion.domain.Receipt
import com.hermes.companion.domain.ReceiptStatus
import com.hermes.companion.node.CapabilityAdapter
import org.json.JSONObject

private fun ok(cmd: NodeCommand, detail: String, payload: String = "{}") =
    Receipt(cmd.requestId, cmd.capability, ReceiptStatus.Completed, detail, payload, System.currentTimeMillis())

private fun fail(cmd: NodeCommand, detail: String) =
    Receipt(cmd.requestId, cmd.capability, ReceiptStatus.Failed, detail, "{}", System.currentTimeMillis())

private fun NodeCommand.json(): JSONObject = runCatching { JSONObject(params) }.getOrDefault(JSONObject())

/** Read the clipboard (best-effort: Android 10+ limits background reads). */
class ClipboardReadAdapter(private val context: Context) : CapabilityAdapter {
    override val capability = NodeCapability.ClipboardRead
    override val requires: Set<AndroidRequirement> = emptySet()
    override fun health() = CapabilityHealth.OsLimited // background reads are restricted on Q+
    override suspend fun invoke(command: NodeCommand): Receipt {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return fail(command, "clipboard unavailable")
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        return ok(command, "read ${text.length} chars", JSONObject().put("text", text).toString())
    }
}

/** Write the clipboard. */
class ClipboardWriteAdapter(private val context: Context) : CapabilityAdapter {
    override val capability = NodeCapability.ClipboardWrite
    override val requires: Set<AndroidRequirement> = emptySet()
    override fun health() = CapabilityHealth.Working
    override suspend fun invoke(command: NodeCommand): Receipt {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return fail(command, "clipboard unavailable")
        val text = command.json().optString("text")
        cm.setPrimaryClip(ClipData.newPlainText("hermes", text))
        return ok(command, "wrote ${text.length} chars")
    }
}

/** Launch an installed app by package name. */
class AppsLaunchAdapter(private val context: Context) : CapabilityAdapter {
    override val capability = NodeCapability.AppsLaunch
    override val requires: Set<AndroidRequirement> = emptySet()
    override fun health() = CapabilityHealth.Working
    override suspend fun invoke(command: NodeCommand): Receipt {
        val pkg = command.json().optString("package")
        if (pkg.isBlank()) return fail(command, "package required")
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return fail(command, "no launch intent for $pkg")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent); ok(command, "launched $pkg")
        }.getOrElse { fail(command, it.message ?: "launch failed") }
    }
}

/** Fire an explicit intent (action + optional data/package). */
class IntentsSendAdapter(private val context: Context) : CapabilityAdapter {
    override val capability = NodeCapability.IntentsSend
    override val requires: Set<AndroidRequirement> = emptySet()
    override fun health() = CapabilityHealth.Working
    override suspend fun invoke(command: NodeCommand): Receipt {
        val o = command.json()
        val action = o.optString("action")
        if (action.isBlank()) return fail(command, "action required")
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        o.optString("data").takeIf { it.isNotBlank() }?.let { intent.data = android.net.Uri.parse(it) }
        o.optString("package").takeIf { it.isNotBlank() }?.let { intent.setPackage(it) }
        return runCatching {
            context.startActivity(intent); ok(command, "sent $action")
        }.getOrElse { fail(command, it.message ?: "intent failed") }
    }
}
