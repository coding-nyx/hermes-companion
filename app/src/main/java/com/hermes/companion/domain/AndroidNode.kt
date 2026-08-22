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
 * Set of capabilities a paired node declares. Mirrors §5.3 of the plan.
 */
enum class NodeCapability(val family: String) {
    // Read-only
    NotificationsRead("notifications.read"),
    CallsObserve("calls.observe"),
    ContactsLookup("contacts.lookup"),
    SmsRead("messages.sms.read"),
    DeviceStatus("device.status"),
    AppUsage("app.usage"),
    LocationRead("location.read"),
    ScreenCapture("screen.capture"),
    ClipboardRead("clipboard.read"),
    MediaSessionRead("media.session.read"),

    // Mutating
    NotificationsAct("notifications.action"),
    NotificationsReply("notifications.reply"),
    CallsAnswer("calls.answer"),
    CallsReject("calls.reject"),
    CallsDial("calls.dial"),
    SmsSend("messages.sms.send"),
    AppsLaunch("apps.launch"),
    IntentsSend("intents.send"),
    ClipboardWrite("clipboard.write"),
    MediaSessionControl("media.session.control"),
    ScreenInput("screen.input"),
    FilesRead("files.read"),
    FilesWrite("files.write"),
    CameraCapture("camera.capture"),
    MicrophoneRecord("microphone.record");
}
