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

private val Canvas = Color.Black
private val Ink = Color.White
private val Accent = Color(0xFFDBFF45)
private val Muted = Color(0xFFB5C0C6)
private val Card = Color(0xFF040B19)
private val DeepInk = Color.Black

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
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF040B19), Color.Black, Color(0xFF540028))
                        )
                    )
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
                            .background(Color(0xFF680F0F), RoundedCornerShape(24.dp))
                            .padding(18.dp)
                    ) {
                        Text("LOCATION IS OFF", color = Color(0xFFFF3964), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            .background(Color(0xFF27422A), RoundedCornerShape(24.dp))
                            .padding(18.dp)
                    ) {
                        Text("BATTERY OPTIMIZATION IS ON", color = Color(0xFF8BEF95), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        Text("Are you traveling?", color = Color.White, fontFamily = TitleFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
    var query by remember { mutableStateOf("") }
    var editMode by remember { mutableStateOf(false) }
    var useDestination by remember { mutableStateOf(true) }
    var hours by remember { mutableStateOf(0) }
    var minutes by remember { mutableStateOf(10) }
    val effectiveMinutes = hours * 60 + minutes

    Text("Looks like you're\ntraveling.", color = Ink, fontSize = 42.sp, lineHeight = 45.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(10.dp))
    Text("What are you carrying?", color = Muted, fontSize = 16.sp)
    Spacer(Modifier.height(20.dp))

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Add an item") },
        placeholder = { Text("e.g. Charger") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = quikLookFieldColors(),
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(Iconsax.Outline.SearchNormal, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            TextButton(onClick = {
                val trimmed = query.trim()
                if (trimmed.isNotEmpty()) {
                    onAddItem(trimmed)
                    query = ""
                }
            }) { Text("Add", fontWeight = FontWeight.Bold) }
        }
    )

    val suggestions = if (query.isBlank()) emptyList() else catalog.filter {
        it.contains(query, ignoreCase = true) && it !in selectedItems
    }
    if (suggestions.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Column(modifier = Modifier.fillMaxWidth().background(Card, RoundedCornerShape(24.dp))) {
            suggestions.take(5).forEach { suggestion ->
                TextButton(
                    onClick = { onToggleItem(suggestion); query = "" },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(suggestion, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("YOUR ITEMS", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        TextButton(onClick = { editMode = !editMode }) {
            Text(if (editMode) "Done" else "Edit list", color = Muted, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(10.dp))

    if (editMode) {
        catalog.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { onRemoveItem(item) }) {
                    Text("Remove", color = Color(0xFFFF3964), fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        catalog.chunked(2).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { item ->
                    ChoiceButton(item, item in selectedItems, { onToggleItem(item) }, Modifier.weight(1f))
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    Spacer(Modifier.height(28.dp))
    Text("HOW LONG IS THIS TRIP?", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ChoiceButton("Set a timer", !useDestination, { useDestination = false }, Modifier.weight(1f))
        ChoiceButton("Add a destination", useDestination, { useDestination = true }, Modifier.weight(1f))
    }
    Spacer(Modifier.height(18.dp))

    if (!useDestination) {
        val arrivalPreview = formatClockTime(System.currentTimeMillis() + effectiveMinutes * 60_000L)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TimeStepper(
                label = "Hours",
                value = hours,
                displayValue = "$hours",
                onDecrement = { hours = (hours - 1).coerceAtLeast(0) },
                onIncrement = { hours = (hours + 1).coerceAtMost(12) },
                modifier = Modifier.weight(1f)
            )
            TimeStepper(
                label = "Minutes",
                value = minutes,
                displayValue = String.format(Locale.US, "%02d", minutes),
                onDecrement = { minutes = (minutes - 5).coerceAtLeast(0) },
                onIncrement = { minutes = (minutes + 5).coerceAtMost(55) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier.fillMaxWidth().background(Card, RoundedCornerShape(24.dp)).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ARRIVING AROUND", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(arrivalPreview, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onStartTimer(effectiveMinutes) },
            enabled = effectiveMinutes > 0,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DeepInk)
        ) { Text("Start journey", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    } else {
        DestinationSetup(
            onSave = onPickDestination,
            onOpenGooglePicker = onOpenDestinationPicker,
            placesAvailable = placesAvailable,
            recentDestinations = recentDestinations,
            savedPlaces = savedPlaces,
            onRemoveSavedPlace = onRemoveSavedPlace,
            destinationPickerError = destinationPickerError
        )
        if (pickedDestination != null) {
            Spacer(Modifier.height(18.dp))
            SavePlaceRow(destination = pickedDestination, savedPlaces = savedPlaces, onAddSavedPlace = onAddSavedPlace)
            Spacer(Modifier.height(18.dp))
            Text("HOW ARE YOU TRAVELLING?", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            TravelSpeeds.modes.forEach { mode ->
                ChoiceButton(mode, mode == travelMode, { onSelectMode(mode) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onStartToDestination(pickedDestination) },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DeepInk)
            ) { Text("Start journey", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }

    Spacer(Modifier.height(10.dp))
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = Muted) }
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
            disabledContainerColor = Color(0xFF040B19),
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
    val arrivalAtEpochMs = remember(tick) {
        context.getSharedPreferences("laststop", Context.MODE_PRIVATE).getLong("eta_arrival_at_epoch_ms", 0L)
    }
    val arrivalClockText = arrivalAtEpochMs.takeIf { it > 0L }?.let(::formatClockTime)

    Text(if (exitMode) "Check before\nyou leave." else "Journey in\nprogress.", color = Ink,
        fontSize = 42.sp, lineHeight = 45.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(24.dp))

    if (exitMode) {
        selectedItems.forEach { item ->
            ChoiceButton(item, item in checkedItems, {
                checkedItems = if (item in checkedItems) checkedItems - item else checkedItems + item
            }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onStop,
            enabled = selectedItems.isEmpty() || checkedItems.containsAll(selectedItems),
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DeepInk)
        ) { Text("All checked — finish", fontWeight = FontWeight.Bold) }
    } else {
        Column(modifier = Modifier.fillMaxWidth().background(Card, RoundedCornerShape(24.dp)).padding(22.dp)) {
            when (target) {
                is ActiveTarget.ToDestination -> {
                    Text("HEADING TO", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(target.destination.name, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(20.dp))
                    Text("DISTANCE REMAINING", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(distance?.let(::formatDistance) ?: "Finding location…", color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Black)
                }
                is ActiveTarget.Timer -> {
                    Text("TIME REMAINING", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(formatCountdown(remainingSeconds ?: 0L), color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Black)
                }
            }
            if (arrivalClockText != null) {
                Spacer(Modifier.height(14.dp))
                Text("ARRIVING AROUND", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(arrivalClockText, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (target is ActiveTarget.ToDestination) {
                    "Arrival time uses live traffic data. You'll be alerted at 5 min, 1 min, and on arrival."
                } else {
                    "Tracking continues when QuikLook is minimized. You'll be alerted at 5 min, 1 min, and on arrival."
                },
                color = Muted,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onEnterExitMode, modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(32.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = DeepInk)) {
            Text("Enter Exit Mode", fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop journey", color = Muted) }
    }
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
