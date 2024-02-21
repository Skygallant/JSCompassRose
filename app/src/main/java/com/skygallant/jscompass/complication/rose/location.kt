package com.skygallant.jscompass.complication.rose

import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.skygallant.jscompass.complication.rose.data.complicationsDataStore
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class LocationUpdatesService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        var x = Location("foo")
        if (Receiver.checkPermission(this)) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener {
                    x = it
                }
            runBlocking {
                applicationContext.complicationsDataStore.updateData {
                    it.copy(
                        myLocation = x,
                        initLoc = true
                    )
                }
            }
        }
        startLocationUpdates()
        return START_NOT_STICKY
    }
/**
    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startLocationUpdates()
    }
**/
    private fun startLocationUpdates() {
        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, TimeUnit.HOURS.toMillis(1)).apply {
                setMinUpdateDistanceMeters(1000f)
            }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                Log.d(TAG, "location results")
                for (location in locationResult.locations) {
                    Log.d(TAG, "Lat: ${location.latitude}, Long: ${location.longitude}")
                    if (location != null) {
                        runBlocking {
                            applicationContext.complicationsDataStore.updateData {
                                it.copy(
                                    myLocation = location
                                )
                            }
                        }
                    }
                }
            }
        }

        if (Receiver.checkPermission(this)) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } else {
            return
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    companion object {
        private const val TAG = "LocationUpdatesService"
    }
}
