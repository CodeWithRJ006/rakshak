package com.rakshak.core.alert

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

class AndroidLocationController(private val context: Context) : LocationController {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override fun getLocationAsync(): Deferred<LocationData?> {
        val deferred = CompletableDeferred<LocationData?>()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            deferred.complete(null)
            return deferred
        }

        try {
            val task = fusedLocationClient.lastLocation
            if (task.isComplete) {
                if (task.isSuccessful && task.result != null) {
                    deferred.complete(LocationData(task.result.latitude, task.result.longitude))
                } else {
                    deferred.complete(null)
                }
            } else {
                task.addOnCompleteListener { completedTask ->
                    if (completedTask.isSuccessful && completedTask.result != null) {
                        deferred.complete(LocationData(completedTask.result.latitude, completedTask.result.longitude))
                    } else {
                        deferred.complete(null)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            deferred.complete(null)
        }

        return deferred
    }
}
