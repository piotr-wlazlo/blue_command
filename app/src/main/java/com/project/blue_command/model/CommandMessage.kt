package com.project.blue_command.model

data class CommandMessage(
    val id: String,
    val senderId: String,
    val senderUsername: String,
    val commandLabel: String,
    val sentAtMillis: Long
)
