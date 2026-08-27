package com.hermes.companion

import android.app.Application
import com.hermes.companion.data.repo.CompanionData
import com.hermes.companion.di.AppScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application-scoped state. Hilt owns the object graph; the injected [scope]
 * outlives every screen so a run keeps being collected into the database after
 * you leave Chat. No gateway is baked in — the fleet is hydrated from Room and
 * grows through Discovery / Settings.
 */
@HiltAndroidApp
class CompanionApp : Application() {

    @Inject lateinit var data: CompanionData

    @Inject @AppScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            data.bootstrap(emptyList())
            data.fleet.refresh()
        }
    }
}
