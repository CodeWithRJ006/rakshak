package com.rakshak.core.readiness

import com.rakshak.core.alert.AlertSenderTest.MockLocationController
import com.rakshak.core.alert.AlertSenderTest.MockSmsController
import com.rakshak.core.detector.CrashDetector
import com.rakshak.core.detector.DetectorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class P0PipelineTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testCountdownExpirationFiresAlertPath() = runTest(testDispatcher) {
        val detector = CrashDetector()
        val sms = MockSmsController()
        val loc = MockLocationController()
        loc.deferred.complete(null)
        val pipeline = P0Pipeline(detector, sms, loc, { listOf("123") }, dispatcher = testDispatcher)
        pipeline.countdownDurationMs = 2_000L // 2 seconds for test

        val job = launch(testDispatcher) { pipeline.collectStateFlow() }
        testScheduler.advanceUntilIdle()

        // Trigger incident -> CONFIRMED
        detector.triggerExternalIncident(100L)
        testScheduler.advanceTimeBy(100L)

        // Countdown active
        assertEquals(DetectorState.CONFIRMED, detector.currentState)
        assertEquals(0, pipeline.alertCount)

        // Let countdown expire (2 seconds = 2000ms)
        testScheduler.advanceTimeBy(3_000L)
        testScheduler.advanceUntilIdle()

        // Assert existing alert path fired
        assertEquals(1, pipeline.alertCount)
        assertEquals(DetectorState.ALERTED, detector.currentState)

        job.cancel()
    }

    @Test
    fun testTapWithinWindowCancelsAlertAndReturnsToMonitoring() = runTest(testDispatcher) {
        val detector = CrashDetector()
        val sms = MockSmsController()
        val loc = MockLocationController()
        loc.deferred.complete(null)
        val pipeline = P0Pipeline(detector, sms, loc, { listOf("123") }, dispatcher = testDispatcher)
        pipeline.countdownDurationMs = 10_000L

        val job = launch(testDispatcher) { pipeline.collectStateFlow() }
        testScheduler.advanceUntilIdle()

        // Trigger incident -> CONFIRMED
        detector.triggerExternalIncident(100L)
        testScheduler.advanceTimeBy(2_000L) // 2 seconds into 10s countdown

        // Rider taps "I'M GOOD"
        val cancelled = pipeline.cancelByRider()
        assertTrue(cancelled)

        testScheduler.advanceUntilIdle()

        // Assert alert was CANCELLED, NO SMS sent, state returned to MONITORING
        assertEquals(0, pipeline.alertCount)
        assertEquals(DetectorState.MONITORING, detector.currentState)

        job.cancel()
    }

    @Test
    fun testRapidDoubleTapDoesNotCrashOrDoubleCancel() = runTest(testDispatcher) {
        val detector = CrashDetector()
        val sms = MockSmsController()
        val loc = MockLocationController()
        loc.deferred.complete(null)
        val pipeline = P0Pipeline(detector, sms, loc, { listOf("123") }, dispatcher = testDispatcher)
        pipeline.countdownDurationMs = 10_000L

        val job = launch(testDispatcher) { pipeline.collectStateFlow() }
        testScheduler.advanceUntilIdle()

        detector.triggerExternalIncident(100L)
        testScheduler.advanceTimeBy(1_000L)

        // Rapid double-tap
        val firstTap = pipeline.cancelByRider()
        val secondTap = pipeline.cancelByRider()

        assertTrue(firstTap)
        assertFalse(secondTap) // Second tap safely ignored, no crash or duplicate state change

        testScheduler.advanceUntilIdle()

        assertEquals(0, pipeline.alertCount)
        assertEquals(DetectorState.MONITORING, detector.currentState)

        job.cancel()
    }
}
