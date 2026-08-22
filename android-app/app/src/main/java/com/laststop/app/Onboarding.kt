package com.laststop.app

import android.util.Log
import kotlinx.coroutines.delay
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Onboarding runs on the black ground: four numbered steps behind a splash, each with a back
 * arrow and a progress rail, per the supplied designs. The app itself switches to the cream
 * ground once setup finishes.
 */
private enum class OnboardingStep(val index: Int) {
    Intro(0), Account(1), Permission(2), Profile(3);

    companion object { const val COUNT = 4 }
}

@Composable
internal fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(QuikLook.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ic_quiklook_tile),
                contentDescription = "QuikLook",
                modifier = Modifier.width(184.dp)
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "QuikLook",
                color = QuikLook.White,
                fontFamily = TitleFontFamily,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

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
    initialProfile: UserProfile,
    onSaveProfile: (UserProfile) -> Unit,
    onFinish: () -> Unit
) {
    var step by remember { mutableStateOf(OnboardingStep.Intro) }
    Log.d("OnboardingFlow", "composing step=$step")

    fun back() {
        step = when (step) {
            OnboardingStep.Intro -> OnboardingStep.Intro
            OnboardingStep.Account -> OnboardingStep.Intro
            OnboardingStep.Permission -> OnboardingStep.Account
            OnboardingStep.Profile -> OnboardingStep.Permission
        }
    }

    MaterialTheme(typography = QuikLookTypography) {
        Surface(color = QuikLook.Black, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 30.dp, vertical = 36.dp)
            ) {
                StepHeader(step = step, onBack = ::back)
                Spacer(Modifier.height(30.dp))
                when (step) {
                    OnboardingStep.Intro -> IntroStep(
                        onNext = { step = OnboardingStep.Account }
                    )
                    OnboardingStep.Account -> AccountStep(
                        signedInUser = signedInUser,
                        onSignIn = onSignIn,
                        signInError = signInError,
                        onNext = { step = OnboardingStep.Permission }
                    )
                    OnboardingStep.Permission -> PermissionStep(
                        hasLocationPermission = hasLocationPermission,
                        onRequestLocationPermission = onRequestLocationPermission,
                        onNext = { step = OnboardingStep.Profile }
                    )
                    OnboardingStep.Profile -> ProfileStep(
                        signedInUser = signedInUser,
                        initialProfile = initialProfile,
                        onSaveProfile = onSaveProfile,
                        onFinish = onFinish
                    )
                }
            }
        }
    }
}

/** Back arrow plus the four-segment progress rail shared by every onboarding screen. */
@Composable
private fun StepHeader(step: OnboardingStep, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_left),
            contentDescription = "Back",
            tint = if (step == OnboardingStep.Intro) QuikLook.White.copy(alpha = 0.25f) else QuikLook.White,
            modifier = Modifier
                .size(26.dp)
                .then(if (step == OnboardingStep.Intro) Modifier else Modifier.clickable(onClick = onBack))
        )
        Spacer(Modifier.width(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(OnboardingStep.COUNT) { i ->
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (i <= step.index) QuikLook.Lime else QuikLook.White.copy(alpha = 0.22f))
                )
            }
        }
    }
}

/** Heading where the final phrase is lime — the recurring title treatment in the designs. */
@Composable
private fun TwoToneTitle(lead: String, accent: String) {
    Text(
        buildAnnotatedString {
            append(lead)
            withStyle(SpanStyle(color = QuikLook.Lime)) { append(accent) }
        },
        color = QuikLook.White,
        fontFamily = TitleFontFamily,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun DarkBody(text: String) {
    Text(
        text,
        color = QuikLook.MutedOnDark,
        fontFamily = BodyFontFamily,
        fontSize = 15.sp,
        lineHeight = 22.sp
    )
}

@Composable
private fun LimeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(62.dp),
        shape = RoundedCornerShape(31.dp),
        colors = ButtonDefaults.buttonColors(containerColor = QuikLook.Lime, contentColor = QuikLook.Ink)
    ) {
        Text(label, fontFamily = TitleFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuietButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = QuikLook.MutedOnDark, fontFamily = BodyFontFamily, fontSize = 14.sp)
    }
}

