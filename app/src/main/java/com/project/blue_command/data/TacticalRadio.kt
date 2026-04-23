package com.project.blue_command.data

import kotlinx.coroutines.flow.SharedFlow

interface TacticalRadio {
    val incomingMessages: SharedFlow<ByteArray>
    fun startListening(groupId: String)
    fun stopListening()
    suspend fun broadcastCommand(groupId: String, payload: ByteArray)
}