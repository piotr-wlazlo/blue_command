package com.project.blue_command.data.ble

import android.content.Context
import kotlinx.coroutines.flow.SharedFlow

class NordicBleMeshService(val context: Context) : BleService {
    override val incomingPayloads: SharedFlow<ByteArray>
        get() = TODO("Not yet implemented")

    override fun startListening() {
        TODO("Not yet implemented")
    }

    override fun stopListening() {
        TODO("Not yet implemented")
    }

    override suspend fun broadcastPayload(payload: ByteArray) {
        TODO("Not yet implemented")
    }
}