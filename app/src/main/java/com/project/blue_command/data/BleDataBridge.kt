package com.project.blue_command.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object BleDataBridge {
    private val _incomingCommands = MutableSharedFlow<ByteArray>(extraBufferCapacity = 10)
    val incomingCommands: SharedFlow<ByteArray> = _incomingCommands

    fun emit(data: ByteArray) {
        _incomingCommands.tryEmit(data)
    }
}