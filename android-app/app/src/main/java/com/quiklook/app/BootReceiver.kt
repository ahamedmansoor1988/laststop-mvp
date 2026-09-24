package com.quiklook.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Restarts tracking after a reboot — without this, both the passive watcher and an in-progress
 * journey only ever (re)start from MainActivity.onResume, so a phone reboot mid-trip silently
 * stops all tracking. This matters most for long journeys (a multi-hour train/bus ride), where
 * the odds of a reboot happening mid-trip are far higher than for a five-minute walk.
 *
 * Note that this is best-effort, not guaranteed. A location-type foreground service cannot be
 * started from the background while the app holds only while-in-use location permission — and
 * that restriction is NOT waived by the BOOT_COMPLETED exemption, which only lifts the general
 * background-start rule. So these calls may well be rejected with SecurityException; the
 * services catch it, and JourneyTrackingService posts a "tap to resume tracking" notification
 * so a reboot mid-trip is visible rather than silent.
 *
 * Granting ACCESS_BACKGROUND_LOCATION would make the restart reliable, but that permission
 * triggers Google Play's sensitive-permission review (declaration form plus demo video) for
 * the sake of this one path, so we deliberately don't request it. MainActivity.onResume()
 * restarts tracking whenever the user next opens the app, which is always eligible.
 *
 * Deliberately does NOT act on MY_PACKAGE_REPLACED (app update/reinstall) either, for the same
 * reason — MainActivity.onResume() covers that case, since the app is reopened shortly after.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) return

        val prefs = context.getSharedPreferences("laststop", Context.MODE_PRIVATE)
        if (prefs.getBoolean("journey_active", false)) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, JourneyTrackingService::class.java)
            )
        } else {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PassiveDetectionService::class.java).apply { action = PassiveDetectionService.ACTION_START }
            )
        }
    }
}
