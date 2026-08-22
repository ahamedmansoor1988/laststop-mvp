package com.laststop.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.annotation.SuppressLint
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.PlaceAutocomplete
import com.google.android.libraries.places.widget.PlaceAutocompleteActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.rabehx.iconsax.Iconsax
import io.github.rabehx.iconsax.outline.SearchNormal
import io.github.rabehx.iconsax.outline.Add
import io.github.rabehx.iconsax.outline.Bag
import io.github.rabehx.iconsax.outline.Building
import io.github.rabehx.iconsax.outline.Headphone
import io.github.rabehx.iconsax.outline.Home
import io.github.rabehx.iconsax.outline.Key
import io.github.rabehx.iconsax.outline.Mobile
import io.github.rabehx.iconsax.outline.Monitor
import io.github.rabehx.iconsax.outline.Setting2
import io.github.rabehx.iconsax.outline.Wallet
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The app runs on the cream ground from the designs; only onboarding is black.
private val Canvas = QuikLook.Cream
private val Ink = QuikLook.Ink
private val Accent = QuikLook.Lime
private val Muted = QuikLook.Muted
private val Card = QuikLook.White
private val DeepInk = QuikLook.Ink

@Composable
private fun quikLookFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    focusedContainerColor = Card,
    unfocusedContainerColor = Card,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Color(0xFF6E6D66),
    focusedLabelColor = Accent,
    unfocusedLabelColor = Muted,
    cursorColor = Accent,
    focusedPlaceholderColor = Muted,
    unfocusedPlaceholderColor = Muted
)

private sealed interface LocationUiState {
    data object PermissionNeeded : LocationUiState
    data object PermissionDenied : LocationUiState
    data object WaitingForFix : LocationUiState
    data class Available(val location: Location) : LocationUiState
}

internal data class Destination(val name: String, val latitude: Double, val longitude: Double)

private data class SavedPlace(val label: String, val destination: Destination)

private enum class UiScreen { Idle, Journey, Settings }

private sealed interface JourneySelection {
    data class Timer(val minutes: Int) : JourneySelection
    data class ToDestination(val destination: Destination, val travelMode: String) : JourneySelection
}

private sealed interface ActiveTarget {
    data class Timer(val endAtEpochMs: Long) : ActiveTarget
    data class ToDestination(val destination: Destination, val travelMode: String) : ActiveTarget
}

