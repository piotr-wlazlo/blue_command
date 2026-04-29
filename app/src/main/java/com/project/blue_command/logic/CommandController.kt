package com.project.blue_command.logic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.project.blue_command.data.SessionRepository
import com.project.blue_command.data.TacticalRadioManager
import com.project.blue_command.model.CombatGroup
import com.project.blue_command.model.CommandMessage
import com.project.blue_command.model.TacticalCommand
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CommandController(application: Application) : AndroidViewModel(application) {
    private val radioManager = TacticalRadioManager(application)
    private val _allCommands = MutableStateFlow<List<CommandMessage>>(emptyList())

    private val authController = AuthController()

    val receivedCommands: StateFlow<List<CommandMessage>> = combine(
        _allCommands,
        SessionRepository.activeGroup
    ) { allCommands, currentGroup ->
        if (currentGroup == null) emptyList()
        else allCommands.filter { it.groupId == currentGroup.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val pendingAcks = ConcurrentHashMap<Int, MutableSet<String>>()
    private val retransmissionJobs = ConcurrentHashMap<Int, Job>()

    private var lastMsgId: Int? = null
    private var lastTime: Long = 0L

    init {
        // Listenery dla zmian stanów
        viewModelScope.launch {
            SessionRepository.activeGroup.collect { group ->
                if (group != null) {
                    radioManager.startListening(group)
                } else {
                    onCleared()
                }
            }
        }

        viewModelScope.launch {
            SessionRepository.currentUser.collect { user ->
                if (user == null) {
                    onCleared()
                }
            }
        }

        viewModelScope.launch {
            radioManager.incomingCommands.collect { payload ->
                val group = SessionRepository.activeGroup.value ?: return@collect
                val user = SessionRepository.currentUser.value ?: return@collect

                if (payload.size < 4) return@collect

                val msgType = payload[0].toInt() and 0xFF
                val msgId = payload[1].toInt() and 0xFF
                val dataByte = payload[2].toInt() and 0xFF
                val senderHash = payload[3].toInt() and 0xFF

                when (msgType) {
                    0x01 -> {
                        val cmdCode = dataByte
                        val now = System.currentTimeMillis()
                        val isDuplicate = (msgId == lastMsgId && (now - lastTime) < 5000)

                        if (!isDuplicate) {
                            lastMsgId = msgId
                            lastTime = now

                            val sender = group.memberIds.mapNotNull { memberId ->
                                // Dostep do bazy danych uzytkownikow, mozliwe, ze to sie zmieni
                                // po wdrozeniu bazy danych
                                authController.getUserById(memberId)
                            }.find { (it.id.hashCode() and 0xFF) == senderHash }

                            val senderName = sender?.username ?: "Nieznany ($senderHash)"
                            val senderId = sender?.id ?: "unknown"

                            TacticalCommand.entries.find { it.code == cmdCode }?.let { cmd ->
                                addMessageToList(senderId, senderName, cmd.label, group.id)
                            }
                        }

                        viewModelScope.launch {
                            delay((0..1000).random().toLong())
                            val userHashByte = (user.id.hashCode() and 0xFF).toByte()
                            val ackPayload = byteArrayOf(0x02, msgId.toByte(), userHashByte)

                            radioManager.broadcastCommand(group, ackPayload)
                        }
                    }

                    0x02 -> {
                        val senderHash = dataByte
                        val pendingForThisMsg = pendingAcks[msgId]

                        if (pendingForThisMsg != null) {
                            val wasRemoved = pendingForThisMsg.removeIf { (it.hashCode() and 0xFF) == senderHash }

                            if (wasRemoved) {
                                _allCommands.value = _allCommands.value.map { msg ->
                                    if (msg.bleMsgId == msgId && msg.senderId == user.id) {
                                        msg.copy(receivedAcks = msg.receivedAcks + 1)
                                    } else { msg }
                                }

                                if (pendingForThisMsg.isEmpty()) {
                                    retransmissionJobs[msgId]?.cancel()
                                    retransmissionJobs.remove(msgId)
                                    pendingAcks.remove(msgId)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun sendCommand(command: TacticalCommand) {
        val group = SessionRepository.activeGroup.value ?: return
        val user = SessionRepository.currentUser.value ?: return

//        val msgId = (0..255).random()
        val msgId = 100
        val expectedAcks = group.memberIds.filter { it != user.id }.toMutableSet()
        pendingAcks[msgId] = expectedAcks

        addMessageToList(user.id, user.username, command.label, group.id, msgId, expectedAcks.size)

        val userHashByte = (user.id.hashCode() and 0xFF).toByte()
        val cmdPayload = byteArrayOf(0x01, msgId.toByte(), command.code.toByte(), userHashByte)

        val job = viewModelScope.launch {
            while (isActive && pendingAcks[msgId]?.isNotEmpty() == true) {
                radioManager.broadcastCommand(group, cmdPayload)
            }
        }
        retransmissionJobs[msgId] = job
    }

    fun setActiveGroup(group: CombatGroup?) {
        SessionRepository.setActiveGroup(group)
    }

    private fun addMessageToList(
        senderId: String, senderName: String, label: String, groupId: String,
        bleMsgId: Int? = null, expectedAcks: Int = 0
    ) {
        val newMessage = CommandMessage(
            id = UUID.randomUUID().toString(),
            senderId = senderId, senderUsername = senderName,
            commandLabel = label, sentAtMillis = System.currentTimeMillis(),
            groupId = groupId, bleMsgId = bleMsgId,
            expectedAcks = expectedAcks, receivedAcks = 0
        )
        _allCommands.value = (listOf(newMessage) + _allCommands.value).take(100)
    }

    override fun onCleared() {
        radioManager.stopListening()
        retransmissionJobs.values.forEach { it.cancel() }
        super.onCleared()
    }
}