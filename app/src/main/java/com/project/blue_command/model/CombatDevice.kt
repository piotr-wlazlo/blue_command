package com.project.blue_command.model

data class CombatDevice(
    val id: String,
    val name: String,
    var assignedSoldierId: String? = null
)
