package com.laststop.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import io.github.rabehx.iconsax.Iconsax
import io.github.rabehx.iconsax.outline.Home
import io.github.rabehx.iconsax.outline.Location
import io.github.rabehx.iconsax.outline.Routing
import io.github.rabehx.iconsax.outline.SearchNormal
import io.github.rabehx.iconsax.outline.Shield
import io.github.rabehx.iconsax.outline.TickCircle
import io.github.rabehx.iconsax.outline.Timer

/**
 * Three screens: explain, ask for the one permission the app cannot work without, then set
 * home. Deliberately short — the product's whole promise is that you don't have to think about
 * it, so a long setup contradicts the pitch.
 */
private enum class OnboardingStep { Intro, Permission, Home }

private val Lime = Color(0xFFDBFF45)
private val MutedText = Color(0xFFB5C0C6)
private val DimText = Color(0xFF6E6D66)

/** Full-screen cold-start loading screen — the animated liquid-gradient card centered on black,
 * matching the provided design's proportions (card ~77% width, ~29% height of the canvas). */
@Composable
internal fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        LiquidGradientCard(
            modifier = Modifier
                .fillMaxWidth(0.77f)
                .aspectRatio(316f / 265f)
        )
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
    onFinish: () -> Unit
) {
    var step by remember { mutableStateOf(OnboardingStep.Intro) }
    Log.d("OnboardingFlow", "composing step=$step")

    MaterialTheme(typography = QuikLookTypography) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            when (step) {
                OnboardingStep.Intro -> IntroStep(
                    onNext = { step = OnboardingStep.Permission }
                )
                OnboardingStep.Permission -> PermissionStep(
                    hasLocationPermission = hasLocationPermission,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onNext = { step = OnboardingStep.Home }
                )
                OnboardingStep.Home -> HomeStep(
                    homeDestination = homeDestination,
                    onOpenDestinationPicker = onOpenDestinationPicker,
                    placesAvailable = placesAvailable,
                    destinationPickerError = destinationPickerError,
                    signedInUser = signedInUser,
                    onSignIn = onSignIn,
                    signInError = signInError,
                    onFinish = onFinish
                )
            }
        }
    }
}

/** Full-bleed soft corner glow over the black page background. */
@Composable
private fun CornerGlow(color: Color, centerX: Float, centerY: Float, alpha: Float = 0.30f) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                center = Offset(size.width * centerX, size.height * centerY),
                radius = size.maxDimension * 0.85f
            )
        )
    }
}

