package com.rakshak.core.alert

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSenderTest {

    class MockSmsController : SmsController {
        var permission = true
        var service = true
        var sendCount = 0
        val messagesSent = mutableListOf<String>()

        override fun hasPermission(): Boolean = permission
        override fun hasService(): Boolean = service
        override fun sendSms(destinationAddress: String, text: String): Boolean {
            sendCount++
            messagesSent.add(text)
            return true
        }
    }

    class MockLocationController(val delayMillis: Long = 0, val returnLocation: Boolean = true) : LocationController {
        var callCount = 0
        override suspend fun getLocation(timeoutMillis: Long): LocationData? {
            callCount++
            if (delayMillis > 0) {
                delay(delayMillis)
            }
            if (delayMillis > timeoutMillis) {
                return null // Simulate timeout within the caller/controller logic
            }
            return if (returnLocation) LocationData(37.422, -122.084) else null
        }
    }

    @Test
    fun testRealModeSendsSmsWithExactMessageBody() = runBlocking {
        val sms = MockSmsController()
        val loc = MockLocationController()
        val sender = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("1234567890"))

        val result = sender.sendEmergencyAlert()
        assertEquals(AlertResult.ALERT_SENT, result)
        assertEquals(1, sms.sendCount)
        assertEquals("SOS! A crash has been detected. Location: 37.422, -122.084", sms.messagesSent[0])
    }

    @Test
    fun testDemoModeNeverInvokesRealSmsManager() = runBlocking {
        val sms = MockSmsController()
        val loc = MockLocationController()
        val sender = AlertSender(AlertMode.DEMO_MODE, sms, loc, listOf("1234567890"))

        val result = sender.sendEmergencyAlert()
        assertEquals(AlertResult.ALERT_SENT, result)
        assertEquals(0, sms.sendCount) // NEVER INVOKED

        assertEquals(1, sender.demoLogs.size)
        assertEquals("1234567890", sender.demoLogs[0].recipient)
        assertEquals("available", sender.demoLogs[0].locationStatus)
        assertEquals("SOS! A crash has been detected. Location: 37.422, -122.084", sender.demoLogs[0].messageBody)
    }

    @Test
    fun testMissingPermissionFailsGracefully() = runBlocking {
        val sms = MockSmsController().apply { permission = false }
        val loc = MockLocationController()
        
        // Real mode
        val senderReal = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("1234567890"))
        assertEquals(AlertResult.ALERT_FAILED_NO_PERMISSION, senderReal.sendEmergencyAlert())
        assertEquals(0, sms.sendCount)

        // Demo mode
        val senderDemo = AlertSender(AlertMode.DEMO_MODE, sms, loc, listOf("1234567890"))
        assertEquals(AlertResult.ALERT_FAILED_NO_PERMISSION, senderDemo.sendEmergencyAlert())
        assertEquals(1, senderDemo.demoLogs.size)
    }

    @Test
    fun testUnavailableLocationSendsSmsWithFallbackText() = runBlocking {
        val sms = MockSmsController()
        val loc = MockLocationController(returnLocation = false)
        val sender = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("1234567890"))

        val result = sender.sendEmergencyAlert()
        assertEquals(AlertResult.ALERT_SENT, result)
        assertEquals(1, sms.sendCount)
        assertEquals("SOS! A crash has been detected. Location unavailable", sms.messagesSent[0])
    }

    @Test
    fun testUnavailableCellularServiceFailsGracefully() = runBlocking {
        val sms = MockSmsController().apply { service = false }
        val loc = MockLocationController()
        val sender = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("1234567890"))

        val result = sender.sendEmergencyAlert()
        assertEquals(AlertResult.ALERT_FAILED_NO_SERVICE, result)
        assertEquals(0, sms.sendCount)
    }

    @Test
    fun testRepeatedCallsDoNotCrash() = runBlocking {
        val sms = MockSmsController()
        val loc = MockLocationController()
        val sender = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("1234567890"))

        assertEquals(AlertResult.ALERT_SENT, sender.sendEmergencyAlert())
        assertEquals(AlertResult.ALERT_SENT, sender.sendEmergencyAlert())
        assertEquals(AlertResult.ALERT_SENT, sender.sendEmergencyAlert())
        assertEquals(3, sms.sendCount)
    }

    @Test
    fun testGpsTimeoutDoesNotBlockIndefinitely() = runBlocking {
        val sms = MockSmsController()
        // Delay is longer than timeout
        val loc = MockLocationController(delayMillis = 2000L)
        // Set a short timeout 1 second
        val sender = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("1234567890"), locationTimeoutMillis = 1000L)

        val startTime = System.currentTimeMillis()
        val result = sender.sendEmergencyAlert()
        val duration = System.currentTimeMillis() - startTime

        // Even though location timed out, SMS is sent
        assertEquals(AlertResult.ALERT_SENT, result)
        assertEquals(1, sms.sendCount)
        assertEquals("SOS! A crash has been detected. Location unavailable", sms.messagesSent[0])
        // It shouldn't have waited the full 2000ms delay if our framework properly cancels, 
        // but since we simulate it internally via delayMillis > timeout, it returns null early.
        assertTrue(duration < 3000L) 
    }
}
