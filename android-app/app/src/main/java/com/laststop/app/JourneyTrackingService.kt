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
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JourneyTrackingService : Service() {
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager
    private var journeyMode = MODE_DESTINATION
    private var destinationName = "Destination"
    private var destinationLatitude = 0.0
    private var destinationLongitude = 0.0
    private var travelMode = "Bike"
    private var durationEndAtEpochMs = 0L
    private var highAccuracyMode = false
    private var fiveMinuteAlertSent = false
    private var threeMinuteAlertSent = false
    private var oneMinuteAlertSent = false
    private var reachedAlertSent = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient()
    private var routeFetchInFlight = false
    private var lastRouteFetchElapsedMs = 0L
    private var lastFetchRemainingSeconds: Float? = null
    private var liveEtaSeconds: Float? = null
    private var liveEtaFetchedAtElapsedMs = 0L
    private var liveDistanceMeters: Float? = null
    private var consecutiveFetchFailures = 0

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerTick = object : Runnable {
        override fun run() {
            onDurationTick()
            timerHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

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
            NotificationChannel(CHANNEL_ID, "Active journeys", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows progress while quiklook tracks a journey"
                setShowBadge(false)
            }
        )
        val alarmSoundUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        notificationManager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "Journey alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts as you approach the end of a journey"
                enableVibration(true)
                setSound(
                    alarmSoundUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TEST_ALERT -> {
                if (BuildConfig.DEBUG) showStageAlert(Stage.REACHED)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            ACTION_DISMISS_ALERT -> {
                // Just clears the notification — the next checkpoint still fires on its own
                // schedule regardless, so this doesn't touch tracking state at all.
                notificationManager.cancel(RAPID_ALERT_NOTIFICATION_ID)
                return START_STICKY
            }
            ACTION_START -> readAndStoreJourney(intent)
            else -> loadJourney()
        }

        if (journeyMode == MODE_DESTINATION && !hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification(statusText()))
        } catch (e: SecurityException) {
            // Android refuses to start a location-type foreground service from the background
            // while only while-in-use location permission is held — this is what happens when
            // BootReceiver tries to resume a journey after a reboot. Rather than dying silently
            // and leaving a long trip untracked, tell the user how to get tracking back.
            Log.d(TAG, "startForeground rejected (not in an eligible state): ${e.message}")
            notifyTrackingNeedsResume()
            stopSelf()
            return START_NOT_STICKY
        }
        if (journeyMode == MODE_DESTINATION) {
            requestUpdates()
        } else {
            timerHandler.removeCallbacks(timerTick)
            timerHandler.post(timerTick)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        locationClient.removeLocationUpdates(locationCallback)
        timerHandler.removeCallbacks(timerTick)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun readAndStoreJourney(intent: Intent) {
        journeyMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_DESTINATION
        fiveMinuteAlertSent = false
        oneMinuteAlertSent = false
        reachedAlertSent = false
        liveEtaSeconds = null
        liveEtaFetchedAtElapsedMs = 0L
        liveDistanceMeters = null
        consecutiveFetchFailures = 0
        lastRouteFetchElapsedMs = 0L
        lastFetchRemainingSeconds = null
        val prefs = getSharedPreferences("laststop", MODE_PRIVATE).edit()
            .putString("journey_mode", journeyMode)
            .putBoolean("journey_active", true)

        if (journeyMode == MODE_DURATION) {
            val minutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 10)
            durationEndAtEpochMs = System.currentTimeMillis() + minutes * 60_000L
            prefs.putInt("duration_minutes", minutes)
            prefs.putLong("duration_end_at", durationEndAtEpochMs)
            prefs.putLong("eta_arrival_at_epoch_ms", durationEndAtEpochMs)
        } else {
            destinationName = intent.getStringExtra(EXTRA_NAME) ?: "Destination"
            destinationLatitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
            destinationLongitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
            travelMode = intent.getStringExtra(EXTRA_TRAVEL_MODE) ?: "Bike"
            prefs.putString("destination_name", destinationName)
                .putString("destination_latitude", destinationLatitude.toString())
                .putString("destination_longitude", destinationLongitude.toString())
                .putString("travel_mode", travelMode)
                .remove("eta_arrival_at_epoch_ms")
        }
        prefs.apply()
    }

    private fun loadJourney() {
        val preferences = getSharedPreferences("laststop", MODE_PRIVATE)
        journeyMode = preferences.getString("journey_mode", MODE_DESTINATION) ?: MODE_DESTINATION
        if (journeyMode == MODE_DURATION) {
            durationEndAtEpochMs = preferences.getLong("duration_end_at", System.currentTimeMillis())
        } else {
            destinationName = preferences.getString("destination_name", "Destination") ?: "Destination"
            destinationLatitude = preferences.getString("destination_latitude", "0")?.toDoubleOrNull() ?: 0.0
            destinationLongitude = preferences.getString("destination_longitude", "0")?.toDoubleOrNull() ?: 0.0
            travelMode = preferences.getString("travel_mode", "Bike") ?: "Bike"
        }
    }

    private fun requestUpdates() {
        if (!hasLocationPermission()) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return
        locationClient.removeLocationUpdates(locationCallback)
        locationClient.requestLocationUpdates(buildLocationRequest(), locationCallback, mainLooper)
    }

    private fun buildLocationRequest(): LocationRequest = if (highAccuracyMode) {
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .build()
    } else {
        LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15_000L)
            .setMinUpdateIntervalMillis(10_000L)
            .setMinUpdateDistanceMeters(25f)
            .build()
    }

    private fun handleLocation(location: Location) {
        if (location.hasAccuracy() && location.accuracy > 100f) return

        val distanceToDestination = distanceToDestination(location)
        val movingFast = location.hasSpeed() && location.speed >= FAST_SPEED_METERS_PER_SECOND
        val shouldUseHighAccuracy = movingFast || distanceToDestination <= NEAR_DESTINATION_METERS
        if (shouldUseHighAccuracy != highAccuracyMode) {
            highAccuracyMode = shouldUseHighAccuracy
            requestUpdates()
        }

        val liveEtaNow = currentLiveEtaSeconds()
        val etaSeconds = liveEtaNow ?: run {
            val effectiveSpeed = if (location.hasSpeed() && location.speed > 0.5f) {
                location.speed.toDouble()
            } else {
                TravelSpeeds.metersPerSecond(travelMode)
            }
            val estimatedRoadDistance = distanceToDestination * ROAD_DISTANCE_FACTOR
            (estimatedRoadDistance / effectiveSpeed).toFloat()
        }
        val displayDistance = liveDistanceMeters?.takeIf { it >= 0f && liveEtaNow != null } ?: distanceToDestination

        val now = SystemClock.elapsedRealtime()
        val elapsedSinceLastFetchSeconds = (now - lastRouteFetchElapsedMs) / 1000f
        val requiredDelaySeconds = computeNextPollDelaySeconds(etaSeconds)
        // No alert past the 1-minute mark depends on live ETA — "arrived" is pure GPS distance —
        // so once that alert has fired, further polling only refreshes cosmetic display text.
        // Not worth the credits.
        if (!oneMinuteAlertSent && !routeFetchInFlight && elapsedSinceLastFetchSeconds >= requiredDelaySeconds) {
            lastFetchRemainingSeconds = etaSeconds
            lastRouteFetchElapsedMs = now
            fetchLiveRoute(location)
        }

        Log.d(
            TAG,
            "eta source=${if (liveEtaNow != null) "live-route" else "fallback-estimate"} " +
                "straightLineDistance=$distanceToDestination etaSeconds=$etaSeconds"
        )

        persistArrivalTime(etaSeconds)
        checkStageAlerts(remainingSeconds = etaSeconds, arrivedByDistance = distanceToDestination <= ARRIVAL_RADIUS_METERS)
        updateNotification(statusText(displayDistance, etaSeconds))
    }

    private fun fetchLiveRoute(origin: Location) {
        val apiKey = BuildConfig.ROUTES_API_KEY
        if (apiKey.isBlank() || apiKey == "DEFAULT_API_KEY") {
            Log.d(TAG, "ROUTES_API_KEY not configured — using fallback estimate")
            return
        }
        routeFetchInFlight = true
        serviceScope.launch {
            val result = runCatching { requestRoute(origin, apiKey) }.getOrNull()
            routeFetchInFlight = false
            if (result != null) {
                liveEtaSeconds = result.first
                liveEtaFetchedAtElapsedMs = SystemClock.elapsedRealtime()
                liveDistanceMeters = result.second
                consecutiveFetchFailures = 0
                Log.d(TAG, "live route fetched: etaSeconds=${result.first} distanceMeters=${result.second}")
            } else {
                consecutiveFetchFailures = (consecutiveFetchFailures + 1).coerceAtMost(5)
                Log.d(TAG, "live route fetch failed or returned no route (failures=$consecutiveFetchFailures) — using fallback estimate")
            }
        }
    }

    /** The live ETA as a fixed point in time, decayed by how long ago it was actually fetched —
     * without this, a route fetched hours ago (now that polling is sparse for long trips) would
     * keep being reported as-is forever instead of counting down, freezing the arrival clock and
     * risking a false "arrived" trigger if it were ever allowed to decay below zero undetected. */
    private fun currentLiveEtaSeconds(): Float? {
        val eta = liveEtaSeconds ?: return null
        val elapsedSinceFetch = (SystemClock.elapsedRealtime() - liveEtaFetchedAtElapsedMs) / 1000f
        val decayed = eta - elapsedSinceFetch
        return if (decayed > 0f) decayed else null
    }

    private fun requestRoute(origin: Location, apiKey: String): Pair<Float, Float>? {
        val mode = routesTravelMode(travelMode)
        val body = JSONObject().apply {
            put("origin", JSONObject().put("location", JSONObject().put("latLng",
                JSONObject().put("latitude", origin.latitude).put("longitude", origin.longitude))))
            put("destination", JSONObject().put("location", JSONObject().put("latLng",
                JSONObject().put("latitude", destinationLatitude).put("longitude", destinationLongitude))))
            put("travelMode", mode)
            if (mode == "DRIVE") put("routingPreference", "TRAFFIC_AWARE")
        }
        val request = Request.Builder()
            .url("https://routes.googleapis.com/directions/v2:computeRoutes")
            .addHeader("X-Goog-Api-Key", apiKey)
            .addHeader("X-Goog-FieldMask", "routes.duration,routes.distanceMeters")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.d(TAG, "Routes API HTTP ${response.code}: ${response.body?.string()}")
                return null
            }
            val json = JSONObject(response.body?.string() ?: return null)
            val routes = json.optJSONArray("routes") ?: return null
            if (routes.length() == 0) return null
            val route = routes.getJSONObject(0)
            val seconds = route.optString("duration").removeSuffix("s").toFloatOrNull() ?: return null
            val distance = route.optDouble("distanceMeters", -1.0).toFloat()
            return seconds to distance
        }
    }

    private fun routesTravelMode(mode: String): String = when (mode) {
        "Walk" -> "WALK"
        "Bike" -> "BICYCLE"
        "Car" -> "DRIVE"
        "Bus", "Train" -> "TRANSIT"
        else -> "DRIVE"
    }

    private fun persistArrivalTime(etaSeconds: Float) {
        val arrivalAt = System.currentTimeMillis() + (etaSeconds.coerceAtLeast(0f) * 1000).toLong()
        getSharedPreferences("laststop", MODE_PRIVATE).edit().putLong("eta_arrival_at_epoch_ms", arrivalAt).apply()
    }

    private fun formatClockTime(epochMs: Long): String = SimpleDateFormat("h:mm a", Locale.US).format(Date(epochMs))

    private fun onDurationTick() {
        val remainingSeconds = (durationEndAtEpochMs - System.currentTimeMillis()) / 1000f
        checkStageAlerts(remainingSeconds = remainingSeconds, arrivedByDistance = false)
        updateNotification(statusText())
        if (remainingSeconds <= 0f) timerHandler.removeCallbacks(timerTick)
    }

    private fun checkStageAlerts(remainingSeconds: Float, arrivedByDistance: Boolean) {
        if (!reachedAlertSent && (arrivedByDistance || remainingSeconds <= 0f)) {
            reachedAlertSent = true
            fiveMinuteAlertSent = true
            threeMinuteAlertSent = true
            oneMinuteAlertSent = true
            getSharedPreferences("laststop", MODE_PRIVATE).edit().putBoolean("auto_exit_mode", true).apply()
            showStageAlert(Stage.REACHED)
            return
        }
        if (!oneMinuteAlertSent && remainingSeconds in 0f..ONE_MINUTE_SECONDS) {
            oneMinuteAlertSent = true
            threeMinuteAlertSent = true
            fiveMinuteAlertSent = true
            showStageAlert(Stage.ONE_MINUTE)
            return
        }
        if (!threeMinuteAlertSent && remainingSeconds in 0f..THREE_MINUTE_SECONDS) {
            threeMinuteAlertSent = true
            fiveMinuteAlertSent = true
            showStageAlert(Stage.THREE_MINUTES)
            return
        }
        if (!fiveMinuteAlertSent && remainingSeconds in 0f..FIVE_MINUTE_SECONDS) {
            fiveMinuteAlertSent = true
            showStageAlert(Stage.FIVE_MINUTES)
        }
    }

    /**
     * Schedules the next Routes API poll relative to how much trip is left, instead of a flat
     * interval — a 12h bus ride shouldn't poll every few minutes, but the last few minutes before
     * arrival need tight timing for the 5-min/1-min alerts. Falls back to a longer cadence if the
     * ETA hasn't meaningfully improved since the last poll (e.g. stuck in traffic near arrival),
     * so a stalled trip doesn't keep re-polling on the tight near-arrival schedule for nothing —
     * the actual "arrived" alert never depends on this call, it's purely local GPS distance.
     */
    private fun computeNextPollDelaySeconds(remainingSeconds: Float): Float {
        val remaining = remainingSeconds.coerceAtLeast(0f)
        val baseDelay = when {
            remaining > LONG_TRIP_THRESHOLD_SECONDS -> remaining * LONG_TRIP_WAIT_FRACTION
            remaining > MEDIUM_TRIP_THRESHOLD_SECONDS -> remaining - MEDIUM_TRIP_LEAD_SECONDS
            remaining > FIVE_MINUTE_SECONDS -> remaining - FIVE_MINUTE_SECONDS
            remaining > ONE_MINUTE_SECONDS -> remaining - ONE_MINUTE_SECONDS
            else -> STUCK_NEAR_ARRIVAL_POLL_SECONDS
        }.coerceIn(MIN_POLL_INTERVAL_SECONDS, MAX_POLL_INTERVAL_SECONDS)

        val lastRemaining = lastFetchRemainingSeconds
        val stuckNearArrival = remaining <= FIVE_MINUTE_SECONDS &&
            lastRemaining != null &&
            (lastRemaining - remaining) < STUCK_PROGRESS_THRESHOLD_SECONDS
        val scheduledDelay = if (stuckNearArrival) baseDelay.coerceAtLeast(STUCK_NEAR_ARRIVAL_POLL_SECONDS) else baseDelay

        if (consecutiveFetchFailures <= 0) return scheduledDelay
        val retryBackoff = (RETRY_BASE_SECONDS * (1 shl (consecutiveFetchFailures - 1))).coerceAtMost(MAX_RETRY_BACKOFF_SECONDS)
        return minOf(scheduledDelay, retryBackoff)
    }

    private fun distanceToDestination(location: Location): Float {
        val result = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            destinationLatitude,
            destinationLongitude,
            result
        )
        return result[0]
    }

    private fun statusText(distanceMeters: Float? = null, etaSeconds: Float? = null): String {
        if (journeyMode == MODE_DURATION) {
            val remainingMinutes = ((durationEndAtEpochMs - System.currentTimeMillis()) / 60_000f).coerceAtLeast(0f)
            return "${remainingMinutes.toInt()} min remaining — arriving ~${formatClockTime(durationEndAtEpochMs)}"
        }
        if (distanceMeters == null) return "Finding your location…"
        val distanceText = if (distanceMeters >= 1_000f) {
            String.format(Locale.US, "%.1f km away", distanceMeters / 1_000f)
        } else {
            "${distanceMeters.toInt()} m away"
        }
        val arrivalText = etaSeconds?.let { formatClockTime(System.currentTimeMillis() + (it.coerceAtLeast(0f) * 1000).toLong()) }
        return if (arrivalText != null) "$distanceText — arriving ~$arrivalText" else distanceText
    }

    private fun updateNotification(status: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private enum class Stage { FIVE_MINUTES, THREE_MINUTES, ONE_MINUTE, REACHED }

    private fun showStageAlert(stage: Stage) {
        val (title, text) = when (stage) {
            Stage.FIVE_MINUTES -> "5 minutes left" to "Start gathering your belongings."
            Stage.THREE_MINUTES -> "3 minutes left" to "Almost there — get ready."
            Stage.ONE_MINUTE -> "1 minute left" to "Get ready — almost there."
            Stage.REACHED -> "You've arrived" to "Check your belongings before you go."
        }
        val openApp = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_DESTINATION
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        if (stage != Stage.REACHED) {
            val snooze = PendingIntent.getService(
                this,
                4,
                Intent(this, JourneyTrackingService::class.java).apply { action = ACTION_DISMISS_ALERT },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Snooze", snooze)
        }

        notificationManager.notify(RAPID_ALERT_NOTIFICATION_ID, builder.build())
    }

    /** Shown when tracking could not be (re)started without the user opening the app — most
     * often after a device reboot mid-journey. Tapping it opens QuikLook, which restarts
     * tracking from onResume(), where the app is always in an eligible state. */
    private fun notifyTrackingNeedsResume() {
        val openApp = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notificationManager.notify(
            RESUME_NEEDED_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Tap to resume tracking")
                .setContentText("QuikLook stopped tracking your trip. Open the app to pick it back up.")
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun buildNotification(status: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopJourney = PendingIntent.getService(
            this,
            1,
            Intent(this, JourneyTrackingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (journeyMode == MODE_DURATION) "Tracking your trip" else "Tracking to $destinationName"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(status)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Stop journey", stopJourney)
            .build()
    }

    private fun stopTracking() {
        locationClient.removeLocationUpdates(locationCallback)
        timerHandler.removeCallbacks(timerTick)
        getSharedPreferences("laststop", MODE_PRIVATE).edit()
            .putBoolean("journey_active", false)
            .putBoolean("auto_exit_mode", false)
            .remove("eta_arrival_at_epoch_ms")
            .apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_START = "com.laststop.app.action.START_JOURNEY"
        const val ACTION_STOP = "com.laststop.app.action.STOP_JOURNEY"
        const val ACTION_OPEN_DESTINATION = "com.laststop.app.action.OPEN_DESTINATION"
        const val ACTION_TEST_ALERT = "com.laststop.app.action.TEST_MOVEMENT_ALERT"
        const val ACTION_DISMISS_ALERT = "com.laststop.app.action.DISMISS_ALERT"
        const val EXTRA_MODE = "journey_mode"
        const val EXTRA_NAME = "destination_name"
        const val EXTRA_LATITUDE = "destination_latitude"
        const val EXTRA_LONGITUDE = "destination_longitude"
        const val EXTRA_TRAVEL_MODE = "travel_mode"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
        const val MODE_DURATION = "duration"
        const val MODE_DESTINATION = "destination"
        private const val CHANNEL_ID = "active_journey"
        // Renamed from "movement_alerts" — channel sound/vibration settings can't be changed
        // after creation, so a new ID is required to apply the alarm sound on devices that
        // already have the old channel installed.
        private const val ALERT_CHANNEL_ID = "journey_alerts_alarm"
        private const val NOTIFICATION_ID = 1001
        private const val RAPID_ALERT_NOTIFICATION_ID = 1002
        private const val RESUME_NEEDED_NOTIFICATION_ID = 1003
        private const val FAST_SPEED_METERS_PER_SECOND = 8f
        private const val NEAR_DESTINATION_METERS = 1_000f
        private const val ARRIVAL_RADIUS_METERS = 60f
        private const val TICK_INTERVAL_MS = 15_000L
        private const val FIVE_MINUTE_SECONDS = 300f
        private const val THREE_MINUTE_SECONDS = 180f
        private const val ONE_MINUTE_SECONDS = 60f
        private const val LONG_TRIP_THRESHOLD_SECONDS = 4 * 3600f
        private const val LONG_TRIP_WAIT_FRACTION = 0.667f
        private const val MEDIUM_TRIP_THRESHOLD_SECONDS = 1_800f
        private const val MEDIUM_TRIP_LEAD_SECONDS = 90 * 60f
        private const val STUCK_NEAR_ARRIVAL_POLL_SECONDS = 90f
        private const val STUCK_PROGRESS_THRESHOLD_SECONDS = 20f
        private const val MIN_POLL_INTERVAL_SECONDS = 30f
        private const val MAX_POLL_INTERVAL_SECONDS = 3 * 3600f
        private const val RETRY_BASE_SECONDS = 60f
        private const val MAX_RETRY_BACKOFF_SECONDS = 600f
        private const val ROAD_DISTANCE_FACTOR = 1.3f
        private const val TAG = "JourneyTracking"
    }
}
