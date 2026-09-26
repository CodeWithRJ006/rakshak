package com.rakshak.core.alert

import kotlinx.coroutines.withTimeoutOrNull

enum class AlertMode {
    REAL_MODE,
    DEMO_MODE
}

enum class AlertResult {
    ALERT_SENT,
    ALERT_FAILED_NO_PERMISSION,
    ALERT_FAILED_NO_SERVICE,
    ALERT_FAILED_NO_CONTACTS,
    ALERT_FAILED_UNKNOWN
}

data class LocationData(val latitude: Double, val longitude: Double)

interface SmsController {
    fun hasPermission(): Boolean
    fun hasService(): Boolean
    fun sendSms(destinationAddress: String, text: String): Boolean
}

interface LocationController {
    // The controller itself should try to be fast, but we wrap it in withTimeoutOrNull anyway
    suspend fun getLocation(): LocationData?
}

class AlertSender(
    private val mode: AlertMode,
    private val smsController: SmsController,
    private val locationController: LocationController,
    private val emergencyContacts: List<String>,
    private val locationTimeoutMillis: Long = 3000L
) {
    val demoLogs = mutableListOf<DemoLog>()

    data class DemoLog(
        val recipient: String,
        val messageBody: String,
        val locationStatus: String,
        val alertResult: AlertResult
    )

    suspend fun sendEmergencyAlert(): AlertResult {
        if (emergencyContacts.isEmpty()) {
            return AlertResult.ALERT_FAILED_NO_CONTACTS
        }

        if (mode == AlertMode.REAL_MODE) {
            if (!smsController.hasPermission()) {
                return AlertResult.ALERT_FAILED_NO_PERMISSION
            }
            if (!smsController.hasService()) {
                return AlertResult.ALERT_FAILED_NO_SERVICE
            }
        }

        // Bounded wait for location. SMS is higher priority, so we do not wait indefinitely.
        val location = try {
            withTimeoutOrNull(locationTimeoutMillis) {
                locationController.getLocation()
            }
        } catch (e: Exception) {
            null
        }
        
        val locationStatus = if (location != null) "available" else "unavailable"
        
        val locationText = if (location != null) {
            "Location: " + location.latitude + ", " + location.longitude
        } else {
            "Location unavailable"
        }
        
        val message = "SOS! A crash has been detected. " + locationText

        if (mode == AlertMode.DEMO_MODE) {
            logDemo(AlertResult.ALERT_SENT, locationStatus, message)
            return AlertResult.ALERT_SENT
        }

        var successCount = 0
        for (contact in emergencyContacts) {
            val sent = smsController.sendSms(contact, message)
            if (sent) successCount++
        }

        return if (successCount > 0) AlertResult.ALERT_SENT else AlertResult.ALERT_FAILED_UNKNOWN
    }
    
    private fun logDemo(result: AlertResult, locationStatus: String, messageBody: String) {
        for (contact in emergencyContacts) {
            demoLogs.add(DemoLog(contact, messageBody, locationStatus, result))
        }
    }
}
