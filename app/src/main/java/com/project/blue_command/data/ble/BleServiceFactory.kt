package com.project.blue_command.data.ble

import android.content.Context

object BleServiceFactory {
    fun getClassicBleBroadcastService(context: Context): BleBroadcastService {
        return BleBroadcastService(context)
    }
}