private val DEFAULT_BELONGINGS = listOf(
    "Bag", "Phone", "Wallet", "Laptop", "Keys", "Earbuds",
    "Charger", "Power bank", "Water bottle", "Umbrella", "Sunglasses", "Medicines"
)

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationState by mutableStateOf<LocationUiState>(LocationUiState.PermissionNeeded)
    private var recentDestinations by mutableStateOf<List<Destination>>(emptyList())
    private var savedPlaces by mutableStateOf<List<SavedPlace>>(emptyList())
    private var belongingsCatalog by mutableStateOf<List<String>>(emptyList())
    private var journeyActive by mutableStateOf(false)
    private var activeTarget by mutableStateOf<ActiveTarget?>(null)
    private var exitMode by mutableStateOf(false)
    private var uiScreen by mutableStateOf(UiScreen.Idle)
    private var systemLocationEnabled by mutableStateOf(false)
    private var batteryOptimizationIgnored by mutableStateOf(true)
    private var placesAvailable by mutableStateOf(false)
    private var destinationPickerError by mutableStateOf<String?>(null)
    private var onboardingComplete by mutableStateOf(true)
    private var signedInUser by mutableStateOf<SignedInUser?>(null)
    private var userProfile by mutableStateOf(UserProfile())
    private var signInError by mutableStateOf<String?>(null)
    private var pickingHomeForOnboarding = false
    private var hasLocationPermissionState by mutableStateOf(false)
    private lateinit var placesClient: PlacesClient

    private val destinationPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode == PlaceAutocompleteActivity.RESULT_OK) {
            val prediction = PlaceAutocomplete.getPredictionFromIntent(data)
            if (prediction == null) {
                destinationPickerError = "Google did not return a destination. Please try again."
                return@registerForActivityResult
            }
            val sessionToken = PlaceAutocomplete.getSessionTokenFromIntent(data)
            val request = FetchPlaceRequest.builder(
                prediction.placeId,
                listOf(Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION)
            ).setSessionToken(sessionToken).build()
            placesClient.fetchPlace(request)
                .addOnSuccessListener { response ->
                    val place = response.place
                    val location = place.location
                    if (location == null) {
                        destinationPickerError = "Google did not return coordinates for that place."
                        return@addOnSuccessListener
                    }
                    val label = place.displayName?.takeIf { it.isNotBlank() }
                        ?: place.formattedAddress?.takeIf { it.isNotBlank() }
                        ?: prediction.getPrimaryText(null).toString()
                    destinationPickerError = null
                    val picked = Destination(label, location.latitude, location.longitude)
                    if (pickingHomeForOnboarding) {
                        pickingHomeForOnboarding = false
                        addSavedPlace("Home", picked)
                    } else {
                        pickedDestination = picked
                        saveRecentDestination(picked)
                    }
                }
                .addOnFailureListener { error ->
                    destinationPickerError = error.message ?: "Google could not load that destination."
                }
        } else if (result.resultCode != RESULT_CANCELED) {
            destinationPickerError = PlaceAutocomplete.getResultStatusFromIntent(data)?.statusMessage
                ?: "Google destination search failed."
        }
    }

    private var pickedDestination by mutableStateOf<Destination?>(null)

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
        .setMinUpdateIntervalMillis(2_000L)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { locationState = LocationUiState.Available(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val placesApiKey = BuildConfig.PLACES_API_KEY
        if (placesApiKey.isNotBlank() && placesApiKey != "DEFAULT_API_KEY") {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, placesApiKey)
            placesClient = Places.createClient(this)
            placesAvailable = true
        }
        recentDestinations = loadRecentDestinations()
        savedPlaces = loadSavedPlaces()
        belongingsCatalog = loadBelongingsCatalog()
        onboardingComplete = getSharedPreferences("laststop", MODE_PRIVATE).getBoolean("onboarding_complete", false)
        signedInUser = loadSignedInUser()
        userProfile = loadUserProfile()
        refreshJourneyState()
        refreshSystemLocationState()
        refreshPermissionState()
        refreshBatteryOptimizationState()
        handleIncomingIntent(intent)

        setContent {
            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(1_700)
                showSplash = false
            }

            if (showSplash) {
                SplashScreen()
            } else if (!onboardingComplete) {
                OnboardingFlow(
                    signedInUser = signedInUser,
                    onSignIn = ::signInWithGoogle,
                    signInError = signInError,
                    homeDestination = savedPlaces.find { it.label.equals("Home", ignoreCase = true) }?.destination,
                    onOpenDestinationPicker = ::openHomeLocationPicker,
                    placesAvailable = placesAvailable,
                    destinationPickerError = destinationPickerError,
                    hasLocationPermission = hasLocationPermissionState,
                    onRequestLocationPermission = ::requestLocationPermissionForOnboarding,
                    initialProfile = userProfile,
                    onSaveProfile = ::saveUserProfile,
                    onFinish = ::finishOnboarding
                )
            } else {
            LastStopApp(
                locationState = locationState,
                onPermissionResult = { granted ->
                    if (granted) startLocationUpdates() else locationState = LocationUiState.PermissionDenied
                },
                onOpenSettings = ::openAppSettings,
                onOpenLocationSettings = ::openLocationSettings,
                systemLocationEnabled = systemLocationEnabled,
                batteryOptimizationIgnored = batteryOptimizationIgnored,
                onRequestIgnoreBatteryOptimizations = ::requestIgnoreBatteryOptimizations,
                onStartUpdates = ::startLocationUpdates,
                onStopUpdates = ::stopLocationUpdates,
                uiScreen = uiScreen,
                onChangeScreen = { uiScreen = it },
                recentDestinations = recentDestinations,
                pickedDestination = pickedDestination,
                onPickDestination = { pickedDestination = it; saveRecentDestination(it) },
                savedPlaces = savedPlaces,
                onAddSavedPlace = ::addSavedPlace,
                onRemoveSavedPlace = ::removeSavedPlace,
                belongingsCatalog = belongingsCatalog,
                onAddCatalogItem = ::addCatalogItem,
                onRemoveCatalogItem = ::removeCatalogItem,
                onOpenDestinationPicker = ::openDestinationPicker,
                placesAvailable = placesAvailable,
                destinationPickerError = destinationPickerError,
                journeyActive = journeyActive,
                activeTarget = activeTarget,
                exitMode = exitMode,
                onEnterExitMode = { exitMode = true },
                onStartJourney = ::startJourney,
                onStopJourney = ::stopJourney,
                ensureNotificationPermission = ::ensureNotificationPermission,
                signedInUser = signedInUser,
                onSignIn = ::signInWithGoogle,
                onSignOut = ::signOut,
                signInError = signInError,
                onOpenHomeLocationPicker = ::openHomeLocationPicker,
                hasLocationPermission = hasLocationPermissionState
            )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (BuildConfig.DEBUG && intent?.action == JourneyTrackingService.ACTION_TEST_ALERT) {
            startService(Intent(this, JourneyTrackingService::class.java).apply {
                action = JourneyTrackingService.ACTION_TEST_ALERT
            })
        }
        if (BuildConfig.DEBUG && intent?.action == PassiveDetectionService.ACTION_TEST_ALERT) {
            startService(Intent(this, PassiveDetectionService::class.java).apply {
                action = PassiveDetectionService.ACTION_TEST_ALERT
            })
        }
        if (intent?.action == PassiveDetectionService.ACTION_OPEN_TRAVEL_SETUP && !journeyActive) {
            uiScreen = UiScreen.Journey
        }
    }

    override fun onResume() {
        super.onResume()
        refreshJourneyState()
        refreshSystemLocationState()
        refreshPermissionState()
        refreshBatteryOptimizationState()
        if (hasLocationPermission() && systemLocationEnabled) startLocationUpdates()
        if (!journeyActive) startPassiveWatch()
    }

    override fun onPause() {
        stopLocationUpdates()
        super.onPause()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun refreshPermissionState() {
        hasLocationPermissionState = hasLocationPermission()
        if (!hasLocationPermissionState) locationState = LocationUiState.PermissionNeeded
    }

    private val onboardingPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true || results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermissionState = granted
        if (granted) {
            locationState = LocationUiState.WaitingForFix
            startLocationUpdates()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ensureNotificationPermission()
        } else {
            locationState = LocationUiState.PermissionDenied
        }
    }

    private fun requestLocationPermissionForOnboarding() {
        onboardingPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    private fun startLocationUpdates() {
        refreshSystemLocationState()
        if (!systemLocationEnabled) {
            locationState = LocationUiState.WaitingForFix
            return
        }
        if (!hasLocationPermission()) {
            locationState = LocationUiState.PermissionNeeded
            return
        }

        locationState = LocationUiState.WaitingForFix
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) locationState = LocationUiState.Available(location)
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized) fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }

    private fun openLocationSettings() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun refreshSystemLocationState() {
        val manager = getSystemService(LocationManager::class.java)
        systemLocationEnabled = manager?.isLocationEnabled == true
    }

    private fun refreshBatteryOptimizationState() {
        val powerManager = getSystemService(PowerManager::class.java)
        batteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(packageName) == true
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        }
    }

    private fun openDestinationPicker() {
        if (!placesAvailable) return
        pickingHomeForOnboarding = false
        destinationPickerError = null
        destinationPicker.launch(PlaceAutocomplete.createIntent(this) {
            setCountries(listOf("in"))
        })
    }

    private fun openHomeLocationPicker() {
        if (!placesAvailable) return
        pickingHomeForOnboarding = true
        destinationPickerError = null
        destinationPicker.launch(PlaceAutocomplete.createIntent(this) {
            setCountries(listOf("in"))
        })
    }

    private fun signInWithGoogle() {
        signInError = null
        lifecycleScope.launch {
            when (val result = GoogleAuth.signIn(this@MainActivity)) {
                is SignInResult.Success -> {
                    signedInUser = result.user
                    saveSignedInUser(result.user)
                }
                is SignInResult.Cancelled -> Unit
                is SignInResult.Failed -> signInError = result.message
            }
        }
    }

    private fun loadSignedInUser(): SignedInUser? {
        val prefs = getSharedPreferences("laststop", MODE_PRIVATE)
        val name = prefs.getString("user_display_name", null) ?: return null
        val email = prefs.getString("user_email", "") ?: ""
        val photo = prefs.getString("user_photo_url", null)
        return SignedInUser(name, email, photo)
    }

    private fun saveSignedInUser(user: SignedInUser) {
        getSharedPreferences("laststop", MODE_PRIVATE).edit()
            .putString("user_display_name", user.displayName)
            .putString("user_email", user.email)
            .putString("user_photo_url", user.photoUrl)
            .apply()
    }

    private fun loadUserProfile(): UserProfile {
        val prefs = getSharedPreferences("laststop", MODE_PRIVATE)
        return UserProfile(
            name = prefs.getString("profile_name", "") ?: "",
            age = prefs.getString("profile_age", "") ?: "",
            gender = prefs.getString("profile_gender", "") ?: "",
            phone = prefs.getString("profile_phone", "") ?: ""
        )
    }

    private fun saveUserProfile(profile: UserProfile) {
        userProfile = profile
        getSharedPreferences("laststop", MODE_PRIVATE).edit()
            .putString("profile_name", profile.name.trim())
            .putString("profile_age", profile.age.trim())
            .putString("profile_gender", profile.gender.trim())
            .putString("profile_phone", profile.phone.trim())
            .apply()
    }

    private fun signOut() {
        signedInUser = null
        getSharedPreferences("laststop", MODE_PRIVATE).edit()
            .remove("user_display_name")
            .remove("user_email")
            .remove("user_photo_url")
            .apply()
    }

    private fun finishOnboarding() {
        onboardingComplete = true
        getSharedPreferences("laststop", MODE_PRIVATE).edit().putBoolean("onboarding_complete", true).apply()
        startPassiveWatch()
    }

    private fun loadDestination(): Destination? {
        val preferences = getSharedPreferences("laststop", MODE_PRIVATE)
        if (!preferences.contains("destination_latitude")) return null
        return Destination(
            name = preferences.getString("destination_name", "Destination") ?: "Destination",
            latitude = preferences.getString("destination_latitude", null)?.toDoubleOrNull() ?: return null,
            longitude = preferences.getString("destination_longitude", null)?.toDoubleOrNull() ?: return null
        )
    }

    private fun saveRecentDestination(value: Destination) {
        recentDestinations = (listOf(value) + recentDestinations)
            .distinctBy { "${it.latitude},${it.longitude}" }
            .take(5)
        saveRecentDestinations(recentDestinations)
    }

    private fun loadRecentDestinations(): List<Destination> {
        val raw = getSharedPreferences("laststop", MODE_PRIVATE)
            .getString("recent_destinations", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(Destination(item.getString("name"), item.getDouble("latitude"), item.getDouble("longitude")))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveRecentDestinations(values: List<Destination>) {
        val array = JSONArray()
        values.forEach { value ->
            array.put(JSONObject().apply {
                put("name", value.name)
                put("latitude", value.latitude)
                put("longitude", value.longitude)
            })
        }
        getSharedPreferences("laststop", MODE_PRIVATE).edit()
            .putString("recent_destinations", array.toString())
            .apply()
    }

    private fun loadSavedPlaces(): List<SavedPlace> {
        val raw = getSharedPreferences("laststop", MODE_PRIVATE).getString("saved_places", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(SavedPlace(
                        item.getString("label"),
                        Destination(item.getString("name"), item.getDouble("latitude"), item.getDouble("longitude"))
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveSavedPlacesList(values: List<SavedPlace>) {
        val array = JSONArray()
        values.forEach { value ->
            array.put(JSONObject().apply {
                put("label", value.label)
                put("name", value.destination.name)
                put("latitude", value.destination.latitude)
                put("longitude", value.destination.longitude)
            })
        }
        getSharedPreferences("laststop", MODE_PRIVATE).edit()
            .putString("saved_places", array.toString())
            .apply()
    }

    private fun addSavedPlace(label: String, destination: Destination) {
        savedPlaces = savedPlaces.filterNot { it.label.equals(label, ignoreCase = true) } + SavedPlace(label, destination)
        saveSavedPlacesList(savedPlaces)
    }

    private fun removeSavedPlace(label: String) {
        savedPlaces = savedPlaces.filterNot { it.label.equals(label, ignoreCase = true) }
        saveSavedPlacesList(savedPlaces)
    }

    private fun loadBelongingsCatalog(): List<String> {
        val raw = getSharedPreferences("laststop", MODE_PRIVATE).getString("belongings_catalog", null)
            ?: return DEFAULT_BELONGINGS.also { saveBelongingsCatalog(it) }
        return runCatching {
            val array = JSONArray(raw)
            buildList { for (index in 0 until array.length()) add(array.getString(index)) }
        }.getOrDefault(DEFAULT_BELONGINGS)
    }

    private fun saveBelongingsCatalog(items: List<String>) {
        val array = JSONArray()
        items.forEach { array.put(it) }
        getSharedPreferences("laststop", MODE_PRIVATE).edit()
            .putString("belongings_catalog", array.toString())
            .apply()
    }

    private fun addCatalogItem(name: String) {
        if (belongingsCatalog.any { it.equals(name, ignoreCase = true) }) return
        belongingsCatalog = belongingsCatalog + name
        saveBelongingsCatalog(belongingsCatalog)
    }

    private fun removeCatalogItem(name: String) {
        belongingsCatalog = belongingsCatalog - name
        saveBelongingsCatalog(belongingsCatalog)
    }

    private fun refreshJourneyState() {
        val prefs = getSharedPreferences("laststop", MODE_PRIVATE)
        journeyActive = prefs.getBoolean("journey_active", false)
        activeTarget = if (!journeyActive) null else when (prefs.getString("journey_mode", JourneyTrackingService.MODE_DESTINATION)) {
            JourneyTrackingService.MODE_DURATION -> ActiveTarget.Timer(prefs.getLong("duration_end_at", System.currentTimeMillis()))
            else -> loadDestination()?.let { ActiveTarget.ToDestination(it, prefs.getString("travel_mode", "Bike") ?: "Bike") }
        }
        if (prefs.getBoolean("auto_exit_mode", false)) {
            exitMode = true
            prefs.edit().putBoolean("auto_exit_mode", false).apply()
        }
        if (!journeyActive) exitMode = false
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
    }

    private fun startPassiveWatch() {
        if (journeyActive) return
        if (!hasLocationPermission() || !systemLocationEnabled) return
        ContextCompat.startForegroundService(this, Intent(this, PassiveDetectionService::class.java).apply {
            action = PassiveDetectionService.ACTION_START
        })
    }

    private fun stopPassiveWatch() {
        startService(Intent(this, PassiveDetectionService::class.java).apply {
            action = PassiveDetectionService.ACTION_STOP
        })
    }

    private fun startJourney(selection: JourneySelection) {
        stopPassiveWatch()
        val intent = Intent(this, JourneyTrackingService::class.java).apply {
            action = JourneyTrackingService.ACTION_START
            when (selection) {
                is JourneySelection.Timer -> {
                    putExtra(JourneyTrackingService.EXTRA_MODE, JourneyTrackingService.MODE_DURATION)
                    putExtra(JourneyTrackingService.EXTRA_DURATION_MINUTES, selection.minutes)
                }
                is JourneySelection.ToDestination -> {
                    putExtra(JourneyTrackingService.EXTRA_MODE, JourneyTrackingService.MODE_DESTINATION)
                    putExtra(JourneyTrackingService.EXTRA_NAME, selection.destination.name)
                    putExtra(JourneyTrackingService.EXTRA_LATITUDE, selection.destination.latitude)
                    putExtra(JourneyTrackingService.EXTRA_LONGITUDE, selection.destination.longitude)
                    putExtra(JourneyTrackingService.EXTRA_TRAVEL_MODE, selection.travelMode)
                }
            }
        }
        ContextCompat.startForegroundService(this, intent)
        journeyActive = true
        exitMode = false
        activeTarget = when (selection) {
            is JourneySelection.Timer -> ActiveTarget.Timer(System.currentTimeMillis() + selection.minutes * 60_000L)
            is JourneySelection.ToDestination -> ActiveTarget.ToDestination(selection.destination, selection.travelMode)
        }
        uiScreen = UiScreen.Idle
        pickedDestination = null
        getSharedPreferences("laststop", MODE_PRIVATE).edit().putBoolean("journey_active", true).apply()
    }

    private fun stopJourney() {
        startService(Intent(this, JourneyTrackingService::class.java).apply {
            action = JourneyTrackingService.ACTION_STOP
        })
        journeyActive = false
        activeTarget = null
        exitMode = false
        getSharedPreferences("laststop", MODE_PRIVATE).edit().putBoolean("journey_active", false).apply()
        startPassiveWatch()
    }
}

@Composable
private fun LastStopApp(
    locationState: LocationUiState,
    onPermissionResult: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    systemLocationEnabled: Boolean,
    batteryOptimizationIgnored: Boolean,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onStartUpdates: () -> Unit,
    onStopUpdates: () -> Unit,
    uiScreen: UiScreen,
    onChangeScreen: (UiScreen) -> Unit,
    recentDestinations: List<Destination>,
    pickedDestination: Destination?,
    onPickDestination: (Destination) -> Unit,
    savedPlaces: List<SavedPlace>,
    onAddSavedPlace: (String, Destination) -> Unit,
    onRemoveSavedPlace: (String) -> Unit,
    onOpenDestinationPicker: () -> Unit,
    placesAvailable: Boolean,
    destinationPickerError: String?,
    belongingsCatalog: List<String>,
    onAddCatalogItem: (String) -> Unit,
    onRemoveCatalogItem: (String) -> Unit,
    journeyActive: Boolean,
    activeTarget: ActiveTarget?,
    exitMode: Boolean,
    onEnterExitMode: () -> Unit,
    onStartJourney: (JourneySelection) -> Unit,
    onStopJourney: () -> Unit,
    ensureNotificationPermission: () -> Unit,
    signedInUser: SignedInUser?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    signInError: String?,
    onOpenHomeLocationPicker: () -> Unit,
    hasLocationPermission: Boolean
) {
    val context = LocalContext.current
    var permissionWasRequested by remember { mutableStateOf(false) }
    var pendingSelection by remember { mutableStateOf<JourneySelection?>(null) }
    var selectedItems by remember { mutableStateOf(setOf("Bag", "Phone", "Wallet")) }
    var travelMode by remember { mutableStateOf("Bike") }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        onPermissionResult(granted)
        if (granted) {
            pendingSelection?.let(onStartJourney)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        pendingSelection = null
    }

    DisposableEffect(Unit) {
        onStartUpdates()
        onDispose(onStopUpdates)
    }

    val permanentlyDenied = permissionWasRequested &&
        locationState is LocationUiState.PermissionDenied &&
        !ActivityCompat.shouldShowRequestPermissionRationale(context as ComponentActivity, Manifest.permission.ACCESS_FINE_LOCATION)

    fun beginJourney(selection: JourneySelection) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            ensureNotificationPermission()
            onStartJourney(selection)
        } else if (permanentlyDenied) {
            onOpenSettings()
        } else {
            pendingSelection = selection
            permissionWasRequested = true
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    MaterialTheme(typography = QuikLookTypography) {
        Surface(color = Canvas, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Canvas)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("QUIKLOOK", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (!journeyActive) {
                        TextButton(onClick = { onChangeScreen(UiScreen.Settings) }) {
                            Icon(Iconsax.Outline.Setting2, contentDescription = null, tint = Muted, modifier = Modifier.height(18.dp).width(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Settings", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                if (!systemLocationEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFDE7EB), RoundedCornerShape(24.dp))
                            .padding(18.dp)
                    ) {
                        Text("LOCATION IS OFF", color = Color(0xFFC20031), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text("Turn on Location so QuikLook can notice when you're traveling.", color = Ink, fontSize = 15.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onOpenLocationSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DeepInk)
                        ) { Text("Turn on location", fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                if (!batteryOptimizationIgnored) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE9F6EC), RoundedCornerShape(24.dp))
                            .padding(18.dp)
                    ) {
                        Text("BATTERY OPTIMIZATION IS ON", color = Color(0xFF1F7F45), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Your phone may stop QuikLook's background watcher to save power. Allow it to run unrestricted so travel detection keeps working.",
                            color = Ink,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onRequestIgnoreBatteryOptimizations,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DeepInk)
                        ) { Text("Allow background activity", fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                when {
                    journeyActive && activeTarget != null -> ActiveJourneyScreen(
                        location = (locationState as? LocationUiState.Available)?.location,
                        target = activeTarget,
                        selectedItems = selectedItems,
                        exitMode = exitMode,
                        onEnterExitMode = onEnterExitMode,
                        onStop = onStopJourney
                    )
                    uiScreen == UiScreen.Journey -> JourneyScreen(
                        catalog = belongingsCatalog,
                        selectedItems = selectedItems,
                        onToggleItem = { item ->
                            selectedItems = if (item in selectedItems) selectedItems - item else selectedItems + item
                        },
                        onAddItem = { name ->
                            val trimmed = name.trim()
                            if (trimmed.isNotEmpty()) {
                                onAddCatalogItem(trimmed)
                                selectedItems = selectedItems + trimmed
                            }
                        },
                        onRemoveItem = { item ->
                            onRemoveCatalogItem(item)
                            selectedItems = selectedItems - item
                        },
                        recentDestinations = recentDestinations,
                        pickedDestination = pickedDestination,
                        onPickDestination = onPickDestination,
                        savedPlaces = savedPlaces,
                        onAddSavedPlace = onAddSavedPlace,
                        onRemoveSavedPlace = onRemoveSavedPlace,
                        onOpenDestinationPicker = onOpenDestinationPicker,
                        placesAvailable = placesAvailable,
                        destinationPickerError = destinationPickerError,
                        travelMode = travelMode,
                        onSelectMode = { travelMode = it },
                        onStartTimer = { minutes -> beginJourney(JourneySelection.Timer(minutes)) },
                        onStartToDestination = { destination -> beginJourney(JourneySelection.ToDestination(destination, travelMode)) },
                        onCancel = { onChangeScreen(UiScreen.Idle) }
                    )
                    uiScreen == UiScreen.Settings -> SettingsScreen(
                        signedInUser = signedInUser,
                        onSignIn = onSignIn,
                        onSignOut = onSignOut,
                        signInError = signInError,
                        homeDestination = savedPlaces.find { it.label.equals("Home", ignoreCase = true) }?.destination,
                        onOpenHomeLocationPicker = onOpenHomeLocationPicker,
                        placesAvailable = placesAvailable,
                        destinationPickerError = destinationPickerError,
                        hasLocationPermission = hasLocationPermission,
                        systemLocationEnabled = systemLocationEnabled,
                        batteryOptimizationIgnored = batteryOptimizationIgnored,
                        onOpenAppSettings = onOpenSettings,
                        onOpenLocationSettings = onOpenLocationSettings,
                        onRequestIgnoreBatteryOptimizations = onRequestIgnoreBatteryOptimizations,
                        onBack = { onChangeScreen(UiScreen.Idle) }
                    )
                    else -> IdleScreen(
                        onStartNow = { onChangeScreen(UiScreen.Journey) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun IdleScreen(onStartNow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Text("Are you traveling?", color = Ink, fontFamily = TitleFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text("Set active reminders for your next destination.", color = Color(0xFFB5C0C6), fontFamily = BodyFontFamily, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onStartNow,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DeepInk),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                modifier = Modifier.height(40.dp)
            ) { Text("Yes, set trip", fontWeight = FontWeight.Black) }
            TextButton(onClick = {}, modifier = Modifier.height(40.dp)) {
                Text("Not now", color = Color(0xFFB5C0C6), fontWeight = FontWeight.Bold)
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(50))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DashboardTab("Destination", true, onStartNow, Modifier.weight(1f))
        DashboardTab("Timer", false, onStartNow, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(18.dp))
            .clickable(onClick = onStartNow)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Iconsax.Outline.SearchNormal, contentDescription = null, tint = Muted, modifier = Modifier.height(20.dp).width(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Where are you going?", color = Muted, fontFamily = BodyFontFamily, fontSize = 14.sp)
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DashboardChip("Home", onStartNow, icon = Iconsax.Outline.Home)
        DashboardChip("Office", onStartNow, icon = Iconsax.Outline.Building)
        DashboardChip("Add place", onStartNow, icon = Iconsax.Outline.Add)
    }
    Spacer(Modifier.height(22.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("BELONGINGS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Edit list", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onStartNow))
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DashboardChip("Bag", onStartNow, selected = true, icon = Iconsax.Outline.Bag)
        DashboardChip("Phone", onStartNow, selected = true, icon = Iconsax.Outline.Mobile)
        DashboardChip("Wallet", onStartNow, selected = true, icon = Iconsax.Outline.Wallet)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DashboardChip("Laptop", onStartNow, icon = Iconsax.Outline.Monitor)
        DashboardChip("Keys", onStartNow, selected = true, icon = Iconsax.Outline.Key)
        DashboardChip("Earbuds", onStartNow, icon = Iconsax.Outline.Headphone)
    }
}

@Composable
private fun DashboardTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        label,
        color = if (selected) DeepInk else Muted,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .background(if (selected) Accent else Color.Transparent, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    )
}

@Composable
private fun DashboardChip(label: String, onClick: () -> Unit, selected: Boolean = false, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .background(if (selected) Accent else Card, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = if (selected) DeepInk else Ink, modifier = Modifier.size(16.dp))
        }
        Text(label, color = if (selected) DeepInk else Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsScreen(
    signedInUser: SignedInUser?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    signInError: String?,
    homeDestination: Destination?,
    onOpenHomeLocationPicker: () -> Unit,
    placesAvailable: Boolean,
    destinationPickerError: String?,
    hasLocationPermission: Boolean,
    systemLocationEnabled: Boolean,
    batteryOptimizationIgnored: Boolean,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onBack: () -> Unit
) {
    Text("Settings", color = Ink, fontSize = 38.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(20.dp))

    Column(modifier = Modifier.fillMaxWidth().background(Card, RoundedCornerShape(24.dp)).padding(20.dp)) {
        Text("ACCOUNT", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (signedInUser != null) {
            Text(signedInUser.displayName, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(signedInUser.email, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onSignOut) { Text("Sign out", color = Color(0xFFFF3964), fontWeight = FontWeight.Bold) }
        } else {
            Text("Not signed in", color = Ink, fontSize = 16.sp)
            signInError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Color(0xFFFF3964), fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DeepInk)
            ) { Text("Sign in with Google", fontWeight = FontWeight.Bold) }
        }
    }
    Spacer(Modifier.height(16.dp))

    Column(modifier = Modifier.fillMaxWidth().background(Card, RoundedCornerShape(24.dp)).padding(20.dp)) {
        Text("HOME LOCATION", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(homeDestination?.name ?: "Not set", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        destinationPickerError?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Color(0xFFFF3964), fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpenHomeLocationPicker,
            enabled = placesAvailable,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DeepInk)
        ) { Text(if (homeDestination != null) "Change home address" else "Set home address", fontWeight = FontWeight.Bold) }
    }
    Spacer(Modifier.height(16.dp))

    Column(modifier = Modifier.fillMaxWidth().background(Card, RoundedCornerShape(24.dp)).padding(20.dp)) {
        Text("PERMISSIONS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Location access", color = Ink, fontSize = 15.sp)
            Text(
                if (hasLocationPermission) "Granted" else "Not granted",
                color = if (hasLocationPermission) Color(0xFF8BEF95) else Color(0xFFFF3964),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        if (!hasLocationPermission) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onOpenAppSettings,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DeepInk)
            ) { Text("Open app settings", fontWeight = FontWeight.Bold) }
        }
        if (!systemLocationEnabled) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onOpenLocationSettings,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DeepInk)
            ) { Text("Turn on location", fontWeight = FontWeight.Bold) }
        }
        if (!batteryOptimizationIgnored) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onRequestIgnoreBatteryOptimizations,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DeepInk)
            ) { Text("Allow background activity", fontWeight = FontWeight.Bold) }
        }
    }

    Spacer(Modifier.height(16.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done", color = Muted, fontWeight = FontWeight.Bold) }
}

@Composable
private fun JourneyScreen(
    catalog: List<String>,
    selectedItems: Set<String>,
    onToggleItem: (String) -> Unit,
    onAddItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    recentDestinations: List<Destination>,
    pickedDestination: Destination?,
    onPickDestination: (Destination) -> Unit,
    savedPlaces: List<SavedPlace>,
    onAddSavedPlace: (String, Destination) -> Unit,
    onRemoveSavedPlace: (String) -> Unit,
    onOpenDestinationPicker: () -> Unit,
    placesAvailable: Boolean,
    destinationPickerError: String?,
    travelMode: String,
    onSelectMode: (String) -> Unit,
    onStartTimer: (Int) -> Unit,
    onStartToDestination: (Destination) -> Unit,
    onCancel: () -> Unit
) {
    var useDestination by remember { mutableStateOf(true) }
    var showCarry by remember { mutableStateOf(false) }
    var minutes by remember { mutableStateOf(15) }

    // Design 8 — the carry list, shown once a trip is configured.
    if (showCarry) {
        CarrySelection(
            catalog = catalog,
            selectedItems = selectedItems,
            onToggleItem = onToggleItem,
            onAddItem = onAddItem,
            onRemoveItem = onRemoveItem,
            onBack = { showCarry = false },
            onContinue = {
                if (useDestination) pickedDestination?.let(onStartToDestination) else onStartTimer(minutes)
            },
            canContinue = if (useDestination) pickedDestination != null else minutes > 0
        )
        return
    }

    // ---- Design 4 / 5: segmented control, then either destination or timer setup ----
    SegmentedTabs(
        useDestination = useDestination,
        onSelect = { useDestination = it }
    )
    Spacer(Modifier.height(16.dp))

    if (useDestination) {
        // Search field — white, 43dp tall, 11.5dp corners.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Card, RoundedCornerShape(14.dp))
                .then(if (placesAvailable) Modifier.clickable(onClick = onOpenDestinationPicker) else Modifier)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(R.drawable.ic_glass), null, tint = Muted, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(11.dp))
            Text(
                pickedDestination?.name ?: "Where are you going?",
                color = if (pickedDestination != null) Ink else Muted,
                fontFamily = BodyFontFamily,
                fontSize = 15.sp,
                maxLines = 1
            )
        }
        destinationPickerError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = QuikLook.Danger, fontFamily = BodyFontFamily, fontSize = 13.sp)
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("Saved location")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            savedPlaces.take(2).forEach { place ->
                val selected = pickedDestination?.name == place.destination.name
                PlaceChip(
                    label = place.label,
                    iconRes = if (place.label.equals("Home", true)) R.drawable.ic_home else R.drawable.ic_building,
                    selected = selected,
                    onClick = { onPickDestination(place.destination) }
                )
            }
            AddPlaceChip(enabled = placesAvailable, onClick = onOpenDestinationPicker)
        }

        Spacer(Modifier.height(26.dp))
        SectionLabel("Mode of travel")
        Spacer(Modifier.height(12.dp))
        val modes = listOf(
            "Bike" to R.drawable.ic_theta_theta,
            "Walk" to R.drawable.ic_user,
            "Bus" to R.drawable.ic_bus,
            "Car" to R.drawable.ic_car,
            "Flight" to R.drawable.ic_airplane,
            "Train" to R.drawable.ic_building
        )
        modes.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { (label, icon) ->
                    ModeTile(label, icon, travelMode == label, { onSelectMode(label) }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(9.dp))
        }
    } else {
        SectionLabel("Quick presets")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(5, 10, 15, 30).forEach { preset ->
                PresetChip(preset, minutes == preset, { minutes = preset }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
        TimerFace(totalSeconds = minutes * 60L, remainingSeconds = minutes * 60L)
        Spacer(Modifier.height(14.dp))
        Text(
            "Tap a quick preset above to set your countdown",
            color = Muted,
            fontFamily = BodyFontFamily,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(Modifier.height(30.dp))
    Button(
        onClick = { showCarry = true },
        enabled = if (useDestination) pickedDestination != null else minutes > 0,
        modifier = Modifier.fillMaxWidth(0.92f).height(70.dp),
        shape = RoundedCornerShape(35.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink, contentColor = Color.White,
            disabledContainerColor = QuikLook.Surface, disabledContentColor = Muted
        )
    ) { Text("Start journey", fontFamily = TitleFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    Spacer(Modifier.height(10.dp))
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel", color = Muted, fontFamily = BodyFontFamily)
    }
}

/**
 * The countdown face from design 5: a squircle carrying a radial navy-to-blue fill and a white
 * progress ring. Proportions are taken from the source — ring radius 130 and stroke 11 on a 328
 * card — so it scales correctly on any screen width.
 */
@Composable
private fun TimerFace(totalSeconds: Long, remainingSeconds: Long, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(percent = 18))
            .background(Brush.radialGradient(listOf(Color(0xFF072766), Color(0xFF2B8CFF)))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.width * (11f / 328f)
            val radius = size.width * (130f / 328f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            drawArc(
                color = Color.White.copy(alpha = 0.3f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            val fraction = if (totalSeconds > 0L) {
                (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
            } else 1f
            drawArc(
                color = Color.White,
                startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            String.format(
                Locale.US, "%02d : %02d : %02d",
                remainingSeconds / 3600, (remainingSeconds % 3600) / 60, remainingSeconds % 60
            ),
            color = Color.White,
            fontFamily = TitleFontFamily,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(text, color = Ink, fontFamily = TitleFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Bold)

/** Destination | Timer — a #EEEDE7 track with a white pill on the selected half. */
@Composable
private fun SegmentedTabs(useDestination: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(QuikLook.Surface, RoundedCornerShape(26.dp))
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(true to "Destination", false to "Timer").forEach { (isDest, label) ->
            val on = useDestination == isDest
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(21.dp))
                    .background(if (on) Card else Color.Transparent)
                    .clickable { onSelect(isDest) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(if (isDest) R.drawable.ic_location_add else R.drawable.ic_timer),
                    null,
                    tint = if (on) Ink else Muted,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    label,
                    color = if (on) Ink else Muted,
                    fontFamily = TitleFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PlaceChip(label: String, iconRes: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Ink else QuikLook.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(iconRes), null, tint = if (selected) Accent else Ink, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (selected) Accent else Ink, fontFamily = BodyFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddPlaceChip(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_add), null, tint = Ink, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text("Add place", color = Ink, fontFamily = BodyFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** 159x99 tile at 11.5dp corners: icon over label, dark + lime when selected. */
@Composable
private fun ModeTile(label: String, iconRes: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(99.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Ink else QuikLook.Surface)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painterResource(iconRes), null, tint = if (selected) Accent else Ink, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, color = if (selected) Accent else Ink, fontFamily = BodyFontFamily, fontSize = 14.sp)
    }
}

@Composable
private fun PresetChip(minutes: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Ink else QuikLook.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("$minutes min", color = if (selected) Accent else Ink, fontFamily = BodyFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Design 8 — "Select what you carry". */
@Composable
private fun CarrySelection(
    catalog: List<String>,
    selectedItems: Set<String>,
    onToggleItem: (String) -> Unit,
    onAddItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    canContinue: Boolean
) {
    var query by remember { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.radialGradient(listOf(QuikLook.GreenLight, QuikLook.GreenDark))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(22.dp)) {
            Text("Are you\ntraveling?", color = Color.White, fontFamily = TitleFontFamily,
                fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text("Set active reminders for your next destination.", color = Color.White.copy(alpha = 0.9f),
                fontFamily = BodyFontFamily, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
    Spacer(Modifier.height(24.dp))
    SectionLabel("Select what you carry")
    Spacer(Modifier.height(14.dp))
    catalog.chunked(2).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { item ->
                CarryChip(item, item in selectedItems, { onToggleItem(item) }, Modifier.weight(1f))
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
    }
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Card, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_add), null, tint = Muted, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) { PlainField(query, { query = it }, "Add an item") }
        if (query.isNotBlank()) {
            TextButton(onClick = { onAddItem(query.trim()); query = "" }) {
                Text("Add", color = Ink, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(28.dp))
    Button(
        onClick = onContinue,
        enabled = canContinue,
        modifier = Modifier.fillMaxWidth(0.92f).height(70.dp),
        shape = RoundedCornerShape(35.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink, contentColor = Color.White,
            disabledContainerColor = QuikLook.Surface, disabledContentColor = Muted
        )
    ) { Text("Continue", fontFamily = TitleFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text("Back", color = Muted, fontFamily = BodyFontFamily)
    }
}

@Composable
private fun CarryChip(item: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Ink else QuikLook.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(belongingIcon(item)), null, tint = if (selected) Accent else Ink, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        Text(item, color = if (selected) Accent else Ink, fontFamily = BodyFontFamily, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun TimeStepper(
    label: String,
    value: Int,
    displayValue: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.background(Card, RoundedCornerShape(18.dp)).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.background(Canvas, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StepperGlyph("–", enabled = value > 0, onClick = onDecrement)
            Text(displayValue, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
            StepperGlyph("+", enabled = true, onClick = onIncrement)
        }
    }
}

@Composable
private fun StepperGlyph(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(32.dp).width(36.dp),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = DeepInk,
            disabledContainerColor = QuikLook.Surface,
            disabledContentColor = Muted
        )
    ) { Text(symbol, fontSize = 16.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Accent else Card,
            contentColor = if (selected) DeepInk else Ink
        )
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

/** Maps a belongings label to one of the supplied Iconsax drawables. */
internal fun belongingIcon(item: String): Int = when (item.lowercase(Locale.US)) {
    "bag", "handbag" -> R.drawable.ic_bag
    "phone", "mobile" -> R.drawable.ic_mobile
    "wallet" -> R.drawable.ic_empty_wallet
    "keys", "key" -> R.drawable.ic_key
    "earbuds", "headphones" -> R.drawable.ic_headphones
    "sunglasses", "glasses" -> R.drawable.ic_glass
    "laptop" -> R.drawable.ic_keyboard_open
    "charger", "power bank" -> R.drawable.ic_battery_charging
    "water bottle" -> R.drawable.ic_theta_theta
    "umbrella" -> R.drawable.ic_ticket_star
    "medicines", "medicine" -> R.drawable.ic_book_open
    "kids" -> R.drawable.ic_profile_2user
    "passport", "boarding pass" -> R.drawable.ic_ticket_star
    "watch" -> R.drawable.ic_watch
    "books" -> R.drawable.ic_book_open
    else -> R.drawable.ic_shopping_bag
}

/** A dark pill carrying a lime icon and label — the recurring item chip in the designs. */
@Composable
private fun ItemChip(
    item: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .background(Ink, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(belongingIcon(item)), null, tint = Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Text(item, color = Accent, fontFamily = BodyFontFamily, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
        if (trailing != null) {
            Spacer(Modifier.width(9.dp))
            trailing()
        }
    }
}

@Composable
private fun ActiveJourneyScreen(
    location: Location?,
    target: ActiveTarget,
    selectedItems: Set<String>,
    exitMode: Boolean,
    onEnterExitMode: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    var checkedItems by remember { mutableStateOf(emptySet<String>()) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick++
        }
    }

    val distance = (target as? ActiveTarget.ToDestination)?.let { toDestination ->
        location?.let {
            val result = FloatArray(1)
            Location.distanceBetween(it.latitude, it.longitude, toDestination.destination.latitude, toDestination.destination.longitude, result)
            result[0]
        }
    }
    val remainingSeconds = if (target is ActiveTarget.Timer) {
        ((target.endAtEpochMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L).also { tick }
    } else null
    val prefs = context.getSharedPreferences("laststop", Context.MODE_PRIVATE)
    val arrivalAtEpochMs = remember(tick) { prefs.getLong("eta_arrival_at_epoch_ms", 0L) }
    val transport = remember(tick) { prefs.getString("travel_mode", "Bike") ?: "Bike" }

    if (exitMode) {
        ExitChecklist(
            items = selectedItems.toList(),
            checkedItems = checkedItems,
            onToggle = { item ->
                checkedItems = if (item in checkedItems) checkedItems - item else checkedItems + item
            },
            onDone = onStop
        )
        return
    }

    // Header strip — journey state and the headline number, per the design.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Accent, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(Ink, RoundedCornerShape(50)))
        Spacer(Modifier.width(9.dp))
        Text("JOURNEY ACTIVE", color = Ink, fontFamily = TitleFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(
            when {
                distance != null -> "${formatDistance(distance)} left"
                remainingSeconds != null -> "${formatCountdown(remainingSeconds)} left"
                else -> ""
            },
            color = Ink, fontFamily = BodyFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
    }
    Spacer(Modifier.height(16.dp))

    if (target is ActiveTarget.Timer && remainingSeconds != null) {
        // The service already records the chosen length, so the ring can show elapsed
        // progress without adding a start timestamp to the model.
        val total = (prefs.getInt("duration_minutes", 0) * 60L).coerceAtLeast(remainingSeconds)
        TimerFace(totalSeconds = total, remainingSeconds = remainingSeconds)
        Spacer(Modifier.height(16.dp))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard("ETA", arrivalAtEpochMs.takeIf { it > 0L }?.let(::formatClockTime) ?: "—", Modifier.weight(1f))
        StatCard(
            "Remaining",
            when {
                remainingSeconds != null -> formatCountdown(remainingSeconds)
                distance != null -> formatDistance(distance)
                else -> "—"
            },
            Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard("Transport", transport, Modifier.weight(1f))
        StatCard("Destination", (target as? ActiveTarget.ToDestination)?.destination?.name ?: "Timer", Modifier.weight(1f))
    }
    Spacer(Modifier.height(16.dp))

    AlertProgress(
        stage = when {
            remainingSeconds != null && remainingSeconds <= 60 -> 2
            distance != null && distance <= 500f -> 2
            remainingSeconds != null && remainingSeconds <= 300 -> 1
            distance != null && distance <= 2_000f -> 1
            else -> 0
        }
    )
    Spacer(Modifier.height(16.dp))

    Column(modifier = Modifier.fillMaxWidth().background(Card, RoundedCornerShape(22.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ACTIVE LIST", color = Muted, fontFamily = BodyFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${selectedItems.size} items", color = Ink, fontFamily = TitleFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        selectedItems.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item -> ItemChip(item, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    Spacer(Modifier.height(20.dp))

    Button(
        onClick = onEnterExitMode,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Accent)
    ) { Text("Check my things", fontFamily = TitleFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = onStop,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(containerColor = QuikLook.Danger, contentColor = Color.White)
    ) { Text("Stop journey", fontFamily = TitleFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Card, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(label, color = Muted, fontFamily = BodyFontFamily, fontSize = 12.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = Ink, fontFamily = TitleFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

/** Leave — Midway — Arrival rail showing which alerts have fired. */
@Composable
private fun AlertProgress(stage: Int) {
    Column(modifier = Modifier.fillMaxWidth().background(Card, RoundedCornerShape(22.dp)).padding(18.dp)) {
        Text("EXIT CHECKLIST ALERTS", color = Muted, fontFamily = BodyFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            listOf("Leave", "Midway", "Arrival").forEachIndexed { i, label ->
                val reached = i <= stage
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(if (reached) Accent else QuikLook.Border, RoundedCornerShape(50))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        label,
                        color = if (reached) Ink else Muted,
                        fontFamily = BodyFontFamily,
                        fontSize = 12.sp,
                        fontWeight = if (reached) FontWeight.Bold else FontWeight.Normal
                    )
                }
                if (i < 2) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .padding(horizontal = 8.dp)
                            .background(if (i < stage) Ink else QuikLook.Border)
                    )
                }
            }
        }
    }
}

/** Design 9 — the exit checklist, on the green hero card. */
@Composable
private fun ExitChecklist(
    items: List<String>,
    checkedItems: Set<String>,
    onToggle: (String) -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.radialGradient(listOf(QuikLook.GreenLight, QuikLook.GreenDark)),
                RoundedCornerShape(28.dp)
            )
            .padding(22.dp)
    ) {
        Text(
            "Check your things\nbefore leaving",
            color = Color.White,
            fontFamily = TitleFontFamily,
            fontSize = 26.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(22.dp))
        items.forEach { item ->
            ItemChip(
                item,
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(item) }
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(if (item in checkedItems) Accent else Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item in checkedItems) {
                        Icon(painterResource(R.drawable.ic_quiklook_mark), null, tint = Ink, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onDone,
        enabled = items.isEmpty() || checkedItems.containsAll(items),
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink,
            contentColor = Accent,
            disabledContainerColor = QuikLook.Surface,
            disabledContentColor = Muted
        )
    ) { Text("I took everything", fontFamily = TitleFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

private fun formatDistance(meters: Float): String = if (meters >= 1_000f) {
    String.format(Locale.US, "%.1f km", meters / 1_000f)
} else {
    "${meters.toInt()} m"
}

private fun formatCountdown(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun formatClockTime(epochMs: Long): String = SimpleDateFormat("h:mm a", Locale.US).format(Date(epochMs))

@Composable
private fun SavePlaceRow(
    destination: Destination,
    savedPlaces: List<SavedPlace>,
    onAddSavedPlace: (String, Destination) -> Unit
) {
    var customLabel by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().background(Card, RoundedCornerShape(24.dp)).padding(16.dp)) {
        Text("SAVE THIS PLACE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Home", "Office").forEach { label ->
                val alreadySaved = savedPlaces.any { it.label.equals(label, ignoreCase = true) && it.destination == destination }
                ChoiceButton(
                    if (alreadySaved) "✓ $label" else label,
                    alreadySaved,
                    { onAddSavedPlace(label, destination) },
                    Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = customLabel,
                onValueChange = { customLabel = it },
                label = { Text("Custom label") },
                placeholder = { Text("e.g. Gym") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = quikLookFieldColors(),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                val trimmed = customLabel.trim()
                if (trimmed.isNotEmpty()) {
                    onAddSavedPlace(trimmed, destination)
                    customLabel = ""
                }
            }) { Text("Save", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(6.dp))
        Text("Saved places skip Google search next time — no API credits used.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun DestinationSetup(
    onSave: (Destination) -> Unit,
    onOpenGooglePicker: () -> Unit,
    placesAvailable: Boolean,
    recentDestinations: List<Destination>,
    savedPlaces: List<SavedPlace>,
    onRemoveSavedPlace: (String) -> Unit,
    destinationPickerError: String?
) {
    var showCoordinates by remember { mutableStateOf(false) }
    var editingSavedPlaces by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().background(Card, RoundedCornerShape(24.dp)).padding(22.dp)
    ) {
        Text("DESTINATION", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Where are you going?", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Black)
        if (savedPlaces.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("SAVED PLACES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { editingSavedPlaces = !editingSavedPlaces }) {
                    Text(if (editingSavedPlaces) "Done" else "Edit", color = Muted, fontWeight = FontWeight.Bold)
                }
            }
            savedPlaces.forEach { saved ->
                Spacer(Modifier.height(6.dp))
                if (editingSavedPlaces) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(saved.label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(saved.destination.name, color = Muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onRemoveSavedPlace(saved.label) }) {
                            Text("Remove", color = Color(0xFFFF3964), fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    TextButton(
                        onClick = { onSave(saved.destination) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(saved.label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(saved.destination.name, color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (recentDestinations.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("RECENT DESTINATIONS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            recentDestinations.forEach { recent ->
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { onSave(recent) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(recent.name, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Use saved location — no Google search", color = Muted, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("OR SEARCH FOR A NEW PLACE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onOpenGooglePicker,
            enabled = placesAvailable,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DeepInk)
        ) {
            Text(
                if (placesAvailable) "Choose destination" else "Google Places unavailable",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("Search powered by Google without leaving QuikLook", color = Muted, fontSize = 12.sp)
        destinationPickerError?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Color(0xFFFF3964), fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showCoordinates = !showCoordinates }) {
            Text(if (showCoordinates) "Hide coordinates" else "Enter coordinates instead", color = Muted)
        }

        if (showCoordinates) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("Office, airport, station") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = quikLookFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = latitude,
                onValueChange = { latitude = it },
                label = { Text("Latitude") },
                placeholder = { Text("12.9716") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = quikLookFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = longitude,
                onValueChange = { longitude = it },
                label = { Text("Longitude") },
                placeholder = { Text("77.5946") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = quikLookFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFFF3964), fontSize = 14.sp)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val lat = latitude.toDoubleOrNull()
                    val lon = longitude.toDoubleOrNull()
                    error = when {
                        name.isBlank() -> "Enter a destination name."
                        lat == null || lon == null -> "Enter valid numeric coordinates."
                        lat !in -90.0..90.0 -> "Latitude must be between -90 and 90."
                        lon !in -180.0..180.0 -> "Longitude must be between -180 and 180."
                        else -> null
                    }
                    if (error == null) onSave(Destination(name.trim(), lat!!, lon!!))
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DeepInk)
            ) {
                Text("Use coordinates", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LastStopPreview() {
    LastStopApp(
        locationState = LocationUiState.PermissionNeeded,
        onPermissionResult = {},
        onOpenSettings = {},
        onOpenLocationSettings = {},
        systemLocationEnabled = true,
        batteryOptimizationIgnored = true,
        onRequestIgnoreBatteryOptimizations = {},
        onStartUpdates = {},
        onStopUpdates = {},
        uiScreen = UiScreen.Idle,
        onChangeScreen = {},
        recentDestinations = emptyList(),
        pickedDestination = null,
        onPickDestination = {},
        savedPlaces = emptyList(),
        onAddSavedPlace = { _, _ -> },
        onRemoveSavedPlace = {},
        onOpenDestinationPicker = {},
        placesAvailable = true,
        destinationPickerError = null,
        belongingsCatalog = DEFAULT_BELONGINGS,
        onAddCatalogItem = {},
        onRemoveCatalogItem = {},
        journeyActive = false,
        activeTarget = null,
        exitMode = false,
        onEnterExitMode = {},
        onStartJourney = {},
        onStopJourney = {},
        ensureNotificationPermission = {},
        signedInUser = null,
        onSignIn = {},
        onSignOut = {},
        signInError = null,
        onOpenHomeLocationPicker = {},
        hasLocationPermission = true
    )
}
