package com.hereliesaz.wavefrom.ui

import android.app.Activity
import com.hereliesaz.aznavrail.updates.AzUpdateChecker

/** Starts the shared, installer-aware update check for this distribution build. */
internal fun checkAppUpdates(activity: Activity) {
    AzUpdateChecker.check(activity, "HereLiesAz/WaveFrom")
}
