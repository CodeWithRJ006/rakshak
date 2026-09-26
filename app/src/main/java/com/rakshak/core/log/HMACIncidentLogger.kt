package com.rakshak.core.log

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HMACIncidentLogger(private val context: Context) {

    // Hackathon dummy key. In production, securely derive or store in Keystore.
    private val secretKey = "RAKSHAK_HMAC_SECRET".toByteArray()

    suspend fun logIncident(incidentData: String) = withContext(Dispatchers.IO) {
        try {
            val checker = IncidentLogIntegrityChecker(context)
            val logDir = checker.getLogDirectory()
            if (!logDir.exists()) logDir.mkdirs()

            val logFile = File(logDir, IncidentLogIntegrityChecker.LOG_FILE_NAME)
            
            // Format: TIMESTAMP | DATA | HMAC
            val timestamp = System.currentTimeMillis()
            val payload = "$timestamp|$incidentData"
            
            val mac = Mac.getInstance("HmacSHA256")
            val secretKeySpec = SecretKeySpec(secretKey, "HmacSHA256")
            mac.init(secretKeySpec)
            
            val hmacBytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
            val hmacHex = hmacBytes.joinToString("") { "%02x".format(it) }
            
            val entry = "$payload|$hmacHex\n"
            
            logFile.appendText(entry, Charsets.UTF_8)
            Log.d("HMACLogger", "Incident logged securely: $payload")
            
        } catch (e: Exception) {
            Log.e("HMACLogger", "Failed to log incident: $e")
        }
    }
}
