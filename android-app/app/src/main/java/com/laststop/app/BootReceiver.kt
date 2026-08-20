package com.laststop.app

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
 * Deliberately does NOT act on MY_PACKAGE_REPLACED (app update/reinstall): Android only grants
 * the "eligible state" exemption needed to start a location-type foreground service from the
 * background for BOOT_COMPLETED, not for MY_PACKAGE_REPLACED when only while-in-use location
 * permission is held — attempting it there throws SecurityException. MainActivity.onResume()
 * already covers the update/reinstall case reliably, since the app is always reopened by the
 * user (or the system) shortly after.
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
