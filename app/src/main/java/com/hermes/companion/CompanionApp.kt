package com.hermes.companion

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.hermes.companion.data.repo.CompanionData
import com.hermes.companion.di.AppScope
import com.hermes.companion.security.CurrentActivityHolder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application-scoped state. Hilt owns the object graph; the injected [scope]
 * outlives every screen so a run keeps being collected after you leave Chat. No
 * gateway is baked in — the fleet is hydrated from Room and grown through
 * Discovery / Settings.
 */
@HiltAndroidApp
class CompanionApp : Application() {

    @Inject lateinit var data: CompanionData
    @Inject @AppScope lateinit var scope: CoroutineScope
    @Inject lateinit var activityHolder: CurrentActivityHolder

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is FragmentActivity) activityHolder.set(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                if (activity is FragmentActivity) activityHolder.clear(activity)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        data.installElevatedTier()
        scope.launch {
            data.bootstrap(emptyList())
            data.fleet.refresh()
        }
    }
}
