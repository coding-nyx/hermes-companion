package com.hermes.companion.node

import android.content.Context
import com.hermes.companion.node.adapters.AppUsageAdapter
import com.hermes.companion.node.adapters.AppsLaunchAdapter
import com.hermes.companion.node.adapters.CallsLogAdapter
import com.hermes.companion.node.adapters.ClipboardReadAdapter
import com.hermes.companion.node.adapters.ClipboardWriteAdapter
import com.hermes.companion.node.adapters.ContactsLookupAdapter
import com.hermes.companion.node.adapters.DeviceStatusAdapter
import com.hermes.companion.node.adapters.IntentsSendAdapter
import com.hermes.companion.node.adapters.LocationReadAdapter
import com.hermes.companion.node.adapters.NotificationsDismissAdapter
import com.hermes.companion.node.adapters.NotificationsReadAdapter
import com.hermes.companion.node.adapters.NotificationsReplyAdapter

/**
 * The adapters this build ships, in coverage order. Grows as each capability
 * lands; every entry advertises real [CapabilityAdapter.health]. The dispatcher,
 * lease and grant gates that stand in front of invoke() arrive with the broker.
 */
fun defaultAdapters(context: Context): List<CapabilityAdapter> = listOf(
    // Read
    DeviceStatusAdapter(context),
    NotificationsReadAdapter(context),
    ClipboardReadAdapter(context),
    AppUsageAdapter(context),
    LocationReadAdapter(context),
    ContactsLookupAdapter(context),
    CallsLogAdapter(context),
    // Mutating
    AppsLaunchAdapter(context),
    IntentsSendAdapter(context),
    ClipboardWriteAdapter(context),
    NotificationsDismissAdapter(context),
    NotificationsReplyAdapter(context),
)

fun defaultAdapterRegistry(context: Context): AdapterRegistry =
    AdapterRegistry(defaultAdapters(context.applicationContext))
