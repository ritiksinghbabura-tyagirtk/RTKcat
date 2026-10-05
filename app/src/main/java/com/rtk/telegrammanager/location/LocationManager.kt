package com.rtk.telegrammanager.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

class LocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    @SuppressLint("MissingPermission")
    suspend fun getLocationText(): String {
        if (!hasLocationPermission()) {
            return "❌ Location permission not granted on device."
        }

        return try {
            val cancellationTokenSource = CancellationTokenSource()
            
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).await() ?: fusedLocationClient.lastLocation.await()

            if (location != null) {
                val lat = location.latitude
                val lng = location.longitude
                """
                📍 DEVICE LOCATION

                Latitude: $lat
                Longitude: $lng
                Accuracy: ${location.accuracy}m

                🗺 Google Maps Link:
                https://maps.google.com/?q=$lat,$lng
                """.trimIndent()
            } else {
                "⚠️ Unable to fetch location. Please check if GPS is enabled on the device."
            }
        } catch (e: Exception) {
            "❌ Location Error: ${e.localizedMessage ?: "Unknown error"}"
        }
    }
}
