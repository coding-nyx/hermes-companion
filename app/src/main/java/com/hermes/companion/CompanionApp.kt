package com.hermes.companion

import android.app.Application
import com.hermes.companion.data.repo.CompanionData
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.GatewayKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application-scoped state. The scope here outlives every screen, so a run
 * keeps being collected into the database after you leave Chat. Step 4 moves
 * it into a foreground service, which is what makes a run survive the app
 * being backgrounded.
 */
class CompanionApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val data: CompanionData by lazy { CompanionData(this, scope) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        scope.launch {
            data.bootstrap(seedFleet())
            data.fleet.refresh()
        }
    }

    /** Only used on first run, when the gateway table is still empty. */
    private fun seedFleet(): List<GatewayConnection> {
        val host = BuildConfig.DEFAULT_HERMES_HOST
        val port = BuildConfig.DEFAULT_HERMES_PORT
        return listOf(
            GatewayConnection("gw-hub11", "Hub-11 (Live)", GatewayKind.RemoteHttp, "http://100.88.4.63:7800/gw-hub11", "none"),
            GatewayConnection("gw-home", "Home (mock)", GatewayKind.RemoteHttp, "http://$host:$port/gw-home", "none"),
            GatewayConnection("gw-cloud", "Cloud (mock)", GatewayKind.RemoteHttp, "http://$host:$port/gw-cloud", "none"),
        )
    }

    companion object {
        private lateinit var instance: CompanionApp
        fun get(): CompanionApp = instance
    }
}
