package com.laststop.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Always-on, low-power watcher. No journey has to be started for this to run — it is what
 * notices "this person is traveling" in the first place and prompts the user into the setup
 * flow. Stopped while an active journey (JourneyTrackingService) is running and resumed after.
 */
class PassiveDetectionService : Service() {
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager
    private var lastFixLocation: Location? = null
    private var lastFixElapsedMs: Long = 0L
    private var sessionDistanceMeters = 0f
    private var consecutiveFastSpeedReadings = 0
    private var acceptedFixCount = 0
    private var rejectedFixCount = 0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::handleLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(WATCH_CHANNEL_ID, "Watching for travel", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Quiet background notice that quiklook is watching for your next trip"
                setShowBadge(false)
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(PROMPT_CHANNEL_ID, "Travel detected", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when quiklook notices you're on the move"
                enableVibration(true)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TEST_ALERT -> {
                if (BuildConfig.DEBUG) showTravelDetectedAlert()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                locationClient.removeLocationUpdates(locationCallback)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Android 14+ additionally requires the app to be in an "eligible state" (visible /
        // recently interacted with) to start a location-type foreground service when only
        // while-in-use permission is held — a background-triggered start (e.g. from
        // BootReceiver on MY_PACKAGE_REPLACED) can be rejected even with permission granted.
        // MainActivity.onResume() retries this every time the app is actually opened, so
        // failing quietly here is safe rather than crashing the whole process.
        try {
            startForeground(NOTIFICATION_ID, buildWatchNotification())
        } catch (e: SecurityException) {
            Log.d(TAG, "startForeground rejected (not in an eligible state): ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        requestUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        locationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun requestUpdates() {
        if (!hasLocationPermission()) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, WATCH_INTERVAL_MS)
            .setMinUpdateIntervalMillis(WATCH_MIN_INTERVAL_MS)
            .setMinUpdateDistanceMeters(20f)
            .build()
        locationClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun handleLocation(location: Location) {
        if (location.hasAccuracy() && location.accuracy > 100f) {
            rejectedFixCount++
            return
        }

        val now = SystemClock.elapsedRealtime()
        val previous = lastFixLocation
        if (previous != null) {
            val dtSeconds = (now - lastFixElapsedMs) / 1000f
            val segmentDistance = previous.distanceTo(location)
            val impliedSpeed = if (dtSeconds > 0f) segmentDistance / dtSeconds else 0f
            if (dtSeconds > 0f && impliedSpeed > MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND) {
                rejectedFixCount++
                return
            }
            if (dtSeconds > STATIONARY_RESET_SECONDS) {
                // Long gap since the last fix — treat this as the start of a fresh outing
                // rather than counting the gap itself as travelled distance.
                sessionDistanceMeters = 0f
            } else {
                sessionDistanceMeters += segmentDistance
            }
        }

        acceptedFixCount++
        lastFixLocation = Location(location)
        lastFixElapsedMs = now

        val movingFast = location.hasSpeed() && location.speed >= FAST_SPEED_METERS_PER_SECOND
        consecutiveFastSpeedReadings = if (movingFast) consecutiveFastSpeedReadings + 1 else 0

        Log.d(
            TAG,
            "accepted=$acceptedFixCount rejected=$rejectedFixCount accuracy=${location.accuracy} " +
                "sessionDistance=$sessionDistanceMeters consecutiveFast=$consecutiveFastSpeedReadings"
        )

        if (sessionDistanceMeters >= RAPID_DISTANCE_METERS || consecutiveFastSpeedReadings >= CONSECUTIVE_FAST_READINGS_TO_ALERT) {
            sessionDistanceMeters = 0f
            consecutiveFastSpeedReadings = 0
            lastFixLocation = null
            showTravelDetectedAlert()
        }
    }

    private fun showTravelDetectedAlert() {
        val openApp = PendingIntent.getActivity(
            this,
            3,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_TRAVEL_SETUP
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notificationManager.notify(
            PROMPT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, PROMPT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Looks like you're traveling")
                .setContentText("Tap to set up this trip in quiklook")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "Tap to check your belongings and set a timer or destination for this trip."
                ))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()
        )
    }

    private fun buildWatchNotification(): Notification =
        NotificationCompat.Builder(this, WATCH_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("quiklook is watching for your next trip")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_START = "com.laststop.app.action.START_WATCHING"
        const val ACTION_STOP = "com.laststop.app.action.STOP_WATCHING"
        const val ACTION_OPEN_TRAVEL_SETUP = "com.laststop.app.action.OPEN_TRAVEL_SETUP"
        const val ACTION_TEST_ALERT = "com.laststop.app.action.TEST_TRAVEL_ALERT"
        private const val WATCH_CHANNEL_ID = "passive_watch"
        private const val PROMPT_CHANNEL_ID = "travel_prompts"
        private const val NOTIFICATION_ID = 2001
        private const val PROMPT_NOTIFICATION_ID = 2002
        private const val WATCH_INTERVAL_MS = 20_000L
        private const val WATCH_MIN_INTERVAL_MS = 15_000L
        private const val STATIONARY_RESET_SECONDS = 300f
        private const val RAPID_DISTANCE_METERS = 400f
        private const val FAST_SPEED_METERS_PER_SECOND = 8f
        private const val CONSECUTIVE_FAST_READINGS_TO_ALERT = 2
        private const val MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND = 55f
        private const val TAG = "PassiveDetection"
    }
}
