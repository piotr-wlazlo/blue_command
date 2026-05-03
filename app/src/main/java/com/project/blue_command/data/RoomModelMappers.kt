package com.project.blue_command.data

import com.project.blue_command.model.CombatGroup
import com.project.blue_command.model.CommandMessage
import com.project.blue_command.model.UserAccount
import com.project.blue_command.model.UserRole

internal fun GroupEntity.toCombatGroup(): CombatGroup =
    CombatGroup(id, name, groupKeyBase64, memberIds.toMutableList())

internal fun CombatGroup.toGroupEntity(): GroupEntity =
    GroupEntity(id, name, groupKeyBase64, memberIds.toList())

internal fun UserEntity.toUserAccount(): UserAccount =
    UserAccount(id, username, password, UserRole.valueOf(roleName))

internal fun UserAccount.toUserEntity(): UserEntity =
    UserEntity(id, username, password, role.name)

internal fun CommandMessageEntity.toCommandMessage(): CommandMessage =
    CommandMessage(
        id = id,
        senderId = senderId,
        senderUsername = senderUsername,
        commandLabel = commandLabel,
        sentAtMillis = sentAtMillis,
        groupId = groupId,
        bleMsgId = bleMsgId,
        expectedAcks = expectedAcks,
        receivedAcks = receivedAcks,
    )

internal fun CommandMessage.toEntity(): CommandMessageEntity =
    CommandMessageEntity(
        id = id,
        senderId = senderId,
        senderUsername = senderUsername,
        commandLabel = commandLabel,
        sentAtMillis = sentAtMillis,
        groupId = groupId,
        bleMsgId = bleMsgId,
        expectedAcks = expectedAcks,
        receivedAcks = receivedAcks,
    )
