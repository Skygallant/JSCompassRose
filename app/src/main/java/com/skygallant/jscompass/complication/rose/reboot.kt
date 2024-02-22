package com.skygallant.jscompass.complication.rose

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.skygallant.jscompass.complication.rose.data.complicationsDataStore
import kotlinx.coroutines.runBlocking

class Reboot : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            runBlocking {
                context.complicationsDataStore.updateData {
                    it.copy(
                        restarting = true,
                    )
                }
            }
        }
    }

}