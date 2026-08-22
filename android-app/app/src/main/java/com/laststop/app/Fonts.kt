package com.laststop.app

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// Titles: substitute for "Mona Sans" (not on Google Fonts, needs the actual font file to match
// exactly) — Plus Jakarta Sans is a similar-feeling geometric sans in the meantime.
val TitleFontFamily = FontFamily(
    Font(GoogleFont("Plus Jakarta Sans"), fontProvider, FontWeight.Bold),
    Font(GoogleFont("Plus Jakarta Sans"), fontProvider, FontWeight.Black),
    Font(GoogleFont("Plus Jakarta Sans"), fontProvider, FontWeight.Medium)
)

val BodyFontFamily = FontFamily(
    Font(GoogleFont("Inter"), fontProvider, FontWeight.Normal),
    Font(GoogleFont("Inter"), fontProvider, FontWeight.Medium),
    Font(GoogleFont("Inter"), fontProvider, FontWeight.Bold)
)
