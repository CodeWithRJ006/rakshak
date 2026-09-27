package com.rakshak.core.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rakshak.R
import com.rakshak.core.alert.AndroidLocationController
import com.rakshak.core.alert.AndroidSmsController
import com.rakshak.core.alert.TestContactConfig
import com.rakshak.core.log.HMACIncidentLogger
import com.rakshak.core.alert.LocationController
import com.rakshak.core.alert.SmsController
import com.rakshak.core.detector.CrashDetector
import com.rakshak.core.readiness.P0Pipeline
import com.rakshak.core.readiness.P0PipelineStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    val accelerometerBuffer = SensorRingBuffer(timeWindowNanos = 4_000_000_000L, maxCapacity = 1000)
    val gyroscopeBuffer = SensorRingBuffer(timeWindowNanos = 4_000_000_000L, maxCapacity = 1000)

    private var isListening = false
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        private val detector = CrashDetector()
    private var tts: TextToSpeech? = null
    lateinit var smsController: SmsController
    lateinit var locationController: LocationController
    private var pipeline: P0Pipeline? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (!::smsController.isInitialized) smsController = AndroidSmsController(this)
        if (!::locationController.isInitialized) locationController = AndroidLocationController(this)

        P0PipelineStatus.updateServiceRunning(true)
        P0PipelineStatus.updateDetectorState(detector.currentState)

        serviceScope.launch {
            accelerometerBuffer.flow.collect { buffer ->
                if (buffer.isNotEmpty()) {
                    detector.processSensorBuffers(buffer, gyroscopeBuffer.flow.value)
                }
            }
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
        
        serviceScope.launch {
            detector.stateFlow.collect { state ->
                if (state == com.rakshak.core.detector.DetectorState.CONFIRMING) {
                    tts?.speak("Crash candidate detected. Verifying...", TextToSpeech.QUEUE_FLUSH, null, null)
                } else if (state == com.rakshak.core.detector.DetectorState.CONFIRMED) {
                    tts?.speak("Emergency confirmed. Rakshak is sending an SOS to your contacts.", TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        }

        pipeline = P0Pipeline(detector, smsController, locationController, { listOf(TestContactConfig.testContactNumber) }, HMACIncidentLogger(this))
        serviceScope.launch {
            pipeline?.collectStateFlow()
        }
    }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SIMULATE_CRASH -> detector.triggerExternalIncident(System.nanoTime())
            ACTION_INJECT_TRACE_FRONTAL -> injectSyntheticTrace("FRONTAL")
            ACTION_INJECT_TRACE_EJECTION -> injectSyntheticTrace("EJECTION")
            ACTION_CANCEL_ALERT -> pipeline?.cancelByRider()
        }
        startForegroundSpecialUse()
        
        if (!isListening) {
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            } else {
                Log.w(TAG, "Accelerometer not available on this device!")
            }

            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
            } else {
                Log.w(TAG, "Gyroscope not available on this device!")
            }
            isListening = true
        }

        return START_STICKY
    }

        private fun injectSyntheticTrace(type: String) {
        serviceScope.launch {
            val t0 = System.nanoTime()
            val accelTrace = mutableListOf<SensorData>()
            val gyroTrace = mutableListOf<SensorData>()

            accelTrace.add(SensorData(t0, floatArrayOf(0f, 9.8f, 0f)))
            gyroTrace.add(SensorData(t0, floatArrayOf(0.1f, 0.1f, 0.1f)))
            
            val t1 = t0 + 100_000_000L 
            val t2 = t1 + 100_000_000L 
            
            if (type == "FRONTAL") {
                // High deceleration in Y/Z, low initial gyro (hit a wall)
                accelTrace.add(SensorData(t1, floatArrayOf(0f, 60f, -40f)))
                accelTrace.add(SensorData(t2, floatArrayOf(0f, 80f, -60f)))
                gyroTrace.add(SensorData(t2, floatArrayOf(1f, 0.5f, 2f)))
            } else {
                // Ejection: Chaotic acceleration, massive gyro spin
                accelTrace.add(SensorData(t1, floatArrayOf(50f, -30f, 40f)))
                accelTrace.add(SensorData(t2, floatArrayOf(70f, -50f, 60f)))
                gyroTrace.add(SensorData(t2, floatArrayOf(12f, -8f, 15f)))
            }

            var t = t2
            while (t - t2 <= 2_500_000_000L) {
                t += 100_000_000L
                accelTrace.add(SensorData(t, floatArrayOf(0f, 9.8f, 0f)))
            }
            detector.processSensorBuffers(accelTrace, gyroTrace)
        }
    }



    private fun startForegroundSpecialUse() {
        val channelId = "rakshak_sensor_channel"
        val channelName = "Crash Detection Sensor"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps crash detection sensors running"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Rakshak Crash Detection")
            .setContentText("Monitoring sensors for safety...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) 
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelerometerBuffer.add(event.timestamp, event.values)
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroscopeBuffer.add(event.timestamp, event.values)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        isListening = false
        serviceScope.cancel()
        P0PipelineStatus.updateServiceRunning(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SensorService"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_SIMULATE_CRASH = "com.rakshak.action.SIMULATE_CRASH"
                const val ACTION_INJECT_TRACE_FRONTAL = "com.rakshak.action.INJECT_TRACE_FRONTAL"
        const val ACTION_INJECT_TRACE_EJECTION = "com.rakshak.action.INJECT_TRACE_EJECTION"
        const val ACTION_CANCEL_ALERT = "com.rakshak.action.CANCEL_ALERT"
    }
}






