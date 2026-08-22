package com.laststop.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/*
 * Palette measured from the supplied design SVGs rather than rounded to the three headline
 * brand colours — the two most-used values across the screens are Ink (#171714) and Cream
 * (#F8F7F2), and neither is pure black or pure white.
 *
 * The product runs two grounds on purpose:
 *   Onboarding  → pure black, white headings with a lime accent word
 *   The app     → cream, ink text on white cards
 */
object QuikLook {
    /** Onboarding ground. The only place pure black is used. */
    val Black = Color(0xFF000000)

    /** App ground. */
    val Cream = Color(0xFFF8F7F2)

    /** Primary text and dark chips/buttons on cream. Not pure black. */
    val Ink = Color(0xFF171714)

    /** Primary action, selected chips, icon+label colour on dark chips. */
    val Lime = Color(0xFFDBFF45)

    val White = Color(0xFFFFFFFF)

    /** Filled surfaces on cream — unselected travel tiles, quiet chips. */
    val Surface = Color(0xFFEEEDE7)

    /** Hairline borders on cream. */
    val Border = Color(0xFFE6E5DE)

    /** Secondary text on cream. */
    val Muted = Color(0xFF6E6D66)

    /** Secondary text on black. */
    val MutedOnDark = Color(0xFFB5C0C6)

    /** Destructive: stop journey / stop timer. */
    val Danger = Color(0xFFFF0331)

    val Success = Color(0xFF34C759)

    // Gradient pairs for the hero cards, centre (dark) to edge (light).
    val GreenDark = Color(0xFF27422A)
    val GreenLight = Color(0xFF8BEF95)
    val PinkDark = Color(0xFF540028)
    val PinkLight = Color(0xFFFF69B2)
    val BlueDark = Color(0xFF040B19)
    val BlueLight = Color(0xFF2B8CFF)
}

/**
 * Single-line field with no Material decoration. The designs place bare text inside their own
 * containers, so this keeps the container fully in charge of the look.
 */
@Composable
fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    color: Color = QuikLook.Ink
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = color, fontSize = 15.sp),
        cursorBrush = SolidColor(QuikLook.Ink),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = QuikLook.Muted, fontSize = 15.sp)
            inner()
        }
    )
}