@Composable
private fun LimeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color.Black)
    ) {
        Text(label, fontFamily = TitleFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IntroStep(
    onNext: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        CornerGlow(Color(0xFFFF69B2), 0.10f, -0.02f, alpha = 0.38f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp)
        ) {
            Spacer(Modifier.height(110.dp))
            Text(
                "Get reminded\nbefore you arrive.",
                color = Color.White,
                fontFamily = TitleFontFamily,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "A calm travel assistant that keeps you and your belongings together.",
                color = MutedText,
                fontFamily = BodyFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(36.dp))
            HowItWorksRow(Iconsax.Outline.Routing, "Detect travel", "The app notes when you leave your home radius or start traveling.")
            Spacer(Modifier.height(20.dp))
            HowItWorksRow(Iconsax.Outline.Timer, "Pick destination or timer", "Select your target destination or set a quick exit countdown timer.")
            Spacer(Modifier.height(20.dp))
            HowItWorksRow(Iconsax.Outline.TickCircle, "Check items on exit", "Get a peaceful checklist ping to make sure you have your keys, bag, and wallet.")
            Spacer(Modifier.height(42.dp))
            LimeButton("Get started", onNext, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HowItWorksRow(icon: ImageVector, title: String, body: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFFFF69B2), Color(0xFF540028)))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontFamily = TitleFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(body, color = MutedText, fontFamily = BodyFontFamily, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

/**
 * Static single-palette version of the splash's liquid-gradient card — same dark-center/
 * light-edge radial recipe and white top-left highlight bloom, no animation. Used as the
 * colored hero panel on the Permission and HomeLocation steps.
 */
@Composable
private fun GradientPanel(
    darkColor: Color,
    lightColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier.clip(RoundedCornerShape(32.dp))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(darkColor, lightColor),
                    center = Offset(size.width * 0.67f, size.height * 0.56f),
                    radius = size.maxDimension * 0.95f
                )
            )
        }
        Canvas(modifier = Modifier.fillMaxSize().blur(60.dp)) {
            val center = Offset(size.width * 0.32f, size.height * 0.2f)
            val radius = size.width * 0.6f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun PermissionStep(
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 40.dp)
    ) {
        Spacer(Modifier.height(60.dp))
        GradientPanel(
            darkColor = Color(0xFF27422A),
            lightColor = Color(0xFF8BEF95),
            modifier = Modifier.fillMaxWidth().aspectRatio(1.02f)
        ) {
            Text(
                "Allow location\naccess",
                color = Color.White,
                fontFamily = TitleFontFamily,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "So QuikLook can notice when you're travelling and remind you before you get out, " +
                    "it needs access to your location while the app is running.",
                color = Color.White.copy(alpha = 0.9f),
                fontFamily = BodyFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFEB0052), Color(0xFF8E0132)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Iconsax.Outline.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Calm & Private", color = Color.White, fontFamily = TitleFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "QuikLook has no servers of its own — your trips stay on your phone. When you track " +
                "to a destination, your location goes to Google Maps only to work out your arrival " +
                "time. No ad networks, no tracking. Only clean reminders.",
            color = MutedText,
            fontFamily = BodyFontFamily,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(36.dp))
        LimeButton(
            label = if (hasLocationPermission) "Continue" else "Allow location",
            onClick = { if (hasLocationPermission) onNext() else onRequestLocationPermission() },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("May be later", color = DimText, fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HomeStep(
    homeDestination: Destination?,
    onOpenDestinationPicker: () -> Unit,
    placesAvailable: Boolean,
    destinationPickerError: String?,
    signedInUser: SignedInUser?,
    onSignIn: () -> Unit,
    signInError: String?,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 40.dp)
    ) {
        Spacer(Modifier.height(60.dp))
        GradientPanel(
            darkColor = Color(0xFF540028),
            lightColor = Color(0xFFFF69B2),
            modifier = Modifier.fillMaxWidth().aspectRatio(1.02f)
        ) {
            Text(
                "Set your home\nlocation",
                color = Color.White,
                fontFamily = TitleFontFamily,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "QuikLook reminds you to check your belongings the moment you step out of your door.",
                color = Color.White.copy(alpha = 0.9f),
                fontFamily = BodyFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))
        if (homeDestination != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF040B19), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(Lime),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Iconsax.Outline.Home, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Home Location", color = Color.White, fontFamily = TitleFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(homeDestination.name, color = MutedText, fontFamily = BodyFontFamily, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Text(
            "Search your home location",
            color = Color.White,
            fontFamily = BodyFontFamily,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(28.dp))
                .then(if (placesAvailable) Modifier.clickable(onClick = onOpenDestinationPicker) else Modifier)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Iconsax.Outline.SearchNormal, contentDescription = null, tint = Color(0xFF6E6D66), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (homeDestination != null) "Change address..." else "Search address...",
                color = Color(0xFF6E6D66),
                fontFamily = BodyFontFamily,
                fontSize = 15.sp
            )
        }
        destinationPickerError?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Color(0xFFFF3964), fontFamily = BodyFontFamily, fontSize = 13.sp)
        }

        // Signing in is about keeping these places, so it belongs on this screen rather than
        // on one of its own — it is genuinely optional and never blocks finishing setup.
        Spacer(Modifier.height(20.dp))
        when {
            signedInUser != null -> Text(
                "Signed in as ${signedInUser.email}",
                color = DimText,
                fontFamily = BodyFontFamily,
                fontSize = 13.sp
            )
            else -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onSignIn)
                    .background(Color(0xFF040B19), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sign in with Google", color = Color.White, fontFamily = TitleFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Optional — keeps your saved places if you change phones.", color = MutedText, fontFamily = BodyFontFamily, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
        signInError?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Color(0xFFFF3964), fontFamily = BodyFontFamily, fontSize = 13.sp)
        }

        Spacer(Modifier.height(28.dp))
        LimeButton(
            label = if (homeDestination != null) "Start using QuikLook" else "Skip for now",
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Soft, continuously drifting radial-gradient blob cycling through three palettes (magenta,
 * blue, green) with eased crossfades — the animated "liquid gradient" intro card. Logo lockup
 * (ic_quiklook_logo.xml) mirrors the provided design's path data unmodified.
 */
@Composable
private fun LiquidGradientCard(modifier: Modifier = Modifier) {
    val palettes = remember {
        listOf(
            Color(0xFF540028) to Color(0xFFFF69B2),
            Color(0xFF040B19) to Color(0xFF2B8CFF),
            Color(0xFF27422A) to Color(0xFF8BEF95)
        )
    }
    val infinite = rememberInfiniteTransition(label = "liquid")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = palettes.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val driftX by infinite.animateFloat(
        initialValue = -0.16f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(tween(6_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "driftX"
    )
    val driftY by infinite.animateFloat(
        initialValue = -0.12f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(tween(7_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "driftY"
    )

    val index = phase.toInt().coerceIn(0, palettes.size - 1)
    val nextIndex = (index + 1) % palettes.size
    val eased = FastOutSlowInEasing.transform(phase - phase.toInt())
    // Gradient stop 0 (dark) sits at the shape's own center per the source SVG's
    // gradientTransform (~67%, 56% of the card); stop 1 (light) is the outer color.
    val centerColor = lerp(palettes[index].first, palettes[nextIndex].first, eased)
    val edgeColor = lerp(palettes[index].second, palettes[nextIndex].second, eased)

    Box(modifier = modifier.clip(RoundedCornerShape(40.dp))) {
        // Base fill covers the entire card in the palette gradient — no black ever shows,
        // even in the corners the highlight blob doesn't reach.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * (0.67f + driftX), size.height * (0.56f + driftY))
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(centerColor, edgeColor),
                    center = center,
                    radius = size.maxDimension * 0.95f
                )
            )
        }
        // Soft white highlight bloom, top-left biased — mirrors the design's inner-shadow
        // filter, which forces its shadow color to pure white (colorMatrix 0,0,0,0,1 per
        // channel) rather than the usual black — same blur/offset recipe for every stage.
        Canvas(modifier = Modifier.fillMaxSize().blur(60.dp)) {
            val center = Offset(size.width * (0.32f + driftX * 0.6f), size.height * (0.2f + driftY * 0.6f))
            val radius = size.width * 0.6f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_quiklook_logo),
            contentDescription = "QuikLook",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .width(160.dp)
                .aspectRatio(245f / 56f)
        )
    }
}
