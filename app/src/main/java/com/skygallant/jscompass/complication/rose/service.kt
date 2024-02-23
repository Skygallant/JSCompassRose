package com.skygallant.jscompass.complication.rose

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.widget.Toast
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.skygallant.jscompass.complication.rose.data.complicationsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.Locale


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

const val TAG: String = "JSCompanionRose"
class Service : SuspendingComplicationDataSourceService(), SensorEventListener {




    private var mIsSensorUpdateEnabled = false

    companion object {
        lateinit var sensorManager: SensorManager
        lateinit var serviceIntent: Intent

        var accelerometerReading = FloatArray(3)
        var magnetometerReading = FloatArray(3)
    }

    private fun doSensors() {
        sensorManager = this.getSystemService(Context.SENSOR_SERVICE) as SensorManager

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
        mIsSensorUpdateEnabled = true
        Log.d(TAG, "tracking mag")
    }

    private fun shutdownSensors() {
        sensorManager.unregisterListener(this)
        mIsSensorUpdateEnabled = false
        Log.d(TAG, "shutdown mag")
    }


    override fun onComplicationActivated(
        complicationInstanceId: Int,
        type: ComplicationType
    ) {
        Log.d(TAG, "onComplicationActivated(): $complicationInstanceId")


        doSensors()
        serviceIntent = Intent(applicationContext, LocationUpdatesService::class.java)
        startService(serviceIntent)

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
        doSensors()
        runBlocking {
            if (applicationContext.complicationsDataStore.data
                    .map { complicationsDataStore ->
                        complicationsDataStore.restarting
                    }
                    .first()) {
                val text = "Rose Booting"
                val duration = Toast.LENGTH_SHORT
                val toast = Toast.makeText(applicationContext, text, duration)
                toast.show()
                //doSensors()
                serviceIntent = Intent(applicationContext, LocationUpdatesService::class.java)
                startService(serviceIntent)
                applicationContext.complicationsDataStore.updateData {
                    it.copy(
                        restarting = false,
                    )
                }
            }
        }

        // Create Tap Action so that the user can trigger an update by tapping the complication.
        val thisDataSource = ComponentName(this, javaClass)
        // We pass the complication id, so we can only update the specific complication tapped.
        val complicationPendingIntent =
            Receiver.getToggleIntent(
                this,
                thisDataSource,
                request.complicationInstanceId
            )

        val number: Int = applicationContext.complicationsDataStore.data
            .map { complicationsDataStore ->
                complicationsDataStore.headingKey
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
        stopService(serviceIntent)

    }

    override fun onSensorChanged(eventCall: SensorEvent?) {
        if (eventCall != null && mIsSensorUpdateEnabled) {
            if (eventCall.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(
                    eventCall.values,
                    0,
                    accelerometerReading,
                    0,
                    accelerometerReading.size
                )
            } else if (eventCall.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(
                    eventCall.values,
                    0,
                    magnetometerReading,
                    0,
                    magnetometerReading.size
                )
            }
            //shutdownSensors()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //
    }

}