package com.project.blue_command.data.ble

import kotlinx.coroutines.flow.SharedFlow

interface BleService {
    val incomingPayloads: SharedFlow<ByteArray>
    fun startListening()
    fun stopListening()
    suspend fun broadcastPayload(payload: ByteArray)
}