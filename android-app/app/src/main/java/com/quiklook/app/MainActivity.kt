package com.quiklook.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.annotation.SuppressLint
import android.util.Log
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
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

private const val FREE_CREDITS = 100

private sealed interface JourneySelection {
    data class Timer(val minutes: Int) : JourneySelection
    data class ToDestination(val destination: Destination, val travelMode: String) : JourneySelection
}

private sealed interface ActiveTarget {
    data class Timer(val endAtEpochMs: Long) : ActiveTarget
    data class ToDestination(val destination: Destination, val travelMode: String) : ActiveTarget
}

private val DEFAULT_BELONGINGS = listOf(
    "Bag", "Phone", "Wallet", "Kids", "Keys", "Sunglasses",
    "Earbuds", "Charger", "Passport", "iPad", "Suitcase", "Cabin bag",
    "Boarding pass", "Books", "Laptop", "Medicine", "Watch"
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
    /** One credit is spent per trip started. New installs begin with a free allowance; paying
     * to top up is not built yet, so running out currently just blocks new trips. */
    private var credits by mutableIntStateOf(FREE_CREDITS)
    private var creditsMessage by mutableStateOf<String?>(null)
    private var signInError by mutableStateOf<String?>(null)
    private var pickingHomeForOnboarding = false
    private var pickingSavedPlace = false
    private var hasLocationPermissionState by mutableStateOf(false)
    private lateinit var placesClient: PlacesClient

    private val destinationPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        Log.d("Places", "autocomplete returned code=${result.resultCode} hasData=${data != null}")
        if (data == null) {
            // Backing out is a normal cancel; anything else closing empty is a real failure and
            // must say so rather than looking like a dead button.
            destinationPickerError = if (result.resultCode == RESULT_CANCELED) null else
                "Place search closed without a result. Check that Places API (New) is enabled for this API key."
            return@registerForActivityResult
        }
        if (result.resultCode == PlaceAutocompleteActivity.RESULT_OK) {
            val prediction = PlaceAutocomplete.getPredictionFromIntent(data)
            if (prediction == null) {
                destinationPickerError = "Google did not return a destination. Please try again."
                return@registerForActivityResult
            }
            val sessionToken = PlaceAutocomplete.getSessionTokenFromIntent(data)
            val request = FetchPlaceRequest.builder(
                prediction.placeId,
                // Keep the follow-up details call in the Essentials tier. The autocomplete
                // prediction already contains the display label, so requesting DISPLAY_NAME
                // would add a Pro-tier field without improving the destination UX.
                listOf(Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION)
            ).setSessionToken(sessionToken).build()
            placesClient.fetchPlace(request)
                .addOnSuccessListener { response ->
                    val place = response.place
                    val location = place.location
                    if (location == null) {
                        destinationPickerError = "Google did not return coordinates for that place."
                        return@addOnSuccessListener
                    }
                    val label = prediction.getPrimaryText(null).toString().takeIf { it.isNotBlank() }
                        ?: place.formattedAddress?.takeIf { it.isNotBlank() }
                        ?: "Destination"
                    destinationPickerError = null
                    val picked = Destination(label, location.latitude, location.longitude)
                    if (pickingHomeForOnboarding) {
                        pickingHomeForOnboarding = false
                        addSavedPlace("Home", picked)
                    } else if (pickingSavedPlace) {
                        pickingSavedPlace = false
                        addSavedPlace(picked.name, picked)
                        pickedDestination = picked
                    } else {
                        pickedDestination = picked
                        saveRecentDestination(picked)
                    }
                }
                .addOnFailureListener { error ->
                    destinationPickerError = error.message ?: "Google could not load that destination."
                }
        } else if (result.resultCode != RESULT_CANCELED) {
            val status = PlaceAutocomplete.getResultStatusFromIntent(data)
            Log.d("Places", "autocomplete error status=$status")
            destinationPickerError = status?.statusMessage
                ?: "Place search failed. Check that Places API (New) is enabled for this API key."
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
        credits = getSharedPreferences("laststop", MODE_PRIVATE).getInt("credits", FREE_CREDITS)
        TravelReminderReceiver.schedule(this)
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

            // Splash and onboarding sit on black, the app itself on cream, so the system bars
            // have to flip with them: light icons over the dark screens, dark icons over the
            // cream one. Fixing this in the theme is not possible - it is one value for the
            // whole app, and the app is two grounds.
            val onDarkGround = showSplash || !onboardingComplete
            SideEffect {
                window.statusBarColor = if (onDarkGround) 0xFF000000.toInt() else 0xFFF8F7F2.toInt()
                window.navigationBarColor = if (onDarkGround) 0xFF000000.toInt() else 0xFFF8F7F2.toInt()
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !onDarkGround
                    isAppearanceLightNavigationBars = !onDarkGround
                }
            }

            if (showSplash) {
                SplashScreen()
            } else if (!onboardingComplete) {
                OnboardingFlow(
                    signedInUser = signedInUser,
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
                onExitApp = { finish() },
                credits = credits,
                creditsMessage = creditsMessage,
                userProfile = userProfile,
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
                onClearRecents = ::clearRecentDestinations,
                savedPlaces = savedPlaces,
                onAddSavedPlace = ::addSavedPlace,
                onRemoveSavedPlace = ::removeSavedPlace,
                belongingsCatalog = belongingsCatalog,
                onAddCatalogItem = ::addCatalogItem,
                onRemoveCatalogItem = ::removeCatalogItem,
                onOpenDestinationPicker = ::openDestinationPicker,
                onOpenSavedPlacePicker = ::openSavedPlacePicker,
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
        systemLocationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager?.isLocationEnabled == true
        } else {
            manager?.let {
                it.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    it.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            } == true
        }
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
        if (!placesAvailable) {
            destinationPickerError = "Place search is unavailable — no Places API key is configured in this build."
            return
        }
        pickingHomeForOnboarding = false
        pickingSavedPlace = false
        destinationPickerError = null
        destinationPicker.launch(PlaceAutocomplete.createIntent(this) {
            setCountries(listOf("in"))
        })
    }

    /** Add place: the result is stored as a saved place, not just used as this trip's target. */
    private fun openSavedPlacePicker() {
        if (!placesAvailable) {
            destinationPickerError = "Place search is unavailable — no Places API key is configured in this build."
            return
        }
        pickingHomeForOnboarding = false
        pickingSavedPlace = true
        destinationPickerError = null
        destinationPicker.launch(PlaceAutocomplete.createIntent(this) {
            setCountries(listOf("in"))
        })
    }

    private fun openHomeLocationPicker() {
        if (!placesAvailable) {
            destinationPickerError = "Place search is unavailable — no Places API key is configured in this build."
            return
        }
        pickingHomeForOnboarding = true
        pickingSavedPlace = false
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

    private fun clearRecentDestinations() {
        recentDestinations = emptyList()
        saveRecentDestinations(emptyList())
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
        if (credits <= 0) {
            creditsMessage = "You have used all your trip credits. Top-ups are coming soon."
            return
        }
        spendCredit()
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

    private fun spendCredit() {
        credits = (credits - 1).coerceAtLeast(0)
        creditsMessage = null
        getSharedPreferences("laststop", MODE_PRIVATE).edit().putInt("credits", credits).apply()
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
    onExitApp: () -> Unit,
    credits: Int,
    creditsMessage: String?,
    userProfile: UserProfile,
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
    onClearRecents: () -> Unit,
    savedPlaces: List<SavedPlace>,
    onAddSavedPlace: (String, Destination) -> Unit,
    onRemoveSavedPlace: (String) -> Unit,
    onOpenDestinationPicker: () -> Unit,
    onOpenSavedPlacePicker: () -> Unit,
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
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Back sits where the wordmark used to; the avatar replaces the settings link.
                    if (uiScreen != UiScreen.Idle && !journeyActive) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_left),
                            contentDescription = "Back",
                            tint = Ink,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { onChangeScreen(UiScreen.Idle) }
                                .padding(6.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (!journeyActive) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Ink)
                                .clickable { onChangeScreen(UiScreen.Settings) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_user),
                                contentDescription = "Your profile",
                                tint = Accent,
                                modifier = Modifier.size(18.dp)
                            )
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
                        credits = credits,
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
                        onClearRecents = onClearRecents,
                        savedPlaces = savedPlaces,
                        onAddSavedPlace = onAddSavedPlace,
                        onRemoveSavedPlace = onRemoveSavedPlace,
                        onOpenDestinationPicker = onOpenDestinationPicker,
                        onOpenSavedPlacePicker = onOpenSavedPlacePicker,
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
                        userProfile = userProfile,
                        credits = credits,
                        onTopUp = { onChangeScreen(UiScreen.Settings) },
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
                        creditsMessage = creditsMessage,
                        onDismiss = onExitApp,
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
                        onStartNow = { onChangeScreen(UiScreen.Journey) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun IdleScreen(
    creditsMessage: String?,
    onDismiss: () -> Unit,
    catalog: List<String>,
    selectedItems: Set<String>,
    onToggleItem: (String) -> Unit,
    onAddItem: (String) -> Unit,
    onStartNow: () -> Unit
) {
    creditsMessage?.let {
        Text(
            it,
            color = QuikLook.Danger,
            fontFamily = BodyFontFamily,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFDE7EB), RoundedCornerShape(16.dp))
                .padding(16.dp)
        )
        Spacer(Modifier.height(16.dp))
    }
    CarrySelection(
        catalog = catalog,
        selectedItems = selectedItems,
        onToggleItem = onToggleItem,
        onAddItem = onAddItem,
        onContinue = onStartNow,
        onDismiss = onDismiss
    )
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

/**
 * Settings, built to the supplied screen. Geometry is the source's, converted from its 412-wide
 * canvas: cards 338.5 wide at 19.25 radius with a #E6E5DE hairline, field rows 53 tall at 11.5,
 * and 25.5-tall action pills — red-outlined for sign out, ink-outlined otherwise.
 */
@Composable
private fun SettingsScreen(
    signedInUser: SignedInUser?,
    userProfile: UserProfile,
    credits: Int,
    onTopUp: () -> Unit,
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
    Text("Settings", color = Ink, fontFamily = TitleFontFamily, fontSize = 34.sp, fontWeight = FontWeight.Bold)

    Spacer(Modifier.height(30.dp))
    SettingsLabel("PROFILE ACCOUNT")
    Spacer(Modifier.height(14.dp))

    // Profile card — avatar, name, and the red-outlined sign-out pill.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(81.dp)
            .border(1.dp, QuikLook.Border, RoundedCornerShape(17.dp))
            .background(Card, RoundedCornerShape(17.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(53.dp)
                    .clip(CircleShape)
                    .background(Accent)
                    .border(1.dp, Ink, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_user), null, tint = Ink, modifier = Modifier.size(24.dp))
            }
            Box(
                modifier = Modifier.size(22.dp).clip(CircleShape).background(Ink),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_add), null, tint = Color.White, modifier = Modifier.size(11.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            signedInUser?.displayName ?: userProfile.name.ifBlank { "Not signed in" },
            color = Ink, fontFamily = BodyFontFamily, fontSize = 16.sp,
            modifier = Modifier.weight(1f), maxLines = 1
        )
        if (signedInUser != null) {
            OutlinePill("Sign out", QuikLook.Danger, onSignOut)
        } else {
            OutlinePill("Sign in", Ink, onSignIn)
        }
    }
    signInError?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = QuikLook.Danger, fontFamily = BodyFontFamily, fontSize = 13.sp)
    }

    Spacer(Modifier.height(8.dp))
    ProfileField(R.drawable.ic_calendar, userProfile.age.ifBlank { "Age not set" }, userProfile.age.isBlank())
    Spacer(Modifier.height(8.dp))
    ProfileField(R.drawable.ic_profile_2user, userProfile.gender.ifBlank { "Gender not set" }, userProfile.gender.isBlank())
    Spacer(Modifier.height(8.dp))
    ProfileField(R.drawable.ic_call, userProfile.phone.ifBlank { "Phone not set" }, userProfile.phone.isBlank())

    Spacer(Modifier.height(52.dp))
    SettingsLabel("TRIP CREDITS")
    Spacer(Modifier.height(14.dp))
    StatusCard(
        icon = R.drawable.ic_ticket_star,
        title = if (credits > 0) "$credits credits left" else "No credits left",
        subtitle = if (credits > 0) "One credit per trip you start" else "Top-ups are coming soon",
        actionLabel = "Coming soon",
        actionColor = Ink,
        enabled = false,
        onAction = onTopUp
    )

    Spacer(Modifier.height(52.dp))
    SettingsLabel("HOME LOCATION")
    Spacer(Modifier.height(14.dp))
    StatusCard(
        icon = R.drawable.ic_location_add,
        title = "Home Radius",
        subtitle = homeDestination?.name ?: "Not set",
        actionLabel = if (homeDestination != null) "Change" else "Set",
        actionColor = Ink,
        enabled = placesAvailable,
        onAction = onOpenHomeLocationPicker
    )
    destinationPickerError?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = QuikLook.Danger, fontFamily = BodyFontFamily, fontSize = 13.sp)
    }

    Spacer(Modifier.height(52.dp))
    SettingsLabel("PERMISSIONS STATUS")
    Spacer(Modifier.height(14.dp))
    StatusCard(
        icon = R.drawable.ic_location_add,
        title = "Location access",
        subtitle = null,
        actionLabel = if (hasLocationPermission) "Allowed" else "Allow",
        actionColor = Ink,
        enabled = true,
        onAction = { if (!hasLocationPermission) onOpenAppSettings() }
    )
    if (!systemLocationEnabled) {
        Spacer(Modifier.height(8.dp))
        StatusCard(
            icon = R.drawable.ic_location_add,
            title = "Location services",
            subtitle = "Turned off on this phone",
            actionLabel = "Turn on",
            actionColor = Ink,
            enabled = true,
            onAction = onOpenLocationSettings
        )
    }
    if (!batteryOptimizationIgnored) {
        Spacer(Modifier.height(8.dp))
        StatusCard(
            icon = R.drawable.ic_battery_charging,
            title = "Background activity",
            subtitle = "Restricted — tracking may stop",
            actionLabel = "Allow",
            actionColor = Ink,
            enabled = true,
            onAction = onRequestIgnoreBatteryOptimizations
        )
    }

    Spacer(Modifier.height(34.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text("Back", color = Muted, fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsLabel(text: String) =
    Text(text, color = Muted, fontFamily = BodyFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)

/** 25.5-tall outlined pill, red for destructive actions and ink otherwise. */
@Composable
private fun OutlinePill(label: String, colour: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colour, RoundedCornerShape(14.dp))
            .background(Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = colour, fontFamily = BodyFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** A 53-tall white field row showing one stored profile value. */
@Composable
private fun ProfileField(icon: Int, value: String, placeholder: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .border(1.dp, QuikLook.Border, RoundedCornerShape(10.dp))
            .background(Card, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), null, tint = Ink, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(value, color = if (placeholder) Muted else Ink, fontFamily = BodyFontFamily, fontSize = 15.sp)
    }
}

/** The home-location and permission cards: round icon well, label, and an outlined action. */
@Composable
private fun StatusCard(
    icon: Int,
    title: String,
    subtitle: String?,
    actionLabel: String,
    actionColor: Color,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .border(1.dp, QuikLook.Border, RoundedCornerShape(17.dp))
            .background(Card, RoundedCornerShape(17.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(35.dp).clip(CircleShape).background(QuikLook.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), null, tint = Ink, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Ink, fontFamily = TitleFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, color = Muted, fontFamily = BodyFontFamily, fontSize = 13.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (enabled) OutlinePill(actionLabel, actionColor, onAction)
    }
}

@Composable
private fun JourneyScreen(
    credits: Int,
    catalog: List<String>,
    selectedItems: Set<String>,
    onToggleItem: (String) -> Unit,
    onAddItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    recentDestinations: List<Destination>,
    pickedDestination: Destination?,
    onPickDestination: (Destination) -> Unit,
    onClearRecents: () -> Unit,
    savedPlaces: List<SavedPlace>,
    onAddSavedPlace: (String, Destination) -> Unit,
    onRemoveSavedPlace: (String) -> Unit,
    onOpenDestinationPicker: () -> Unit,
    onOpenSavedPlacePicker: () -> Unit,
    placesAvailable: Boolean,
    destinationPickerError: String?,
    travelMode: String,
    onSelectMode: (String) -> Unit,
    onStartTimer: (Int) -> Unit,
    onStartToDestination: (Destination) -> Unit,
    onCancel: () -> Unit
) {
    var useDestination by remember { mutableStateOf(true) }
    var minutes by remember { mutableStateOf(15) }

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
            Icon(painterResource(R.drawable.ic_location_add), null, tint = Muted, modifier = Modifier.size(19.dp))
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
        val savedEntries: List<SavedPlace?> = savedPlaces + listOf(null)
        savedEntries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                row.forEach { place ->
                    if (place == null) {
                        AddPlaceChip(
                            enabled = placesAvailable,
                            onClick = onOpenSavedPlacePicker,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        val selected = pickedDestination?.name == place.destination.name
                        PlaceChip(
                            label = place.label,
                            iconRes = if (place.label.equals("Home", true)) R.drawable.ic_home else R.drawable.ic_building,
                            selected = selected,
                            onClick = { onPickDestination(place.destination) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(9.dp))
        }

        val recents = recentDestinations.filter { recent ->
            savedPlaces.none { it.destination.name == recent.name }
        }
        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionLabel("Recent") }
                Text(
                    "Clear",
                    color = Muted,
                    fontFamily = BodyFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClearRecents)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            recents.take(6).chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    row.forEach { recent ->
                        PlaceChip(
                            label = recent.name,
                            iconRes = R.drawable.ic_location_add,
                            selected = pickedDestination?.name == recent.name,
                            onClick = { onPickDestination(recent) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(9.dp))
            }
        }

        Spacer(Modifier.height(26.dp))
        SectionLabel("Mode of travel")
        Spacer(Modifier.height(12.dp))
        val modes = listOf(
            "Bike" to R.drawable.ic_bike,
            "Walk" to R.drawable.ic_walk,
            "Bus" to R.drawable.ic_bus,
            "Car" to R.drawable.ic_car,
            "Flight" to R.drawable.ic_airplane,
            "Train" to R.drawable.ic_train
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

    Spacer(Modifier.height(26.dp))
    // Shown next to the action that spends one, so the cost is visible at the point of paying it.
    Text(
        if (credits > 0) "$credits credits left · 1 per trip" else "No credits left",
        color = if (credits > 0) Muted else QuikLook.Danger,
        fontFamily = BodyFontFamily,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = { if (useDestination) pickedDestination?.let(onStartToDestination) else onStartTimer(minutes) },
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
            .heroGradient(Color(0xFF072766), Color(0xFF2B8CFF), 0.5f, 0.601f, 0.528f, glowAlpha = 0.65f),
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
private fun PlaceChip(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Ink else QuikLook.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(iconRes), null, tint = if (selected) Accent else Ink, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (selected) Accent else Ink,
            fontFamily = BodyFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AddPlaceChip(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
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

/**
 * Design 8. Geometry from the source: a 348x300 hero at 60 corner radius with both prompt pills
 * inside it, then equal-width two-column item chips. Keeping every chip on the same grid makes
 * user-added and translated long names align predictably instead of shifting later rows.
 */
@Composable
private fun CarrySelection(
    catalog: List<String>,
    selectedItems: Set<String>,
    onToggleItem: (String) -> Unit,
    onAddItem: (String) -> Unit,
    onContinue: () -> Unit,
    onDismiss: (() -> Unit)?
) {
    var adding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(348f / 300f)
            .clip(RoundedCornerShape(percent = 17))
            .heroGradient(QuikLook.GreenDark, QuikLook.GreenLight, 0.454f, 1.081f, 0.574f),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                "Are you\ntraveling?", color = Color.White, fontFamily = TitleFontFamily,
                fontSize = 37.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Set active reminders for your next destination.",
                color = Color.White.copy(alpha = 0.92f), fontFamily = BodyFontFamily,
                fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            // Both pills live inside the card, 32 tall at 16 radius.
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PromptPill("Yes, set trip", filled = true, onClick = onContinue)
                PromptPill("Not now", filled = false, onClick = { onDismiss?.invoke() })
            }
        }
    }
    }

    Spacer(Modifier.height(26.dp))
    SectionLabel("Select what you carry")
    Spacer(Modifier.height(14.dp))

    val carryEntries: List<String?> = catalog.map { it } + listOf(null)
    carryEntries.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            row.forEach { item ->
                if (item == null) {
                    AddItemChip(
                        onClick = { adding = !adding },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    CarryChip(
                        item = item,
                        selected = item in selectedItems,
                        onClick = { onToggleItem(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
    }

    if (adding) {
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Card, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) { PlainField(query, { query = it }, "Name the item") }
            TextButton(
                onClick = {
                    val trimmed = query.trim()
                    if (trimmed.isNotEmpty()) { onAddItem(trimmed); query = ""; adding = false }
                }
            ) { Text("Add", color = Ink, fontWeight = FontWeight.Bold) }
        }
    }

    Spacer(Modifier.height(30.dp))
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth(0.84f).height(70.dp),
        shape = RoundedCornerShape(35.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink, contentColor = Color.White
        )
    ) { Text("Continue", fontFamily = TitleFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PromptPill(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (filled) Accent else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Ink, fontFamily = TitleFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddItemChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(39.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_add), null, tint = Muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Text("Add Item", color = Muted, fontFamily = BodyFontFamily, fontSize = 14.sp)
    }
}

@Composable
private fun CarryChip(
    item: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(39.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Ink else QuikLook.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(belongingIcon(item)), null, tint = if (selected) Accent else Ink, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            item,
            color = if (selected) Accent else Ink,
            fontFamily = BodyFontFamily,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

/**
 * The hero gradient from the designs: dark at the centre easing out to pale at the edges. The
 * source gradients are ellipses, not circles — design 8 runs 376 wide by 172 tall — so a plain
 * radial brush cannot express them. This scales the canvas about the gradient centre and draws
 * a circle into it, which yields the ellipse. Values are fractions of the drawn size, taken
 * from each screen's gradientTransform.
 */
private fun Modifier.heroGradient(
    dark: Color,
    light: Color,
    centerYFraction: Float,
    radiusXFraction: Float,
    radiusYFraction: Float,
    // The sources erode the alpha by different amounts before blurring - 5 on the green cards,
    // 25 on the blue timer - so the timer's inner glow lands noticeably stronger.
    glowAlpha: Float = 0.5f
): Modifier = drawBehind {
    val cx = size.width * 0.5f
    val cy = size.height * centerYFraction
    val rx = size.width * radiusXFraction
    val ry = size.height * radiusYFraction

    // Base ellipse: dark at the centre out to the pale stop.
    withTransform({ scale(1f, ry / rx, pivot = Offset(cx, cy)) }) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(dark, light),
                center = Offset(cx, cy),
                radius = rx
            ),
            topLeft = Offset(cx - rx * 2f, cy - rx * 2f),
            size = Size(rx * 4f, rx * 4f)
        )
    }

    // The source card carries an inner shadow whose feColorMatrix forces the shadow colour to
    // pure white, so the inside edges are washed toward white rather than darkened. Without it
    // the card reads far too saturated: the real edge is #C2F1C7, lighter than the #8BEF95 stop.
    val gx = size.width * 0.5f
    val gy = size.height * 0.5f
    val gc = Offset(cx, gy)
    withTransform({ scale(1f, gy / gx, pivot = gc) }) {
        drawRect(
            // The source erodes the alpha before blurring, so the glow starts part-way out
            // and the centre stays the pure gradient colour.
            brush = Brush.radialGradient(
                0.00f to Color.White.copy(alpha = 0f),
                0.38f to Color.White.copy(alpha = 0f),
                1.00f to Color.White.copy(alpha = glowAlpha),
                center = gc,
                radius = gx
            ),
            topLeft = Offset(cx - gx * 2f, gy - gx * 2f),
            size = Size(gx * 4f, gx * 4f)
        )
    }
}

/**
 * Maps a belongings label to a drawable, checked against what each supplied glyph actually
 * depicts rather than against its filename — iconsax-glass is eyewear, not a magnifier, and
 * iconsax-theta is not a vehicle. Anything without a genuine match falls back to a plain bag
 * rather than borrowing a misleading icon.
 */
internal fun belongingIcon(item: String): Int = when (item.lowercase(Locale.US)) {
    "bag", "handbag", "cabin bag", "suitcase" -> R.drawable.ic_bag
    "phone", "mobile", "ipad", "tablet" -> R.drawable.ic_mobile
    "wallet" -> R.drawable.ic_empty_wallet
    "keys", "key" -> R.drawable.ic_key
    "earbuds", "headphones" -> R.drawable.ic_headphones
    "sunglasses", "glasses" -> R.drawable.ic_glass
    "laptop" -> R.drawable.ic_keyboard_open
    "charger", "power bank" -> R.drawable.ic_battery_charging
    "kids" -> R.drawable.ic_profile_2user
    "boarding pass", "ticket" -> R.drawable.ic_ticket_star
    "watch" -> R.drawable.ic_watch
    "books", "book" -> R.drawable.ic_book_open
    "passport" -> R.drawable.ic_password_check
    "medicine", "medicines" -> R.drawable.ic_medicine
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
    // Remaining has to come off the same Google arrival time the ETA card shows, or the two
    // disagree. Previously this fell through to distance for destination trips, so the card
    // read in kilometres while the design and the ETA beside it are both about time.
    val secondsToArrival = remember(tick, arrivalAtEpochMs) {
        arrivalAtEpochMs.takeIf { it > 0L }
            ?.let { ((it - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L) }
    }
    val transport = remember(tick) { prefs.getString("travel_mode", "Bike") ?: "Bike" }
    // Google's road distance, when we have it: a straight line reads short on any real route.
    val roadDistance = remember(tick) {
        prefs.getFloat("eta_road_distance_meters", -1f).takeIf { it >= 0f }
    }

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
                roadDistance != null -> "${formatDistance(roadDistance)} left"
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
                secondsToArrival != null -> formatRemaining(secondsToArrival)
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
        Text(
            value,
            color = Ink,
            fontFamily = TitleFontFamily,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

/**
 * Design 9. From the source: the card is 348 by 656 at 60 radius, the rows are 199 wide — not
 * full width — centred in it, 49 tall and fully rounded, and each tick is a 22.5 lime circle
 * pinned to the row's right edge rather than sitting against the label.
 */
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
            .clip(RoundedCornerShape(percent = 17))
            .heroGradient(QuikLook.GreenDark, QuikLook.GreenLight, 0.5f, 1.08f, 0.574f)
            .padding(top = 69.dp, bottom = 62.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Check your\nthings before\nleaving",
            color = Color.White,
            fontFamily = TitleFontFamily,
            fontSize = 37.sp,
            lineHeight = 43.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(31.dp))
        items.forEach { item ->
            ChecklistRow(item, item in checkedItems) { onToggle(item) }
            Spacer(Modifier.height(8.dp))
        }
    }
    Spacer(Modifier.height(24.dp))
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Button(
            onClick = onDone,
            enabled = items.isEmpty() || checkedItems.containsAll(items),
            modifier = Modifier.fillMaxWidth(0.84f).height(70.dp),
            shape = RoundedCornerShape(35.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Ink,
                contentColor = Accent,
                disabledContainerColor = Ink,
                disabledContentColor = Accent.copy(alpha = 0.38f)
            )
        ) { Text("I took everything", fontFamily = TitleFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    }
}

/** 199-wide pill: icon, label, then the tick pushed to the right edge. */
@Composable
private fun ChecklistRow(item: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.572f)
            .height(43.dp)
            .clip(RoundedCornerShape(50))
            .background(Ink)
            .clickable(onClick = onToggle)
            .padding(start = 14.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(belongingIcon(item)), null, tint = Accent, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            item,
            color = Accent,
            fontFamily = BodyFontFamily,
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) Accent else Color.Transparent)
                .border(1.5.dp, if (checked) Accent else Color.White.copy(alpha = 0.38f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(painterResource(R.drawable.ic_quiklook_mark), null, tint = Ink, modifier = Modifier.size(12.dp))
            }
        }
    }
}


private fun formatDistance(meters: Float): String = if (meters >= 1_000f) {
    String.format(Locale.US, "%.1f km", meters / 1_000f)
} else {
    "${meters.toInt()} m"
}

/** Coarse, human remaining time for a live route: "12 min", "1h 20m", "Arriving". */
private fun formatRemaining(totalSeconds: Long): String {
    val minutes = (totalSeconds + 30) / 60
    return when {
        minutes <= 0L -> "Arriving"
        minutes < 60L -> "$minutes min"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
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
        onExitApp = {},
        credits = 100,
        creditsMessage = null,
        userProfile = UserProfile(),
        uiScreen = UiScreen.Idle,
        onChangeScreen = {},
        recentDestinations = emptyList(),
        pickedDestination = null,
        onPickDestination = {},
        onClearRecents = {},
        savedPlaces = emptyList(),
        onAddSavedPlace = { _, _ -> },
        onRemoveSavedPlace = {},
        onOpenDestinationPicker = {},
        onOpenSavedPlacePicker = {},
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
