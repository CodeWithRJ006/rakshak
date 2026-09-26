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
        override suspend fun getLocation(): LocationData? {
            callCount++
            if (delayMillis > 0) {
                delay(delayMillis)
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
    fun testDemoModeWithPermissionDeniedStillReturnsAlertSent() = runBlocking {
        val sms = MockSmsController().apply { permission = false }
        val loc = MockLocationController()
        
        // Demo mode ignores the missing permission
        val senderDemo = AlertSender(AlertMode.DEMO_MODE, sms, loc, listOf("1234567890"))
        assertEquals(AlertResult.ALERT_SENT, senderDemo.sendEmergencyAlert())
        assertEquals(1, senderDemo.demoLogs.size)
        assertEquals("SOS! A crash has been detected. Location: 37.422, -122.084", senderDemo.demoLogs[0].messageBody)
    }

    @Test
    fun testRealModeMissingPermissionFailsGracefully() = runBlocking {
        val sms = MockSmsController().apply { permission = false }
        val loc = MockLocationController()
        
        // Real mode fails
        val senderReal = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("1234567890"))
        assertEquals(AlertResult.ALERT_FAILED_NO_PERMISSION, senderReal.sendEmergencyAlert())
        assertEquals(0, sms.sendCount)
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
        val loc = MockLocationController(delayMillis = 5000L)
        // Set a short timeout 500ms
        val sender = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("1234567890"), locationTimeoutMillis = 500L)

        val startTime = System.currentTimeMillis()
        val result = sender.sendEmergencyAlert()
        val duration = System.currentTimeMillis() - startTime

        // Even though location timed out, SMS is sent
        assertEquals(AlertResult.ALERT_SENT, result)
        assertEquals(1, sms.sendCount)
        assertEquals("SOS! A crash has been detected. Location unavailable", sms.messagesSent[0])
        
        // Ensure it broke out via withTimeoutOrNull near 500ms (allow some padding)
        assertTrue("Duration was " + duration, duration < 2000L)
    }

    @Test
    fun testMultipleEmergencyContactsBehaveCorrectly() = runBlocking {
        val sms = MockSmsController()
        val loc = MockLocationController(returnLocation = false)
        val sender = AlertSender(AlertMode.REAL_MODE, sms, loc, listOf("111", "222", "333"))

        val result = sender.sendEmergencyAlert()
        assertEquals(AlertResult.ALERT_SENT, result)
        assertEquals(3, sms.sendCount)
    }

    @Test
    fun testEmptyEmergencyContactsIsHandledSafely() = runBlocking {
        val sms = MockSmsController()
        val loc = MockLocationController()
        val sender = AlertSender(AlertMode.REAL_MODE, sms, loc, emptyList())

        val result = sender.sendEmergencyAlert()
        assertEquals(AlertResult.ALERT_FAILED_NO_CONTACTS, result)
        assertEquals(0, sms.sendCount)
    }
}
