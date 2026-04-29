package com.project.blue_command.data

import android.content.Context
import com.project.blue_command.model.CombatGroup
import kotlinx.coroutines.flow.SharedFlow

class NordicBleMeshService(val context: Context) : TacticalRadio {
    override val incomingCommands: SharedFlow<ByteArray>
        get() = TODO("Not yet implemented")

    override fun startListening(group: CombatGroup) {
        TODO("Not yet implemented")
    }

    override fun stopListening() {
        TODO("Not yet implemented")
    }

    override suspend fun broadcastCommand(
        group: CombatGroup,
        payload: ByteArray
    ) {
        TODO("Not yet implemented")
    }
}