/** A feature row: rounded-square dark tile holding an icon, then bold lead-in plus body. */
@Composable
private fun FeatureRow(iconRes: Int, lead: String, rest: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF171A17)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painter = painterResource(iconRes), contentDescription = null, tint = QuikLook.Lime, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = QuikLook.White, fontWeight = FontWeight.Bold)) { append(lead) }
                append(rest)
            },
            color = QuikLook.MutedOnDark,
            fontFamily = BodyFontFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun IntroStep(onNext: () -> Unit) {
    TwoToneTitle("A gentle\n", "reminder")
    Text(
        " when it matters most.",
        color = QuikLook.White,
        fontFamily = TitleFontFamily,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(16.dp))
    DarkBody("QuikLook helps you check your things before you leave a place.")
    Spacer(Modifier.height(46.dp))
    FeatureRow(R.drawable.ic_glass, "Smart place detection ", "knows when you're about to leave a place.")
    Spacer(Modifier.height(22.dp))
    FeatureRow(R.drawable.ic_timer, "Timely reminders ", "alerts you near the end of your visit.")
    Spacer(Modifier.height(22.dp))
    FeatureRow(R.drawable.ic_keyboard_open, "Check with confidence ", "never leave your essentials behind.")
    Spacer(Modifier.height(48.dp))
    LimeButton("Continue", onNext, Modifier.fillMaxWidth())
}

@Composable
private fun AccountStep(
    signedInUser: SignedInUser?,
    onSignIn: () -> Unit,
    signInError: String?,
    onNext: () -> Unit
) {
    TwoToneTitle("You're one step\n", "closer")
    Spacer(Modifier.height(16.dp))
    DarkBody("Create your account to keep your reminders, places, and preferences in sync.")
    Spacer(Modifier.height(34.dp))
    AppDemoAnimation()
    if (signedInUser != null) {
        Spacer(Modifier.height(18.dp))
        Text(
            "Signed in as ${signedInUser.email}",
            color = QuikLook.MutedOnDark,
            fontFamily = BodyFontFamily,
            fontSize = 13.sp
        )
    } else if (signInError != null) {
        Spacer(Modifier.height(16.dp))
        Text(signInError, color = QuikLook.Danger, fontFamily = BodyFontFamily, fontSize = 13.sp)
    }
    Spacer(Modifier.height(30.dp))
    LimeButton(
        label = if (signedInUser != null) "Continue" else "Continue with Google",
        onClick = { if (signedInUser != null) onNext() else onSignIn() },
        modifier = Modifier.fillMaxWidth()
    )
    if (signedInUser == null) QuietButton("Already have an account?  Sign in", onNext)
}

@Composable
private fun PermissionStep(
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onNext: () -> Unit
) {
    TwoToneTitle("Allow location\n", "access")
    Spacer(Modifier.height(16.dp))
    DarkBody("So QuikLook can notice when you're travelling and remind you before you get out, it needs access to your location while the app is running.")
    Spacer(Modifier.height(44.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF171A17)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painter = painterResource(R.drawable.ic_password_check), contentDescription = null, tint = QuikLook.Lime, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = QuikLook.White, fontWeight = FontWeight.Bold)) { append("Privacy Notice: ") }
                append("QuikLook has no servers of its own — your trips stay on your phone. When you track to a destination, your location goes to Google Maps only to work out your arrival time. No ad networks, no tracking.")
            },
            color = QuikLook.MutedOnDark,
            fontFamily = BodyFontFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
    Spacer(Modifier.height(48.dp))
    LimeButton(
        label = if (hasLocationPermission) "Continue" else "Allow location",
        onClick = { if (hasLocationPermission) onNext() else onRequestLocationPermission() },
        modifier = Modifier.fillMaxWidth()
    )
    if (!hasLocationPermission) QuietButton("I'll set it up later", onNext)
}

