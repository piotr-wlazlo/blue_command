package com.project.blue_command.logic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.project.blue_command.data.SessionRepository
import com.project.blue_command.model.CombatDevice
import com.project.blue_command.model.CombatGroup
import com.project.blue_command.model.CommandMessage
import com.project.blue_command.model.UserAccount
import com.project.blue_command.model.UserRole
import com.project.blue_command.security.EncryptionManager
import java.util.UUID

class AuthController : ViewModel() {
    private val users = listOf(
        UserAccount("u-commander-1", "commander", "commander123", UserRole.COMMANDER),
        UserAccount("u-soldier-1", "soldier1", "soldier123", UserRole.SOLDIER),
        UserAccount("u-soldier-2", "soldier2", "soldier123", UserRole.SOLDIER),
        UserAccount("u-soldier-3", "soldier3", "soldier123", UserRole.SOLDIER)
    )

    val devices = mutableStateListOf(
        CombatDevice(id = "d-1", name = "Radio Device A"),
        CombatDevice(id = "d-2", name = "Radio Device B"),
        CombatDevice(id = "d-3", name = "Tracker Device C")
    )

    val groups = mutableStateListOf<CombatGroup>()
    val commandFeed = mutableStateListOf<CommandMessage>()

    var currentUser by mutableStateOf<UserAccount?>(null)
        private set

    var authError by mutableStateOf<String?>(null)
        private set
    private val encryptionManager = EncryptionManager()

    init {
        val demoGroupKey = encryptionManager.generateNewGroupKeyBase64()

        groups.add(
            CombatGroup(
                id = "ALFA-1234-5678-9012",
                name = "Oddział Alfa (Demo)",
                groupKeyBase64 = demoGroupKey,
                memberIds = mutableListOf("u-soldier-1", "u-soldier-2", "u-soldier-3")
            )
        )
        commandFeed.addAll(
            listOf(
                CommandMessage(
                    id = UUID.randomUUID().toString(),
                    senderId = "u-soldier-2",
                    senderUsername = "soldier2",
                    commandLabel = "Enemy",
                    sentAtMillis = System.currentTimeMillis() - 120_000,
                    groupId = "ALFA-1234-5678-9012"
                ),
                CommandMessage(
                    id = UUID.randomUUID().toString(),
                    senderId = "u-commander-1",
                    senderUsername = "commander",
                    commandLabel = "Cover This Area",
                    sentAtMillis = System.currentTimeMillis() - 60_000,
                    groupId = "ALFA-1234-5678-9012"
                )
            )
        )
    }

    fun login(username: String, password: String): Boolean {
        val user = users.firstOrNull {
            it.username.equals(username.trim(), ignoreCase = true) && it.password == password
        }

        return if (user != null) {
            currentUser = user
            authError = null

            SessionRepository.setUser(user)
            true
        } else {
            authError = "Niepoprawny login lub haslo."
            false
        }
    }

    fun logout() {
        currentUser = null
        authError = null

        SessionRepository.clearSession()
    }

    fun getSoldiers(): List<UserAccount> = users.filter { it.role == UserRole.SOLDIER }

    fun getDeviceAssignedToSoldier(soldierId: String): CombatDevice? =
        devices.firstOrNull { it.assignedSoldierId == soldierId }

    fun createGroup(groupName: String): Boolean {
        val name = groupName.trim()
        if (name.isEmpty()) {
            authError = "Nazwa grupy nie może być pusta."
            return false
        }

        val newSecretKey = encryptionManager.generateNewGroupKeyBase64()

        groups.add(
            CombatGroup(
                id = UUID.randomUUID().toString(),
                name = name,
                groupKeyBase64 = newSecretKey
            )
        )
        authError = null
        return true
    }

    fun assignSoldierToGroup(soldierId: String, groupId: String) {
        val group = groups.firstOrNull { it.id == groupId } ?: return
        if (!group.memberIds.contains(soldierId)) {
            group.memberIds.add(soldierId)
            // Trigger Compose recomposition for list item changes.
            groups[groups.indexOf(group)] = group.copy(memberIds = group.memberIds.toMutableList())
        }
    }

    fun removeSoldierFromGroup(soldierId: String, groupId: String) {
        val group = groups.firstOrNull { it.id == groupId } ?: return
        if (group.memberIds.remove(soldierId)) {
            groups[groups.indexOf(group)] = group.copy(memberIds = group.memberIds.toMutableList())
        }
    }

    fun getGroupById(groupId: String): CombatGroup? = groups.firstOrNull { it.id == groupId }

    fun getUserById(userId: String): UserAccount? = users.firstOrNull { it.id == userId }

    fun assignSoldierToDevice(soldierId: String, deviceId: String) {
        val targetDevice = devices.firstOrNull { it.id == deviceId } ?: return
        devices.forEachIndexed { index, device ->
            if (device.assignedSoldierId == soldierId) {
                devices[index] = device.copy(assignedSoldierId = null)
            }
        }
        val deviceIndex = devices.indexOf(targetDevice)
        devices[deviceIndex] = targetDevice.copy(assignedSoldierId = soldierId)
    }

    fun getSoldierNamesForGroup(group: CombatGroup): String {
        val names = group.memberIds
            .mapNotNull { memberId -> users.firstOrNull { it.id == memberId }?.username }
        return if (names.isEmpty()) "brak" else names.joinToString(", ")
    }


    fun getCommandsFromOthers(): List<CommandMessage> {
        val currentUserId = currentUser?.id
        return commandFeed
            .filter { it.senderId != currentUserId }
            .sortedBy { it.sentAtMillis }
    }
}
