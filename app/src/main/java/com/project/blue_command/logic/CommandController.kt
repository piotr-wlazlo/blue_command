package com.project.blue_command.logic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.blue_command.data.database.LocalAppDatabase
import com.project.blue_command.data.SessionRepository
import com.project.blue_command.data.TacticalRadioManager
import com.project.blue_command.data.database.toCommandMessage
import com.project.blue_command.data.database.toEntity
import com.project.blue_command.model.CombatGroup
import com.project.blue_command.model.CommandMessage
import com.project.blue_command.model.TacticalCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CommandController(
    application: Application,
    private val authController: AuthController
) : AndroidViewModel(application) {

    private val radioManager = TacticalRadioManager(application)
    private val appDao = LocalAppDatabase.getDatabase(application).appDao()

    val receivedCommands: StateFlow<List<CommandMessage>> =
        SessionRepository.activeGroup
            .flatMapLatest { group ->
                if (group == null) {
                    flowOf(emptyList())
                } else {
                    appDao.getCommandsForGroup(group.id).map { entities ->
                        entities.map { it.toCommandMessage() }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val pendingAcks = ConcurrentHashMap<Int, MutableSet<String>>()
    private val retransmissionJobs = ConcurrentHashMap<Int, Job>()
    private var nextOutgoingBleMsgId = 100

    private var lastMsgId: Int? = null
    private var lastTime: Long = 0L

    init {
        viewModelScope.launch {
            SessionRepository.activeGroup.collect { group ->
                if (group != null) {
                    radioManager.startListening()
                } else {
                    radioManager.stopListening()
                }
            }
        }

        viewModelScope.launch {
            SessionRepository.currentUser.collect { user ->
                if (user == null) {
                    radioManager.stopListening()
                }
            }
        }

        viewModelScope.launch {
            radioManager.incomingCommands.collect { payload ->
                val group = SessionRepository.activeGroup.value ?: return@collect
                val user = SessionRepository.currentUser.value ?: return@collect

                if (payload.size < 3) return@collect

                val msgType = payload[0].toInt() and 0xFF
                val msgId = payload[1].toInt() and 0xFF
                val dataByte = payload[2].toInt() and 0xFF

                when (msgType) {
                    0x01 -> {
                        if (payload.size < 4) return@collect
                        val senderHash = payload[3].toInt() and 0xFF
                        val cmdCode = dataByte
                        val now = System.currentTimeMillis()
                        val isDuplicate = (msgId == lastMsgId && (now - lastTime) < 5000)

                        if (!isDuplicate) {
                            lastMsgId = msgId
                            lastTime = now

                            val sender = group.memberIds.mapNotNull { memberId ->
                                authController.getUserById(memberId)
                            }.find { (it.id.hashCode() and 0xFF) == senderHash }

                            val senderName = sender?.username ?: "($senderHash)"
                            val senderId = sender?.id ?: "commander"

                            TacticalCommand.entries.find { it.code == cmdCode }?.let { cmd ->
                                addMessageToList(senderId, senderName, cmd.label, group.id)
                            }
                        }

                        viewModelScope.launch {
                            delay((0..1000).random().toLong())
                            val userHashByte = (user.id.hashCode() and 0xFF).toByte()
                            val ackPayload = byteArrayOf(0x02, msgId.toByte(), userHashByte)
                            println("BLE_ACK: Wysyłam ACK: msgId=$msgId, from=${user.username}, hash=${userHashByte.toInt() and 0xFF}")
                            repeat(2) {
                                radioManager.sendCommand(ackPayload)
                            }
                        }
                    }

                    0x02 -> {
                        val ackSenderHash = dataByte
                        val pendingForThisMsg = pendingAcks[msgId]
                        println("BLE_ACK: Odebrano ACK: msgId=$msgId, senderHash=$ackSenderHash, pending=${pendingForThisMsg?.size ?: 0}")

                        if (pendingForThisMsg != null) {
                            val acknowledgedUserId = pendingForThisMsg.firstOrNull {
                                (it.hashCode() and 0xFF) == ackSenderHash
                            }
                            if (acknowledgedUserId == null) {
                                println("BLE_ACK: ACK nierozpoznany w pendingAcks: msgId=$msgId, senderHash=$ackSenderHash")
                            }
                            val wasRemoved = acknowledgedUserId != null && pendingForThisMsg.remove(acknowledgedUserId)

                            if (wasRemoved) {
                                println("BLE_ACK: ACK dopasowany: msgId=$msgId, userId=$acknowledgedUserId, pozostało=${pendingForThisMsg.size}")
                                viewModelScope.launch(Dispatchers.IO) {
                                    val entity = appDao.getCommandByBleMeta(group.id, msgId, user.id)
                                        ?: return@launch
                                    appDao.insertCommand(
                                        entity.copy(
                                            receivedAcks = entity.receivedAcks + 1,
                                            acknowledgedMemberIds = (
                                                entity.acknowledgedMemberIds + acknowledgedUserId
                                            ).distinct(),
                                            isFailed = false,
                                        ),
                                    )
                                }

                                if (pendingForThisMsg.isEmpty()) {
                                    println("BLE_ACK: Komenda w pełni potwierdzona: msgId=$msgId")
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

        val msgId = consumeNextBleMsgId()
        val expectedAcks = group.memberIds.filter { it != user.id }.toMutableSet()
        pendingAcks[msgId] = expectedAcks

        addMessageToList(
            senderId = user.id,
            senderName = user.username,
            label = command.label,
            groupId = group.id,
            bleMsgId = msgId,
            expectedAcks = expectedAcks.size,
            expectedAckMemberIds = expectedAcks.toList(),
        )

        val userHashByte = (user.id.hashCode() and 0xFF).toByte()
        val cmdPayload = byteArrayOf(0x01, msgId.toByte(), command.code.toByte(), userHashByte)

        val job = viewModelScope.launch {
            var counterCommands = 0
            while (isActive && pendingAcks[msgId]?.isNotEmpty() == true) {
                radioManager.sendCommand(cmdPayload)
                counterCommands++
                if (counterCommands == 8) {
                    markCommandAsFailed(group.id, msgId, user.id)
                    pendingAcks.remove(msgId)
                    retransmissionJobs.remove(msgId)
                    return@launch
                }
                delay(3000)
            }
        }
        retransmissionJobs[msgId] = job
    }

    fun setActiveGroup(group: CombatGroup?) {
        SessionRepository.setActiveGroup(group)
    }

    private fun addMessageToList(
        senderId: String,
        senderName: String,
        label: String,
        groupId: String,
        bleMsgId: Int? = null,
        expectedAcks: Int = 0,
        expectedAckMemberIds: List<String> = emptyList(),
    ) {
        val newMessage = CommandMessage(
            id = UUID.randomUUID().toString(),
            senderId = senderId,
            senderUsername = senderName,
            commandLabel = label,
            sentAtMillis = System.currentTimeMillis(),
            groupId = groupId,
            bleMsgId = bleMsgId,
            expectedAcks = expectedAcks,
            receivedAcks = 0,
            expectedAckMemberIds = expectedAckMemberIds,
            acknowledgedMemberIds = emptyList(),
            isFailed = false,
        )
        viewModelScope.launch {
            appDao.insertCommand(newMessage.toEntity())
        }
    }

    private fun consumeNextBleMsgId(): Int {
        val current = nextOutgoingBleMsgId
        nextOutgoingBleMsgId++
        if (nextOutgoingBleMsgId > 255) {
            nextOutgoingBleMsgId = 100
        }
        return current
    }

    private fun markCommandAsFailed(groupId: String, msgId: Int, senderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = appDao.getCommandByBleMeta(groupId, msgId, senderId) ?: return@launch
            appDao.insertCommand(entity.copy(isFailed = true))
        }
    }

    override fun onCleared() {
        radioManager.stopListening()
        retransmissionJobs.values.forEach { it.cancel() }
        super.onCleared()
    }

    companion object {
        fun factory(authController: AuthController): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = authController.getApplication<Application>()
                    @Suppress("UNCHECKED_CAST")
                    return CommandController(app, authController) as T
                }
            }
    }
}