@Composable
private fun ProfileStep(
    signedInUser: SignedInUser?,
    initialProfile: UserProfile,
    onSaveProfile: (UserProfile) -> Unit,
    onFinish: () -> Unit
) {
    var name by remember {
        mutableStateOf(initialProfile.name.ifBlank { signedInUser?.displayName.orEmpty() })
    }
    var age by remember { mutableStateOf(initialProfile.age) }
    var gender by remember { mutableStateOf(initialProfile.gender) }
    var phone by remember { mutableStateOf(initialProfile.phone) }

    TwoToneTitle("Setup your\n", "profile")
    Spacer(Modifier.height(14.dp))
    DarkBody("Tell us a bit about yourself")
    Spacer(Modifier.height(34.dp))
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.size(84.dp).clip(CircleShape).background(QuikLook.Lime),
            contentAlignment = Alignment.Center
        ) {
            Icon(painter = painterResource(R.drawable.ic_user), contentDescription = null, tint = QuikLook.Ink, modifier = Modifier.size(36.dp))
        }
    }
    Spacer(Modifier.height(26.dp))
    ProfileField(R.drawable.ic_user, "NAME", name, { name = it }, "Your name")
    Spacer(Modifier.height(12.dp))
    ProfileField(R.drawable.ic_calendar, "AGE", age, { age = it.filter(Char::isDigit).take(3) }, "Your age")
    Spacer(Modifier.height(12.dp))
    ProfileField(R.drawable.ic_profile_2user, "GENDER", gender, { gender = it }, "Your gender")
    Spacer(Modifier.height(12.dp))
    ProfileField(R.drawable.ic_call, "PHONE NUMBER", phone, { phone = it }, "Optional", optional = true)
    Spacer(Modifier.height(34.dp))
    LimeButton(
        "Finish setup",
        {
            onSaveProfile(UserProfile(name = name, age = age, gender = gender, phone = phone))
            onFinish()
        },
        Modifier.fillMaxWidth()
    )
    QuietButton("I'll set it up later", onFinish)
}

/** White pill field with a leading icon, small caps label, and the value beneath it. */
@Composable
private fun ProfileField(
    iconRes: Int,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    optional: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(QuikLook.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = QuikLook.Ink, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = QuikLook.Muted, fontFamily = BodyFontFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (optional) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "OPTIONAL",
                        color = QuikLook.Muted,
                        fontFamily = BodyFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(QuikLook.Surface)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            PlainField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder
            )
        }
    }
}

/**
 * The "how it works" stage: a handset running miniature versions of the real screens.
 *
 * Sized deliberately — a square stage forces the phone narrow and the type illegible, so the
 * stage is portrait, the handset takes nearly all of it, and every screen lays out in a single
 * column so nothing is squeezed. Each screen fills its frame; none leave dead space.
 */
@Composable
private fun AppDemoAnimation() {
    var slide by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2400)
            slide = (slide + 1) % 4
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.86f)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF3FBF69), Color(0xFF1F7F45)),
                    center = Offset.Unspecified
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .aspectRatio(0.58f)
                .clip(RoundedCornerShape(20.dp))
                .background(QuikLook.Cream)
                .padding(10.dp)
        ) {
            Crossfade(targetState = slide, label = "demo") { i ->
                when (i) {
                    0 -> DemoPrompt()
                    1 -> DemoCarry()
                    2 -> DemoJourney()
                    else -> DemoChecklist()
                }
            }
        }
    }
}

@Composable
private fun DemoCaption(text: String) = Text(
    text,
    color = QuikLook.Muted,
    fontFamily = BodyFontFamily,
    fontSize = 7.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.4.sp
)

