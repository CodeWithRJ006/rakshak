package com.rakshak.core.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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

/**
 * Foreground service that continuously reads Accelerometer and Gyroscope
 * at SENSOR_DELAY_GAME (~50Hz).
 * 
 * Target SDK 35 requires FOREGROUND_SERVICE_TYPE_SPECIAL_USE for custom 
 * long-running processes like continuous crash detection.
 */
class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // 4 seconds rolling window (nanoseconds), max capacity 1000 items
    val accelerometerBuffer = SensorRingBuffer(timeWindowNanos = 4_000_000_000L, maxCapacity = 1000)
    val gyroscopeBuffer = SensorRingBuffer(timeWindowNanos = 4_000_000_000L, maxCapacity = 1000)

    private var isListening = false

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSpecialUse()
        
        if (!isListening) {
            // Handle missing sensors gracefully without crashing
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

        // START_STICKY is intentional: crash detection is a continuous service. If killed by OS memory pressure, 
        // it must be restarted automatically to ensure rider safety.
        return START_STICKY
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
            .setSmallIcon(R.drawable.ic_launcher_foreground) // FALLBACK: using launcher icon for now
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
        
        // event.timestamp is in nanoseconds
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelerometerBuffer.add(event.timestamp, event.values)
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroscopeBuffer.add(event.timestamp, event.values)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for crash detection
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        isListening = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SensorService"
        private const val NOTIFICATION_ID = 1001
    }
}
