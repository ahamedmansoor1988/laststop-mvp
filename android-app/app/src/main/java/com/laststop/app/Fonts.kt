package com.laststop.app

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Uses the platform font rather than Google's downloadable-fonts provider.
 *
 * The provider version crashed the app. Declaring it requires a font_certs.xml holding the
 * provider's base-64 certificate hashes, and androidx parses that file whenever it inflates a
 * view that resolves a fontFamily — including plain EditTexts inside library layouts. If the
 * base-64 is not valid, FontResourcesParserCompat.readCerts throws IllegalArgumentException and
 * takes the whole activity down. That is what killed the Places address-search screen.
 *
 * The requested display face ("Momo Trust Sans") is also not published on Google Fonts, so the
 * provider could never have resolved it in any case.
 *
 * To restore the brand typography: drop the real .ttf/.otf files into res/font and build these
 * families from them with Font(R.font.…). Bundled fonts need no certificates and no network, so
 * this class of failure cannot recur.
 */

val TitleFontFamily = FontFamily.SansSerif

val BodyFontFamily = FontFamily.SansSerif

val QuikLookTypography = Typography(
    displayLarge = TextStyle(fontFamily = TitleFontFamily, fontWeight = FontWeight.Black, fontSize = 42.sp, lineHeight = 45.sp),
    headlineLarge = TextStyle(fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = TitleFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
)
