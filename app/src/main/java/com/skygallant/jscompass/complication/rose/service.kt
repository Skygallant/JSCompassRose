package com.skygallant.jscompass.complication.rose

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.skygallant.jscompass.complication.rose.data.HEADING_KEY
import com.skygallant.jscompass.complication.rose.data.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale


const val TAG: String = "JSCompanionRose"
lateinit var sensorManager: SensorManager
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

/**
val myLocationCallback: LocationCallback = object : LocationCallback() {
    override fun onLocationResult(locationResult: LocationResult) {
        for (location in locationResult.locations) {
            if (location != null) {
                Service.updateLocation(location)
            }
        }
    }
}
**/


class Service : SuspendingComplicationDataSourceService(), SensorEventListener {

    companion object {
        fun checkPermission(thisContext: Context): Boolean {
            return ActivityCompat.checkSelfPermission(
                thisContext,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
        val myWorkRequest = OneTimeWorkRequestBuilder<BgLocationWorker>()
            .build()
    }

    private fun doSensors() {
        sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

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
    }

    private fun shutdownSensors() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "shutdown mag")
    }


    override fun onComplicationActivated(
        complicationInstanceId: Int,
        type: ComplicationType
    ) {
        Log.d(TAG, "onComplicationActivated(): $complicationInstanceId")

        WorkManager.getInstance(applicationContext).enqueue(myWorkRequest)
        doSensors()

    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return RangedValueComplicationData.Builder(
            value = 330f,
            min = 0f,
            max = 360f,
            contentDescription = PlainComplicationText
                .Builder(text = "Ranged Value version of Heading.").build()
        )
            .setText(PlainComplicationText.Builder(text = "330").build())
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