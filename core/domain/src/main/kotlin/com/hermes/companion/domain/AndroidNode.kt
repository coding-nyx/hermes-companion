package com.hermes.companion.domain

data class AndroidNode(
    val nodeId: String,
    val deviceName: String,
    val keyId: String,
    val state: NodeState,
    val capabilities: Set<NodeCapability> = emptySet(),
)

enum class NodeState { Paired, Connected, Disconnected, Revoked }

/**
 * Every capability a paired node can expose. Converged on the id set in
 * `plan/10-architecture/capabilities.md` (§5.3). [exclusive] capabilities take a
 * lease (one holder at a time); [mutating] capabilities require a request-bound
 * approval and carry a grant + expiry.
 */
enum class NodeCapability(
    val family: String,
    val mutating: Boolean = false,
    val exclusive: Boolean = false,
) {
    // ── Read-only ────────────────────────────────────────────────
    NotificationsRead("notifications.read"),
    NotificationsActive("notifications.active"),
    NotificationsActions("notifications.actions"),
    CallsObserve("calls.observe"),
    CallsLog("calls.log"),
    ContactsLookup("contacts.lookup"),
    SmsRead("messages.sms.read"),
    DeviceStatus("device.status"),
    AppUsage("app.usage"),
    LocationRead("location.read"),
    CalendarEvents("calendar.events"),
    MotionActivity("motion.activity"),
    MotionPedometer("motion.pedometer"),
    PhotosLatest("photos.latest"),
    DeviceHealth("device.health"),
    DevicePermissions("device.permissions"),
    DeviceApps("device.apps"),
    ClipboardRead("clipboard.read"),
    MediaSessionRead("media.session.read"),
    ScreenCapture("screen.capture", exclusive = true),

    // ── Mutating ─────────────────────────────────────────────────
    NotificationsDismiss("notifications.dismiss", mutating = true),
    NotificationsAction("notifications.action", mutating = true),
    NotificationsReply("notifications.reply", mutating = true),
    CallsAnswer("calls.answer", mutating = true),
    CallsReject("calls.reject", mutating = true),
    CallsDial("calls.dial", mutating = true),
    SmsSend("messages.sms.send", mutating = true),
    AppsLaunch("apps.launch", mutating = true),
    IntentsSend("intents.send", mutating = true),
    ClipboardWrite("clipboard.write", mutating = true),
    MediaSessionControl("media.session.control", mutating = true),
    ScreenInput("screen.input", mutating = true, exclusive = true),
    ScreenControl("screen.control", mutating = true, exclusive = true), // interactive remote drive
    FilesRead("files.read"),
    FilesWrite("files.write", mutating = true),
    CameraSnap("camera.snap", mutating = true, exclusive = true),
    CameraClip("camera.clip", mutating = true, exclusive = true),
    MicrophoneRecord("microphone.record", mutating = true, exclusive = true),
    CalendarAdd("calendar.add", mutating = true),
    ContactsAdd("contacts.add", mutating = true),
    ShellExec("system.shell", mutating = true); // elevated tier only (Shizuku/root)

    val readOnly: Boolean get() = !mutating

    companion object {
        private val byFamily = entries.associateBy { it.family }
        fun fromFamily(family: String): NodeCapability? = byFamily[family]
    }
}
