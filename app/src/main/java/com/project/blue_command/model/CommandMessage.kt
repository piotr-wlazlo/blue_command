package com.project.blue_command.model

data class CommandMessage(
    val id: String,
    val senderId: String,
    val senderUsername: String,
    val commandLabel: String,
    val sentAtMillis: Long,
    val groupId: String,
    val bleMsgId: Int? = null,
    val expectedAcks: Int = 0,
    val receivedAcks: Int = 0
) {
    val isFullyConfirmed: Boolean
        get() = expectedAcks > 0 && receivedAcks >= expectedAcks
}