package com.hermes.companion.security

import androidx.fragment.app.FragmentActivity
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/** Holds a weak reference to the foreground FragmentActivity for BiometricPrompt. */
@Singleton
class CurrentActivityHolder @Inject constructor() {
    @Volatile private var ref: WeakReference<FragmentActivity>? = null
    fun set(activity: FragmentActivity) { ref = WeakReference(activity) }
    fun clear(activity: FragmentActivity) { if (ref?.get() === activity) ref = null }
    fun fragmentActivity(): FragmentActivity? = ref?.get()
}
