package com.rakshak.core.readiness

import com.rakshak.core.alert.AlertMode
import com.rakshak.core.alert.AlertSender
import com.rakshak.core.alert.LocationController
import com.rakshak.core.alert.SmsController
import com.rakshak.core.detector.CrashDetector
import com.rakshak.core.detector.DetectorState
import com.rakshak.core.mode.AppMode
import com.rakshak.core.mode.ModeManager
import com.rakshak.core.summary.SummaryGenerator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.rakshak.core.log.HMACIncidentLogger
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import android.util.Log

class P0Pipeline(
    private val detector: CrashDetector,
    private val smsController: SmsController,
    private val locationController: LocationController,
    private val contactProvider: () -> List<String>,
    private val incidentLogger: HMACIncidentLogger? = null
) {
    var currentIncidentHandled = false
        private set

    var alertCount = 0
        private set
        
    private val pipelineScope = CoroutineScope(Dispatchers.Default)
        
    suspend fun collectStateFlow() {
        detector.stateFlow.collectLatest { state ->
            P0PipelineStatus.updateDetectorState(state)

            if (state == DetectorState.MONITORING) {
                currentIncidentHandled = false
            }

            if (state == DetectorState.CONFIRMED && !currentIncidentHandled) {
                currentIncidentHandled = true
                
                val mode = ModeManager.currentMode
                val alertMode = if (mode is AppMode.REAL) AlertMode.REAL_MODE else AlertMode.DEMO_MODE
                
                val alertSender = AlertSender(alertMode, smsController, locationController, contactProvider())
                val result = alertSender.sendEmergencyAlert()
                
                pipelineScope.launch {
                    val locationDeferred = locationController.getLocationAsync()
                    val locationData = locationDeferred.await()
                    val locString = if (locationData != null) "${locationData.latitude}, ${locationData.longitude}" else "Unknown"

                    val timestamp = System.currentTimeMillis()
                    val alertStatus = result.name
                    val movementResult = "POST_CRASH_STILLNESS" // Simulating movement result
                    
                    val summary = SummaryGenerator.generateSummary(
                        peakGs = 12.5f,
                        jerkGs = 850f,
                        gyroRads = 12.0f,
                        location = locString,
                        alertStatus = alertStatus,
                        movementResult = movementResult
                    )
                    
                    val jsonObj = JSONObject()
                    jsonObj.put("timestamp", timestamp)
                    jsonObj.put("location", locString)
                    jsonObj.put("alertStatus", alertStatus)
                    jsonObj.put("movementResult", movementResult)
                    jsonObj.put("summary", summary)
                    
                    var chainIntegrity = "UNKNOWN"
                    if (incidentLogger != null) {
                        val verifyResult = incidentLogger.verifyChain()
                        chainIntegrity = if (verifyResult.isIntact) "INTACT" else "BROKEN"
                    }
                    jsonObj.put("chainIntegrity", chainIntegrity)

                    val payloadJson = jsonObj.toString() ?: "{}"

                    // Log locally via HMAC
                    incidentLogger?.logIncident(payloadJson)

                    // POST to embedded server (laptop dashboard)
                    try {
                        // Using your laptop's local IP for the loaner phone
                        val url = URL("http://192.168.1.100:3001/api/incident") 
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.doOutput = true
                        
                        val out = OutputStreamWriter(conn.outputStream)
                        out.write(payloadJson)
                        out.flush()
                        out.close()
                        
                        val responseCode = conn.responseCode
                        Log.d("P0Pipeline", "POST to dashboard, response code: $responseCode")
                    } catch (e: Exception) {
                        Log.e("P0Pipeline", "Failed to POST to dashboard", e)
                    }
                }
                
                alertCount++
                
                P0PipelineStatus.updateLastAlertResult(result)
                detector.markAlerted(System.nanoTime())
            }
        }
    }
}
