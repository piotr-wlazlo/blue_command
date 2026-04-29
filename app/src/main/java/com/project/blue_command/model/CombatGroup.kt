package com.project.blue_command.model

data class CombatGroup(
    val id: String,
    val name: String,
    val groupKeyBase64: String,
    val memberIds: MutableList<String> = mutableListOf()
)
