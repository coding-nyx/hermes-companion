package com.hermes.companion.node

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hermes.companion.domain.CapabilityHealth
import com.hermes.companion.domain.NodeCommand
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AdapterRegistryTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `device status is always working and reports a payload`() = runTest {
        val registry = defaultAdapterRegistry(context)
        val ds = registry.forFamily("device.status")!!
        assertEquals(CapabilityHealth.Working, ds.health())
        val receipt = ds.invoke(NodeCommand(requestId = "r1", capability = "device.status"))
        assertTrue(receipt.payload.contains("battery"))
    }

    @Test
    fun `notifications read is permission-missing until the listener is enabled`() {
        val registry = defaultAdapterRegistry(context)
        val n = registry.forFamily("notifications.read")!!
        // Robolectric has no enabled listener, so health must NOT claim Working.
        assertEquals(CapabilityHealth.PermissionMissing, n.health())
    }

    @Test
    fun `coverage never advertises more than the registered adapters`() {
        val registry = defaultAdapterRegistry(context)
        assertEquals(registry.all().size, registry.coverage().size)
    }
}
