package com.hermes.companion

import android.app.Application
import com.hermes.companion.backend.BackendRegistry
import com.hermes.companion.backend.MockHermesBackend

/**
 * Application-scoped state. The PoC keeps the registry in-memory only;
 * real builds will hydrate from Room and the Keystore-encrypted token
 * envelopes described in §6.
 */
class CompanionApp : Application() {

    val registry: BackendRegistry by lazy {
        BackendRegistry(MockHermesBackend.defaultFleet())
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: CompanionApp
        fun get(): CompanionApp = instance
    }
}
