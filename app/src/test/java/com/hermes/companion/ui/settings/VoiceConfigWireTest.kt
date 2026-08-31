package com.hermes.companion.ui.settings

import android.content.ContextWrapper
import com.hermes.companion.common.VoiceConfig
import com.hermes.companion.data.db.ActiveGatewayEntity
import com.hermes.companion.data.repo.Fleet
import com.hermes.companion.data.repo.FleetRepository
import com.hermes.companion.data.repo.InMemoryNotificationRuleRepository
import com.hermes.companion.domain.GatewayKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * VoiceConfig -> SettingsViewModel wiring tests.
 *
 * SettingsViewModel.setVoiceConfig must:
 *   1. Persist the snapshot to `voice.json` under [Context.filesDir] so the
 *      OS-instantiated voice services in :node can pick it up on their next
 *      reconnect (same file-backed bridge pattern as ActiveGatewayConfig).
 *   2. Update the in-memory `state.voice` field so the UI re-renders.
 *
 * VoiceConfig is a Kotlin object with a static writeSync(filesDir, snap).
 * Spying on a static is awkward under plain JUnit — instead we verify the
 * externally observable side effect: the file-on-disk contract. The :node
 * voice services read this same `voice.json` on every reconnect, so this
 * test is exercising the exact contract the NLS will consume.
 *
 * The Context stub uses [ContextWrapper] (null base) so only [getFilesDir]
 * needs to be overridden — the ViewModel never pokes another Context
 * method, so any unexpected usage would fail loudly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceConfigWireTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setVoiceConfig persists to voice json under filesDir`() = runTest(mainDispatcher) {
        val dir = Files.createTempDirectory("settings-voice-wire").toFile()
        val ctx = FakeContext(dir)
        val vm = newViewModel(ctx)

        val newSnap = VoiceConfig.VoiceSnapshot(
            wakeEnabled = false,
            wakePhrase = "yo hermes",
            sttEngine = "android",
            ttsEngine = "none",
            ttsVoice = "",
            pttHotkey = "headset",
        )

        vm.setVoiceConfig(newSnap)

        // 1. The file must exist on disk — this is the bridge :node reads.
        val file = File(dir, "voice.json")
        assertTrue("writeSync must produce voice.json", file.exists())

        // 2. A subsequent readSync (the exact call :node voice services make)
        //    must see the new snapshot. The defaults cannot be confused with
        //    the new values because ttsEngine differs ("none" vs "android").
        val read = VoiceConfig.readSync(dir)
        assertEquals(newSnap, read)
        assertEquals("none", read.ttsEngine)
        assertEquals("headset", read.pttHotkey)
        assertEquals("yo hermes", read.wakePhrase)
        assertFalse("wake-word must remain off in Phase 1", read.wakeEnabled)
    }

    @Test
    fun `state voice field reflects defaults when no voice json exists`() = runTest(mainDispatcher) {
        val dir = Files.createTempDirectory("settings-voice-defaults").toFile()
        val ctx = FakeContext(dir)
        val vm = newViewModel(ctx)

        // Initial state is seeded synchronously in SettingsViewModel.init via
        // VoiceConfig.readSync(context.filesDir) -> DEFAULT_VOICE.
        assertEquals(VoiceConfig.DEFAULT_VOICE, vm.state.value.voice)
        assertEquals("android", vm.state.value.voice.ttsEngine)
        assertEquals("none", vm.state.value.voice.pttHotkey)
        assertEquals("hey hermes", vm.state.value.voice.wakePhrase)
    }

    @Test
    fun `setVoiceConfig updates state voice field`() = runTest(mainDispatcher) {
        val dir = Files.createTempDirectory("settings-voice-state").toFile()
        val ctx = FakeContext(dir)
        val vm = newViewModel(ctx)

        val newSnap = VoiceConfig.VoiceSnapshot(
            wakeEnabled = false,
            wakePhrase = "hey hermes",
            sttEngine = "android",
            ttsEngine = "android",
            ttsVoice = "en-US-Wavenet-A",
            pttHotkey = "bt_button",
        )
        vm.setVoiceConfig(newSnap)

        // The combine(stateIn) pipeline emits asynchronously; pull one
        // value from the flow rather than reading state.value directly so
        // the assertion is deterministic.
        val after = vm.state.first { it.voice == newSnap }
        assertEquals("en-US-Wavenet-A", after.voice.ttsVoice)
        assertEquals("bt_button", after.voice.pttHotkey)
    }

    @Test
    fun `ttsVoice empty string persists as empty (system default marker)`() = runTest(mainDispatcher) {
        val dir = Files.createTempDirectory("settings-voice-empty").toFile()
        val ctx = FakeContext(dir)
        val vm = newViewModel(ctx)

        val snap = VoiceConfig.DEFAULT_VOICE.copy(ttsEngine = "none", ttsVoice = "")
        vm.setVoiceConfig(snap)

        val read = VoiceConfig.readSync(dir)
        assertEquals("", read.ttsVoice)
        assertEquals("none", read.ttsEngine)
    }

    @Test
    fun `wakeEnabled toggle would persist but is locked off in Phase 1`() = runTest(mainDispatcher) {
        // Defensive: even though the UI ignores wakeEnabled toggles in Phase 1,
        // the file-backed bridge must round-trip true if the VM is called
        // directly (e.g. via a future test or a programmatic update). This
        // proves the wire is end-to-end alive, not just for the editable
        // fields.
        val dir = Files.createTempDirectory("settings-voice-wake").toFile()
        val ctx = FakeContext(dir)
        val vm = newViewModel(ctx)

        val snap = VoiceConfig.DEFAULT_VOICE.copy(wakeEnabled = true)
        vm.setVoiceConfig(snap)

        val read = VoiceConfig.readSync(dir)
        assertTrue("wakeEnabled must round-trip through writeSync", read.wakeEnabled)
    }

    private fun newViewModel(ctx: android.content.Context): SettingsViewModel =
        SettingsViewModel(
            fleet = FakeFleetRepository(),
            context = ctx,
            ruleRepo = InMemoryNotificationRuleRepository(),
        )
}

/**
 * ContextWrapper(null) is allowed — the base is just a stub and we only
 * override [getFilesDir] (the one Context method SettingsViewModel touches).
 * Any other Context call would throw, which is the desired loud failure.
 *
 * The override body references a private backing field (not the constructor
 * parameter directly) because Kotlin does not allow referencing constructor
 * parameters from a method body.
 */
private class FakeContext(dir: File) : ContextWrapper(null) {
    private val dir: File = dir
    override fun getFilesDir(): File = dir
}

/**
 * Minimal in-memory FleetRepository. SettingsViewModel reads
 * [fleet] + [observeActive] for the state combine; the rest are stubbed
 * because no test in this file exercises addGateway / setActive.
 */
private class FakeFleetRepository : FleetRepository {
    private val fleet = MutableStateFlow(Fleet())
    private val active = MutableStateFlow<String?>(null)

    override fun fleet(): Flow<Fleet> = fleet
    override fun observeActive(): Flow<String?> = active

    override suspend fun refresh() = Unit
    override suspend fun addGateway(label: String, baseUrl: String, kind: GatewayKind): Result<String> =
        Result.success("gw-test")
    override suspend fun forget(gatewayId: String): Result<Unit> = Result.success(Unit)
    override suspend fun observeActiveNodeId(gatewayId: String): String? = null
    override fun observeActiveFull(): Flow<ActiveGatewayEntity?> = MutableStateFlow<ActiveGatewayEntity?>(null)
    override fun observeActiveId(): Flow<String?> = active
    override suspend fun setActive(gatewayId: String, url: String, nodeId: String): Result<Unit> {
        active.value = gatewayId
        return Result.success(Unit)
    }
    override suspend fun observeActiveProfileId(gatewayId: String): String? = null
    override suspend fun setActiveProfile(gatewayId: String, profileId: String): Result<Unit> =
        Result.success(Unit)
}
