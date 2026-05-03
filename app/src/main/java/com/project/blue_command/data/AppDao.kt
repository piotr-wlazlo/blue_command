package com.project.blue_command.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(message: CommandMessageEntity)

    @Query(
        """
        SELECT * FROM command_messages
        WHERE groupId = :groupId
        ORDER BY sentAtMillis DESC
        """,
    )
    fun getCommandsForGroup(groupId: String): Flow<List<CommandMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Query("SELECT * FROM combat_groups")
    suspend fun getAllGroups(): List<GroupEntity>

    @Query("SELECT * FROM combat_groups WHERE id = :id LIMIT 1")
    suspend fun getGroupById(id: String): GroupEntity?

    @Insert
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>

    @Query("SELECT COUNT(*) FROM command_messages WHERE groupId = :groupId")
    suspend fun countCommandsForGroup(groupId: String): Int

    @Query(
        """
        SELECT * FROM command_messages
        WHERE groupId = :groupId AND bleMsgId = :bleMsgId AND senderId = :senderId
        LIMIT 1
        """,
    )
    suspend fun getCommandByBleMeta(groupId: String, bleMsgId: Int, senderId: String): CommandMessageEntity?

    @Query("UPDATE command_messages SET receivedAcks = :receivedAcks WHERE id = :id")
    suspend fun updateCommandReceivedAcks(id: String, receivedAcks: Int)
}
