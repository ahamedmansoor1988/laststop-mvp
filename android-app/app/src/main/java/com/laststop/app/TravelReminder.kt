package com.laststop.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * A standing nudge, every twelve hours, asking whether a trip is coming up.
 *
 * Deliberately generic: it never mentions where you are or whether location is on, so it says
 * the same thing to everyone and reveals nothing. It is also quiet by design — a low-importance
 * channel, so it lands in the shade without a sound or heads-up card. An app whose whole promise
 * is that you don't have to think about it cannot afford a reminder that interrupts.
 *
 * Skipped while a trip is already running, since the tracking notification is on screen anyway.
 */
class TravelReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            schedule(context)
            return
        }

        val prefs = context.getSharedPreferences("laststop", Context.MODE_PRIVATE)
        if (prefs.getBoolean("journey_active", false)) return
        if (!prefs.getBoolean("onboarding_complete", false)) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Trip reminders", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "An occasional nudge to set up a trip before you head out"
                    setShowBadge(false)
                }
            )
        }

        val openApp = PendingIntent.getActivity(
            context,
            7,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Heading out today?")
                .setContentText("Set up a trip and QuikLook will remind you to check your things before you get out.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Set up a trip and QuikLook will remind you to check your things before you get out."
                    )
                )
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
    }

    companion object {
        private const val CHANNEL_ID = "trip_reminders"
        private const val NOTIFICATION_ID = 2003
        private const val REQUEST_CODE = 8

        /**
         * Inexact on purpose: the exact minute does not matter, and an inexact alarm lets the
         * system batch it, which costs the user far less battery than a precise one would.
         */
        fun schedule(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val first = System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_DAY
            alarms.setInexactRepeating(
                AlarmManager.RTC,
                first,
                AlarmManager.INTERVAL_HALF_DAY,
                pendingIntent(context)
            )
        }

        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
        }

        private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, TravelReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
