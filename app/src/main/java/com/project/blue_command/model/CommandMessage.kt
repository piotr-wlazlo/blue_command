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
    val receivedAcks: Int = 0,
    val expectedAckMemberIds: List<String> = emptyList(),
    val acknowledgedMemberIds: List<String> = emptyList(),
    val isFailed: Boolean = false,
) {
    val isFullyConfirmed: Boolean
        get() = expectedAcks > 0 && receivedAcks >= expectedAcks

    val statusLabel: String?
        get() = if (expectedAcks <= 0) {
            null
        } else if (isFailed) {
            "failed"
        } else if (isFullyConfirmed) {
            "approved"
        } else {
            "partial approved"
        }
}