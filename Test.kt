package com.rakshak.core.detector

fun main() {
    val config = CrashDetectionConfig(gyroMagnitudeThreshold = 4.0f)
    println(config.gyroMagnitudeThreshold)
}
