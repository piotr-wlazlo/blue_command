package com.project.blue_command.logic

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.project.blue_command.data.GroupEntity
import com.project.blue_command.data.LocalAppDatabase
import com.project.blue_command.data.SessionRepository
import com.project.blue_command.data.toCombatGroup
import com.project.blue_command.data.toEntity
import com.project.blue_command.data.toUserAccount
import com.project.blue_command.data.toUserEntity
import com.project.blue_command.model.CombatDevice
import com.project.blue_command.model.CombatGroup
import com.project.blue_command.model.CommandMessage
import com.project.blue_command.model.UserAccount
import com.project.blue_command.model.UserRole
import com.project.blue_command.security.EncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID

class AuthController(application: Application) : AndroidViewModel(application) {

    private val appDao = LocalAppDatabase.getDatabase(application).appDao()

    private val usersCache = mutableStateListOf<UserAccount>()

    val devices = mutableStateListOf(
        CombatDevice(id = "d-1", name = "Radio Device A"),
        CombatDevice(id = "d-2", name = "Radio Device B"),
        CombatDevice(id = "d-3", name = "Tracker Device C"),
    )

    val groups = mutableStateListOf<CombatGroup>()

    var currentUser by mutableStateOf<UserAccount?>(null)
        private set

    var authError by mutableStateOf<String?>(null)
        private set

    private val encryptionManager = EncryptionManager()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            seedDatabaseIfEmpty()
            refreshUsersFromDb()
            refreshGroupsFromDb()
            seedDemoCommandsIfNeeded()
        }
    }

    private suspend fun seedDatabaseIfEmpty() {
        if (appDao.getAllUsers().isEmpty()) {
            val demoUsers = listOf(
                UserAccount("u-commander-1", "commander", "commander123", UserRole.COMMANDER),
                UserAccount("u-soldier-1", "soldier1", "soldier123", UserRole.SOLDIER),
                UserAccount("u-soldier-2", "soldier2", "soldier123", UserRole.SOLDIER),
                UserAccount("u-soldier-3", "soldier3", "soldier123", UserRole.SOLDIER),
            )
            demoUsers.forEach { appDao.insertUser(it.toUserEntity()) }
        }
        if (appDao.getAllGroups().isEmpty()) {
            val demoGroupKey = encryptionManager.generateNewGroupKeyBase64()
            appDao.insertGroup(
                GroupEntity(
                    id = DEMO_GROUP_ID,
                    name = "Oddział Alfa (Demo)",
                    groupKeyBase64 = demoGroupKey,
                    memberIds = listOf("u-soldier-1", "u-soldier-2", "u-soldier-3"),
                ),
            )
        }
    }

    private suspend fun seedDemoCommandsIfNeeded() {
        if (appDao.countCommandsForGroup(DEMO_GROUP_ID) > 0) return
        val now = System.currentTimeMillis()
        listOf(
            CommandMessage(
                id = UUID.randomUUID().toString(),
                senderId = "u-soldier-2",
                senderUsername = "soldier2",
                commandLabel = "Enemy",
                sentAtMillis = now - 120_000,
                groupId = DEMO_GROUP_ID,
            ),
            CommandMessage(
                id = UUID.randomUUID().toString(),
                senderId = "u-commander-1",
                senderUsername = "commander",
                commandLabel = "Cover This Area",
                sentAtMillis = now - 60_000,
                groupId = DEMO_GROUP_ID,
            ),
        ).forEach { appDao.insertCommand(it.toEntity()) }
    }

    private suspend fun refreshUsersFromDb() {
        val loaded = appDao.getAllUsers().map { it.toUserAccount() }
        withContext(Dispatchers.Main.immediate) {
            usersCache.clear()
            usersCache.addAll(loaded)
        }
    }

    private suspend fun refreshGroupsFromDb() {
        val loaded = appDao.getAllGroups().map { it.toCombatGroup() }
        withContext(Dispatchers.Main.immediate) {
            groups.clear()
            groups.addAll(loaded)
        }
    }

    fun login(username: String, password: String): Boolean {
        val trimmedUser = username.trim()
        val rows = runBlocking(Dispatchers.IO) {
            appDao.getAllUsers()
        }
        val user = rows.map { it.toUserAccount() }.firstOrNull {
            it.username.equals(trimmedUser, ignoreCase = true) && it.password == password
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

    fun getSoldiers(): List<UserAccount> = usersCache.filter { it.role == UserRole.SOLDIER }

    fun getDeviceAssignedToSoldier(soldierId: String): CombatDevice? =
        devices.firstOrNull { it.assignedSoldierId == soldierId }

    fun createGroup(groupName: String): Boolean {
        val name = groupName.trim()
        if (name.isEmpty()) {
            authError = "Nazwa grupy nie może być pusta."
            return false
        }
        viewModelScope.launch(Dispatchers.IO) {
            val newSecretKey = encryptionManager.generateNewGroupKeyBase64()
            appDao.insertGroup(
                GroupEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    groupKeyBase64 = newSecretKey,
                    memberIds = emptyList(),
                ),
            )
            refreshGroupsFromDb()
        }
        authError = null
        return true
    }

    fun assignSoldierToGroup(soldierId: String, groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = appDao.getGroupById(groupId) ?: return@launch
            if (entity.memberIds.contains(soldierId)) return@launch
            val updated = entity.copy(memberIds = entity.memberIds + soldierId)
            appDao.insertGroup(updated)
            refreshGroupsFromDb()
        }
    }

    fun removeSoldierFromGroup(soldierId: String, groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = appDao.getGroupById(groupId) ?: return@launch
            if (!entity.memberIds.contains(soldierId)) return@launch
            val updated = entity.copy(memberIds = entity.memberIds.filter { it != soldierId })
            appDao.insertGroup(updated)
            refreshGroupsFromDb()
        }
    }

    fun getGroupById(groupId: String): CombatGroup? = groups.firstOrNull { it.id == groupId }

    fun getUserById(userId: String): UserAccount? = usersCache.firstOrNull { it.id == userId }

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
        val names = group.memberIds.mapNotNull { memberId -> getUserById(memberId)?.username }
        return if (names.isEmpty()) "brak" else names.joinToString(", ")
    }

    companion object {
        private const val DEMO_GROUP_ID = "ALFA-1234-5678-9012"
    }
}
