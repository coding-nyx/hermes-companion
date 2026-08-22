package com.hermes.companion.backend

import com.hermes.companion.domain.ConversationRoute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendRegistryIsolationTest {

    @Test
    fun `default fleet exposes two gateways and four profile instances`() = runTest {
        val registry = BackendRegistry(MockHermesBackend.defaultFleet())
        val gateways = registry.gateways.value
        assertEquals(2, gateways.size)
        val profiles = registry.unionRoster()
        assertEquals(4, profiles.size)
    }

    @Test
    fun `same-name profiles are disambiguated in the union roster`() = runTest {
        val registry = BackendRegistry(MockHermesBackend.defaultFleet())
        val profiles = registry.unionRoster()
        val ash = profiles.filter { it.profileId == "ash" }
        assertEquals(2, ash.size)
        assertNotEquals(ash[0].handle.display, ash[1].handle.display)
        assertTrue(
            "ash handles should carry gateway suffix",
            ash.all { "-" in it.handle.display },
        )
    }

    @Test
    fun `removing a gateway clears selection that pointed at it`() = runTest {
        val registry = BackendRegistry(MockHermesBackend.defaultFleet())
        val route = ConversationRoute("gw-cloud", "ash", "any")
        registry.selectRoute(route)
        assertEquals(route, registry.selectedRoute.value)
        registry.removeGateway("gw-cloud")
        assertNull(registry.selectedRoute.value)
    }

    @Test
    fun `selection on a different gateway survives gateway removal`() = runTest {
        val registry = BackendRegistry(MockHermesBackend.defaultFleet())
        val route = ConversationRoute("gw-home", "ash", "any")
        registry.selectRoute(route)
        registry.removeGateway("gw-cloud")
        assertEquals(route, registry.selectedRoute.value)
    }

    @Test
    fun `each backend only serves its own gateway`() = runTest {
        val registry = BackendRegistry(MockHermesBackend.defaultFleet())
        val home = registry.backendFor("gw-home")
        val cloud = registry.backendFor("gw-cloud")
        assertTrue(home != null && cloud != null)
        assertNotEquals(home!!.gateway.id, cloud!!.gateway.id)
    }
}
