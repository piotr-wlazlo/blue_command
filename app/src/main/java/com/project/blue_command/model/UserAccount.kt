package com.project.blue_command.model

data class UserAccount(
    val id: String,
    val username: String,
    val password: String,
    val role: UserRole
)
