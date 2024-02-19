package com.skygallant.jscompass.complication.rose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

class BgLocationWorker(context: Context, param: WorkerParameters) :
    CoroutineWorker(context, param) {
    companion object {
        // unique name for the work
        val workName = "BgLocationWorker"
        private const val TAG = "BackgroundLocationWork"
    }

    private val locationClient = LocationServices.getFusedLocationProviderClient(context)

    override suspend fun doWork(): Result {
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "no perm")
            return Result.failure()
        }
        val locationResult = locationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token,
        )
        // Wait for the location result
        try {
            val location = locationResult.await()
            location?.let {
                myLocation = location
                Log.d(
                    TAG,
                    "Current Location = [lat : ${location.latitude}, lng : ${location.longitude}]",
                )
                val text = "Rose Found"
                val duration = Toast.LENGTH_SHORT
                val toast = Toast.makeText(applicationContext, text, duration)
                toast.show()
                return Result.success()
            }
            // Log failure if location is null
            val text = "Rose Search"
            val duration = Toast.LENGTH_SHORT
            val toast = Toast.makeText(applicationContext, text, duration)
            toast.show()
            WorkManager.getInstance(applicationContext).enqueue(Service.myWorkRequest)
            return Result.failure()
        } catch (exception: Exception) {
            // Handle exception if location retrieval fails
            val text = "Rose Error"
            val duration = Toast.LENGTH_SHORT
            val toast = Toast.makeText(applicationContext, text, duration)
            toast.show()
            return Result.failure()
        }
    }

}