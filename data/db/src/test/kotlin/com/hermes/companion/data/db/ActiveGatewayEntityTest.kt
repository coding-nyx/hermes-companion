package com.hermes.companion.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T3A: Active-gateway selection (singleton table `active_gateway`).
 *
 * The singleton-row model: the table holds exactly one row, PK=1, with the
 * currently-active gatewayId. No row -> no active selection. Setter replaces;
 * there is exactly one active row at any moment.
 */
class ActiveGatewayEntityTest {

    @Test
    fun `singleton row id is 1`() {
        val entity = ActiveGatewayEntity(
            id = 1,
            gatewayId = "gw-1",
            updatedAt = 1_700_000_000L,
        )
        assertEquals(1, entity.id)
        assertEquals("gw-1", entity.gatewayId)
        assertEquals(1_700_000_000L, entity.updatedAt)
    }
}

