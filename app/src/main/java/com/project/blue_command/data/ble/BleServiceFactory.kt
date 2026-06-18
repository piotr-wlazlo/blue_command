package com.project.blue_command.data.ble

import android.content.Context

object BleServiceFactory {
    fun getClassicBleBroadcastService(context: Context): BleBroadcastService {
        return BleBroadcastService(context)
    }

    fun getNordicMeshService(context: Context, hardwareDetector: HardwareDetector): NordicMeshService {
        return NordicMeshService(
            meshRepository = com.project.blue_command.data.ble.mesh.MeshRepository(context),
            proxyConnection = com.project.blue_command.data.ble.mesh.BleMeshProxyManager(context),
            hardwareDetector = hardwareDetector
        )
    }
}