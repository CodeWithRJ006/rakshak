package com.rakshak.core.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object P0Pipeline {
    // Change this to your laptop's physical IP for the physical device demo
    private const val DASHBOARD_URL = "http://192.168.1.100:3001/api/incident" 

    suspend fun postIncidentToDashboard(
        peakGs: Float, 
        jerkGs: Float, 
        gyroRads: Float, 
        location: String,
        hmacHash: String,
        summary: String
    ) = withContext(Dispatchers.IO) {
        try {
            Log.i("P0Pipeline", "Preparing payload for dashboard...")
            val json = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("peak_g", peakGs)
                put("jerk_g_s", jerkGs)
                put("gyro_rad_s", gyroRads)
                put("location", location)
                put("hmac_hash", hmacHash)
                put("summary", summary)
            }

            val url = URL(DASHBOARD_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(json.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            Log.i("P0Pipeline", "Dashboard POST Result: $responseCode")
            
            connection.disconnect()
        } catch (e: Exception) {
            Log.e("P0Pipeline", "Failed to post to dashboard. Network error or IP not reachable: ${e.message}")
            // Catch silently as it shouldn't crash the main app if dashboard is offline
        }
    }
}
