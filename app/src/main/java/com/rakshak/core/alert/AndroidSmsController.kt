package com.rakshak.core.alert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

class AndroidSmsController(
    private val context: Context,
    private val smsManagerProvider: () -> SmsManager = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }
) : SmsController {

    override fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    override fun hasService(): Boolean {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return false
        if (telephonyManager.simState != TelephonyManager.SIM_STATE_READY) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (telephonyManager.dataNetworkType == TelephonyManager.NETWORK_TYPE_UNKNOWN && 
                telephonyManager.voiceNetworkType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                return false
            }
        } else {
            @Suppress("DEPRECATION")
            if (telephonyManager.networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                return false
            }
        }
        return true
    }

    override fun sendSms(destinationAddress: String, text: String): Boolean {
        if (!hasPermission() || !hasService()) {
            return false
        }

        return try {
            val smsManager = smsManagerProvider()
            val parts = smsManager.divideMessage(text)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(destinationAddress, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(destinationAddress, null, text, null, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
