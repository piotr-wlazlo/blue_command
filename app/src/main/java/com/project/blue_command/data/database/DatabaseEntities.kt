package com.project.blue_command.data.database

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
    val expectedAckMemberIds: List<String> = emptyList(),
    val acknowledgedMemberIds: List<String> = emptyList(),
    val isFailed: Boolean = false,
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

@Entity(tableName = "user_session")
data class UserSessionEntity(
    @PrimaryKey val id: Int = 1,
    val userId: String,
    val loggedAtMillis: Long,
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
