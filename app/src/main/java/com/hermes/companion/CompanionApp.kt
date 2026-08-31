package com.hermes.companion

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.hermes.companion.data.repo.CompanionData
import com.hermes.companion.di.AppScope
import com.hermes.companion.security.CurrentActivityHolder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application-scoped state. Hilt owns the object graph; the injected [scope]
 * outlives every screen so a run keeps being collected after you leave Chat.
 *
 * No gateway is baked in. The fleet is hydrated from Room and grown through
 * Discovery / Settings. The PoC demo fleet (two in-process mock gateways) was
 * dropped from the bootstrap on user request: a fresh install now lands on an
 * empty gateway list so the only ways to populate it are Discovery (LAN scan
 * or manual URL) or Pair-as-node (T8).
 */
@HiltAndroidApp
class CompanionApp : Application() {

    @Inject lateinit var data: CompanionData
    @Inject @AppScope lateinit var scope: CoroutineScope
    @Inject lateinit var activityHolder: CurrentActivityHolder

    override fun onCreate() {
        Log.i("BootSequence", "onCreate start (pid=${android.os.Process.myPid()})")
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
        // Surface coroutine crashes in logcat. Without this, a thrown
        // IllegalStateException inside the bootstrap launch{} block would
        // be eaten by SupervisorJob and the user would silently see an
        // empty fleet / missing profiles with no diagnostic.
        val uncaught = CoroutineExceptionHandler { _, t ->
            Log.e("BootSequence", "uncaught exception in @AppScope launch", t)
        }
        Log.i("BootSequence", "data injected; calling installElevatedTier")
        data.installElevatedTier()
        Log.i("BootSequence", "installElevatedTier returned; launching bootstrap")
        scope.launch(uncaught) {
            Log.i("BootSequence", "bootstrap start")
            // Bootstrap with no seed: the gateway store starts empty on a fresh
            // install. MockHermesBackend is still available for tests.
            data.bootstrap(emptyList())
            val count = data.fleet.fleet().first().gateways.size
            Log.i("BootSequence", "bootstrap done, gateways count: $count")
            Log.i("BootSequence", "calling fleet.refresh()")
            data.fleet.refresh()
            val after = data.fleet.fleet().first().gateways.size
            Log.i("BootSequence", "fleet.refresh done, gateways count: $after")
        }
    }
}