/** A full-width row in the miniature: icon, label, optional trailing tick. */
@Composable
private fun DemoRow(iconRes: Int, label: String, dark: Boolean, ticked: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (dark) QuikLook.Ink else QuikLook.Surface)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(iconRes), null,
            tint = if (dark) QuikLook.Lime else QuikLook.Ink,
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = if (dark) QuikLook.White else QuikLook.Ink,
            fontFamily = BodyFontFamily,
            fontSize = 9.sp,
            modifier = Modifier.weight(1f)
        )
        if (ticked) {
            Box(
                modifier = Modifier.size(12.dp).clip(CircleShape).background(QuikLook.Lime),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_quiklook_mark), null, tint = QuikLook.Ink, modifier = Modifier.size(8.dp))
            }
        }
    }
}

@Composable
private fun DemoPill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) = Text(
    text,
    color = fg,
    fontFamily = TitleFontFamily,
    fontSize = 9.sp,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center,
    modifier = modifier
        .clip(RoundedCornerShape(20.dp))
        .background(bg)
        .padding(horizontal = 10.dp, vertical = 7.dp)
)

/** 1 — the passive prompt that starts a trip. */
@Composable
private fun DemoPrompt() {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.radialGradient(listOf(QuikLook.GreenLight, QuikLook.GreenDark)))
                .padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Are you\ntraveling?",
                color = QuikLook.White, fontFamily = TitleFontFamily, fontSize = 14.sp,
                lineHeight = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            DemoPill("Yes, set trip", QuikLook.Lime, QuikLook.Ink)
            Spacer(Modifier.height(5.dp))
            DemoPill("Not now", QuikLook.White, QuikLook.Ink)
        }
        DemoCaption("QUIKLOOK NOTICED YOU MOVING")
    }
}

/** 2 — choosing what you carry. */
@Composable
private fun DemoCarry() {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DemoCaption("SELECT WHAT YOU CARRY")
        DemoRow(R.drawable.ic_bag, "Bag", dark = true)
        DemoRow(R.drawable.ic_mobile, "Phone", dark = true)
        DemoRow(R.drawable.ic_empty_wallet, "Wallet", dark = true)
        DemoRow(R.drawable.ic_key, "Keys", dark = false)
        DemoRow(R.drawable.ic_glass, "Sunglasses", dark = false)
        Spacer(Modifier.weight(1f))
        DemoPill("Continue", QuikLook.Ink, QuikLook.White, Modifier.fillMaxWidth())
    }
}

/** 3 — the live journey. */
@Composable
private fun DemoJourney() {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(QuikLook.Lime)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("JOURNEY ACTIVE", color = QuikLook.Ink, fontFamily = TitleFontFamily,
                fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("2.4 km", color = QuikLook.Ink, fontFamily = BodyFontFamily, fontSize = 7.sp)
        }
        DemoStat("ETA", "6:45 PM")
        DemoStat("Remaining", "12 min")
        DemoStat("Destination", "Home")
        Spacer(Modifier.weight(1f))
        DemoPill("Stop journey", QuikLook.Danger, QuikLook.White, Modifier.fillMaxWidth())
    }
}

@Composable
private fun DemoStat(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(QuikLook.White)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = QuikLook.Muted, fontFamily = BodyFontFamily, fontSize = 8.sp, modifier = Modifier.weight(1f))
        Text(value, color = QuikLook.Ink, fontFamily = TitleFontFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

/** 4 — the exit checklist, the moment the product exists for. */
@Composable
private fun DemoChecklist() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.radialGradient(listOf(QuikLook.GreenLight, QuikLook.GreenDark)))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Check your things\nbefore leaving",
            color = QuikLook.White, fontFamily = TitleFontFamily, fontSize = 12.sp,
            lineHeight = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(2.dp))
        DemoRow(R.drawable.ic_bag, "Bag", dark = true, ticked = true)
        DemoRow(R.drawable.ic_mobile, "Phone", dark = true, ticked = true)
        DemoRow(R.drawable.ic_empty_wallet, "Wallet", dark = true, ticked = true)
        DemoRow(R.drawable.ic_key, "Keys", dark = true, ticked = true)
        Spacer(Modifier.weight(1f))
        DemoPill("I took everything", QuikLook.Ink, QuikLook.Lime, Modifier.fillMaxWidth())
    }
}
