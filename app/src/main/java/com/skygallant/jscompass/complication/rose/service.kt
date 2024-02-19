package com.skygallant.jscompass.complication.rose

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.skygallant.jscompass.complication.rose.data.HEADING_KEY
import com.skygallant.jscompass.complication.rose.data.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale


const val TAG: String = "JSCompanionRose"
lateinit var sensorManager: SensorManager
lateinit var FLP: FusedLocationProviderClient
var myLocation: Location? = null
var accelerometerReading = FloatArray(3)
var magnetometerReading = FloatArray(3)
/**
var myLocationCallback = object : LocationCallback() {
    override fun onLocationResult(p0: LocationResult) {
        super.onLocationResult(p0)

        p0.let {
            Log.d(TAG, "ping")
            myLocation = it.lastLocation
        }
    }
}
**/
var myLocationCallback: LocationCallback = object : LocationCallback() {
    @SuppressLint("MissingPermission")
    override fun onLocationResult(locationResult: LocationResult) {
        for (location in locationResult.locations.asReversed()) {
            if (location != null) {
                myLocation = location
                break
            }
        }
        if (myLocation == null) {
            myLocation = FLP.lastLocation.result
        }
    }
}
val myLocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
    .setMinUpdateDistanceMeters(10000f)
    .build()
val myUrgentLocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100)
    .setMaxUpdates(1)
    .build()

class Service : SuspendingComplicationDataSourceService(), SensorEventListener {

    private fun checkPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun doSensors() {
        sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        FLP = LocationServices.getFusedLocationProviderClient(applicationContext)

        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                this,
                magneticField,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        Log.d(TAG, "tracking mag")


        if(checkPermission()) {
            Log.d(TAG, "tracking pos")
            FLP.requestLocationUpdates(myLocationRequest, myLocationCallback, Looper.myLooper())
        }
    }

    private fun shutdownSensors() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "shutdown mag")
        if(checkPermission()) {
            FLP.removeLocationUpdates(myLocationCallback)
            Log.d(TAG, "shutdown pos")
        }
    }


    override fun onComplicationActivated(
        complicationInstanceId: Int,
        type: ComplicationType
    ) {
        Log.d(TAG, "onComplicationActivated(): $complicationInstanceId")

        doSensors()

    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text = "330").build(),
            contentDescription = PlainComplicationText.Builder(text = "Short Text version of Heading.").build()
        )
            .setTapAction(null)
            .build()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.d(TAG, "onComplicationRequest() id: ${request.complicationInstanceId}")

        // Create Tap Action so that the user can trigger an update by tapping the complication.
        val thisDataSource = ComponentName(this, javaClass)
        // We pass the complication id, so we can only update the specific complication tapped.
        val complicationPendingIntent =
            Receiver.getToggleIntent(
                this,
                thisDataSource,
                request.complicationInstanceId
            )

        val number: Int = applicationContext.dataStore.data
            .map { preferences ->
                preferences[HEADING_KEY] ?: 0
            }
            .first()

        val numberText = String.format(Locale.getDefault(), "%d", number)

        return when (request.complicationType) {

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = number.toFloat(),
                min = 0f,
                max = 360f,
                contentDescription = PlainComplicationText
                    .Builder(text = "Ranged Value version of Heading.").build()
            )
                .setText(PlainComplicationText.Builder(text = numberText).build())
                .setTapAction(complicationPendingIntent)
                .build()

            else -> {
                if (Log.isLoggable(TAG, Log.WARN)) {
                    Log.w(TAG, "Unexpected complication type ${request.complicationType}")
                }
                null
            }
        }
    }

    // Called when the complication has been deactivated.
    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        Log.d(TAG, "onComplicationDeactivated(): $complicationInstanceId")

        shutdownSensors()

    }

    override fun onSensorChanged(eventCall: SensorEvent?) {
        if (eventCall != null) {
            val event = eventCall!!
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //
    }

}