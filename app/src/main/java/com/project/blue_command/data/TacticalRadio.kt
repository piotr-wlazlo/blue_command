package com.project.blue_command.data

import com.project.blue_command.model.CombatGroup
import kotlinx.coroutines.flow.SharedFlow

interface TacticalRadio {
    val incomingCommands: SharedFlow<ByteArray>
    fun startListening(group: CombatGroup)
    fun stopListening()
    suspend fun broadcastCommand(group: CombatGroup, payload: ByteArray)
}