package com.laststop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log

private enum class OnboardingStep { Welcome, HowItWorks, SignIn, HomeLocation, Permission }

@Composable
internal fun OnboardingFlow(
    signedInUser: SignedInUser?,
    onSignIn: () -> Unit,
    signInError: String?,
    homeDestination: Destination?,
    onOpenDestinationPicker: () -> Unit,
    placesAvailable: Boolean,
    destinationPickerError: String?,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onFinish: () -> Unit
) {
    var step by remember { mutableStateOf(OnboardingStep.Welcome) }
    Log.d("OnboardingFlow", "composing step=$step")

    MaterialTheme {
        Surface(color = Color(0xFFF8F7F2), modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                when (step) {
                    OnboardingStep.Welcome -> WelcomeStep(onNext = { Log.d("OnboardingFlow", "tap: Welcome->HowItWorks"); step = OnboardingStep.HowItWorks })
                    OnboardingStep.HowItWorks -> HowItWorksStep(onNext = { Log.d("OnboardingFlow", "tap: HowItWorks->SignIn"); step = OnboardingStep.SignIn })
                    OnboardingStep.SignIn -> SignInStep(
                        signedInUser = signedInUser,
                        onSignIn = onSignIn,
                        signInError = signInError,
                        onNext = { Log.d("OnboardingFlow", "tap: SignIn->HomeLocation"); step = OnboardingStep.HomeLocation }
                    )
                    OnboardingStep.HomeLocation -> HomeLocationStep(
                        homeDestination = homeDestination,
                        onOpenDestinationPicker = onOpenDestinationPicker,
                        placesAvailable = placesAvailable,
                        destinationPickerError = destinationPickerError,
                        onNext = { Log.d("OnboardingFlow", "tap: HomeLocation->Permission"); step = OnboardingStep.Permission }
                    )
                    OnboardingStep.Permission -> PermissionStep(
                        hasLocationPermission = hasLocationPermission,
                        onRequestLocationPermission = onRequestLocationPermission,
                        onFinish = { Log.d("OnboardingFlow", "tap: Permission->FINISH"); onFinish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepScaffold(
    eyebrow: String,
    title: String,
    body: String,
    primaryLabel: String,
    primaryEnabled: Boolean = true,
    onPrimary: () -> Unit,
    secondary: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    Text(eyebrow, color = Color(0xFF6E6D66), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(14.dp))
    Text(title, color = Color(0xFF171714), fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(14.dp))
    Text(body, color = Color(0xFF171714), fontSize = 16.sp, lineHeight = 22.sp)
    if (content != null) {
        Spacer(Modifier.height(20.dp))
        content()
    }
    Spacer(Modifier.height(28.dp))
    Button(
        onClick = onPrimary,
        enabled = primaryEnabled,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF171714), contentColor = Color.White)
    ) { Text(primaryLabel, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    if (secondary != null) {
        Spacer(Modifier.height(10.dp))
        secondary()
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    StepScaffold(
        eyebrow = "EXITCHCK",
        title = "Check before\nyou arrive.",
        body = "exitchck watches for when you're traveling and reminds you to check your belongings before you get out — no need to remember to open the app.",
        primaryLabel = "Get started",
        onPrimary = onNext
    )
}

@Composable
private fun HowItWorksStep(onNext: () -> Unit) {
    StepScaffold(
        eyebrow = "HOW IT WORKS",
        title = "Three\nsteps.",
        body = "",
        primaryLabel = "Next",
        onPrimary = onNext,
        content = {
            HowItWorksRow("1", "We notice you're moving", "No need to start anything manually — exitchck notices when you're on the move and prompts you.")
            Spacer(Modifier.height(14.dp))
            HowItWorksRow("2", "Pick your belongings & trip", "Check off what you're carrying, then set a timer or a destination.")
            Spacer(Modifier.height(14.dp))
            HowItWorksRow("3", "Get reminded before you arrive", "Alerts at 5 minutes, 1 minute, and on arrival — so nothing gets left behind.")
        }
    )
}

@Composable
private fun HowItWorksRow(number: String, title: String, body: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            number,
            color = Color(0xFF171714),
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .background(Color(0xFFDBFF45), CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color(0xFF171714), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(body, color = Color(0xFF6E6D66), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun SignInStep(
    signedInUser: SignedInUser?,
    onSignIn: () -> Unit,
    signInError: String?,
    onNext: () -> Unit
) {
    StepScaffold(
        eyebrow = "SIGN IN",
        title = "One quick\nsign-in.",
        body = "Sign in with Google so exitchck can greet you by name. We don't use this to track or sell your data.",
        primaryLabel = if (signedInUser != null) "Continue" else "Sign in with Google",
        onPrimary = { if (signedInUser != null) onNext() else onSignIn() },
        secondary = if (signedInUser == null) {
            {
                TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip for now", color = Color(0xFF6E6D66), fontWeight = FontWeight.Bold)
                }
            }
        } else null,
        content = {
            if (signedInUser != null) {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Text("SIGNED IN AS", color = Color(0xFF6E6D66), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(signedInUser.displayName, color = Color(0xFF171714), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(signedInUser.email, color = Color(0xFF6E6D66), fontSize = 13.sp)
                }
            } else if (signInError != null) {
                Text(signInError, color = Color(0xFFB3261E), fontSize = 13.sp)
            }
        }
    )
}

@Composable
private fun HomeLocationStep(
    homeDestination: Destination?,
    onOpenDestinationPicker: () -> Unit,
    placesAvailable: Boolean,
    destinationPickerError: String?,
    onNext: () -> Unit
) {
    StepScaffold(
        eyebrow = "SET HOME",
        title = "Where's\nhome?",
        body = "Saving this once means exitchck never has to search Google for your home address again — it's stored only on this device.",
        primaryLabel = if (homeDestination != null) "Continue" else "Skip for now",
        onPrimary = onNext,
        content = {
            Column(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp)) {
                if (homeDestination != null) {
                    Text("HOME SET TO", color = Color(0xFF6E6D66), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(homeDestination.name, color = Color(0xFF171714), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = onOpenDestinationPicker,
                    enabled = placesAvailable,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF171714), contentColor = Color.White)
                ) { Text(if (homeDestination != null) "Change home address" else "Search for your home", fontWeight = FontWeight.Bold) }
                destinationPickerError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color(0xFFB3261E), fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "We don't see or store this location on any server — it only ever leaves your device when you actually start a trip, sent directly to Google to calculate directions.",
                    color = Color(0xFF6E6D66),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    )
}

@Composable
private fun PermissionStep(
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onFinish: () -> Unit
) {
    StepScaffold(
        eyebrow = "LAST STEP",
        title = "Allow\nlocation.",
        body = "exitchck only asks for location while the app is running — not \"always,\" just while it's active watching for your next trip. You can turn this off any time in Settings.",
        primaryLabel = if (hasLocationPermission) "Start using exitchck" else "Allow location",
        onPrimary = { if (hasLocationPermission) onFinish() else onRequestLocationPermission() }
    )
}
