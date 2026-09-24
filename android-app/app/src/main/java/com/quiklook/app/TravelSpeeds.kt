package com.quiklook.app

object TravelSpeeds {
    // Realistic average speeds including stops/turns/traffic — not idealized cruising speed —
    // since these only apply as a fallback when live Google Routes data isn't available.
    private val metersPerSecondByMode = mapOf(
        "Walk" to 1.4,
        "Bike" to 4.2,
        "Car" to 8.3,
        "Bus" to 6.9,
        "Train" to 13.9
    )

    val modes: List<String> = metersPerSecondByMode.keys.toList()

    fun metersPerSecond(mode: String): Double = metersPerSecondByMode[mode] ?: 6.9
}
