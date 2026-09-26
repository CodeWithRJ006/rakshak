package com.rakshak.core.alert

enum class AlertMode {
    REAL_MODE,
    DEMO_MODE
}

enum class AlertResult {
    ALERT_SENT,
    ALERT_FAILED_NO_PERMISSION,
    ALERT_FAILED_NO_SERVICE,
    ALERT_FAILED_UNKNOWN
}

data class LocationData(val latitude: Double, val longitude: Double)

interface SmsController {
    fun hasPermission(): Boolean
    fun hasService(): Boolean
    fun sendSms(destinationAddress: String, text: String): Boolean
}

interface LocationController {
    suspend fun getLocation(timeoutMillis: Long): LocationData?
}

class AlertSender(
    private val mode: AlertMode,
    private val smsController: SmsController,
    private val locationController: LocationController,
    private val emergencyContacts: List<String>,
    private val locationTimeoutMillis: Long = 5000L
) {
    val demoLogs = mutableListOf<DemoLog>()

    data class DemoLog(
        val recipient: String,
        val messageBody: String,
        val locationStatus: String,
        val alertResult: AlertResult
    )

    suspend fun sendEmergencyAlert(): AlertResult {
        if (!smsController.hasPermission()) {
            if (mode == AlertMode.DEMO_MODE) {
                logDemo(AlertResult.ALERT_FAILED_NO_PERMISSION, "unknown", "")
            }
            return AlertResult.ALERT_FAILED_NO_PERMISSION
        }

        if (mode == AlertMode.REAL_MODE && !smsController.hasService()) {
            return AlertResult.ALERT_FAILED_NO_SERVICE
        }

        val location = locationController.getLocation(locationTimeoutMillis)
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
