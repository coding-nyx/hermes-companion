package com.hermes.companion.node

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher

/**
 * Holds a MediaProjection consent for this process. Accessibility screenshots
 * are preferred (no system dialog after the service is on); this is the
 * fallback for full-frame capture on older APIs.
 */
object ScreenCapture {
    @Volatile
    private var resultCode: Int = Activity.RESULT_CANCELED

    @Volatile
    private var resultData: Intent? = null

    fun hasGrant(): Boolean = resultData != null && resultCode == Activity.RESULT_OK

    fun store(code: Int, data: Intent?) {
        resultCode = code
        resultData = data
    }

    fun clear() {
        resultCode = Activity.RESULT_CANCELED
        resultData = null
    }

    fun requestIntent(ctx: Context): Intent {
        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mpm.createScreenCaptureIntent()
    }

    fun launch(launcher: ActivityResultLauncher<Intent>, ctx: Context) {
        launcher.launch(requestIntent(ctx))
    }

    fun a11yScreenshotSupported(): Boolean = Build.VERSION.SDK_INT >= 30
}
