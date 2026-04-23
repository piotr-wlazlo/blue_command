package com.project.blue_command.logic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.project.blue_command.data.BleBroadcastService
import com.project.blue_command.data.TacticalRadio
import com.project.blue_command.model.CommandMessage
import com.project.blue_command.model.TacticalCommand
import com.project.blue_command.security.EncryptionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class CommandController(application: Application) : AndroidViewModel(application) {
    private val radioService: TacticalRadio =  BleBroadcastService(application)

    private val encryption = EncryptionManager()

    private val _receivedCommands = MutableStateFlow<List<CommandMessage>>(emptyList())
    val receivedCommands: StateFlow<List<CommandMessage>> = _receivedCommands

    private var activeGroupId: String? = null

    private var lastCode: Int? = null
    private var lastTime: Long = 0L

    init {
        viewModelScope.launch {
            radioService.incomingMessages.collect { payload ->
                val code = encryption.decryptFromBytes(payload) ?: return@collect
                val now = System.currentTimeMillis()

                if (code == lastCode && (now - lastTime) < 4000) return@collect

                lastCode = code
                lastTime = now

                TacticalCommand.entries.find { it.code == code }?.let { cmd ->
                    val senderType = "BLE Radio"
                    addMessageToList("radio_incoming", senderType, cmd.label)
                }
            }
        }
    }

    fun setActiveGroup(groupId: String?) {
        if (groupId == activeGroupId) return
        activeGroupId = groupId
        if (groupId != null) {
            radioService.startListening(groupId)
        } else {
            radioService.stopListening()
        }
    }

    fun sendCommand(command: TacticalCommand) {
        val groupId = activeGroupId ?: return

        addMessageToList("me", "Ja (Nadano)", command.label)

        viewModelScope.launch {
            val encrypted = encryption.encryptToBytes(command.code)
            radioService.broadcastCommand(groupId, encrypted)
        }
    }

    private fun addMessageToList(senderId: String, senderName: String, label: String) {
        val newMessage = CommandMessage(
            id = UUID.randomUUID().toString(),
            senderId = senderId,
            senderUsername = senderName,
            commandLabel = label,
            sentAtMillis = System.currentTimeMillis()
        )
        _receivedCommands.value = (listOf(newMessage) + _receivedCommands.value).take(50)
    }

    override fun onCleared() {
        radioService.stopListening()
        super.onCleared()
    }
}