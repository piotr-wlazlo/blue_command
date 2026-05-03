package com.project.blue_command.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "command_messages")
data class CommandMessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderUsername: String,
    val commandLabel: String,
    val sentAtMillis: Long,
    val groupId: String,
    val bleMsgId: Int? = null,
    val expectedAcks: Int = 0,
    val receivedAcks: Int = 0,
)

@Entity(tableName = "combat_groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val groupKeyBase64: String,
    val memberIds: List<String>,
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val password: String,
    val roleName: String,
)

class StringListConverter {
    @TypeConverter
    fun fromString(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun fromList(list: List<String>): String = list.joinToString(",")
}
