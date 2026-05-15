package com.project.blue_command.data.ble

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object BleDataBridge {
    private val _incomingPayloads = MutableSharedFlow<ByteArray>(extraBufferCapacity = 10)
    val incomingPayloads: SharedFlow<ByteArray> = _incomingPayloads

    fun emit(data: ByteArray) {
        _incomingPayloads.tryEmit(data)
    }
}