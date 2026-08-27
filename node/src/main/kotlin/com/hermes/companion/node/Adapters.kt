package com.hermes.companion.node

import android.content.Context
import com.hermes.companion.node.adapters.DeviceStatusAdapter
import com.hermes.companion.node.adapters.NotificationsReadAdapter

/**
 * The adapters this build ships, in coverage order. Grows as each capability
 * lands; every entry advertises real [CapabilityAdapter.health].
 */
fun defaultAdapters(context: Context): List<CapabilityAdapter> = listOf(
    DeviceStatusAdapter(context),
    NotificationsReadAdapter(context),
)

fun defaultAdapterRegistry(context: Context): AdapterRegistry =
    AdapterRegistry(defaultAdapters(context.applicationContext))
