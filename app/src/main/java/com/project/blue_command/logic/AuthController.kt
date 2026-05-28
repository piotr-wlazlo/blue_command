package com.project.blue_command.logic

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.project.blue_command.data.database.GroupEntity
import com.project.blue_command.data.database.LocalAppDatabase
import com.project.blue_command.data.SessionRepository
import com.project.blue_command.data.database.CommandMessageEntity
import com.project.blue_command.data.database.UserEntity
import com.project.blue_command.data.database.UserSessionEntity
import com.project.blue_command.data.database.toCombatGroup
import com.project.blue_command.data.database.toCommandMessage
import com.project.blue_command.data.database.toEntity
import com.project.blue_command.data.database.toUserAccount
import com.project.blue_command.data.database.toUserEntity
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.zip.GZIPInputStream

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
            restoreLoggedUserFromSession()
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
//            val demoGroupKey = encryptionManager.generateNewGroupKeyBase64()
            appDao.insertGroup(
                GroupEntity(
                    id = DEMO_GROUP_ID,
                    name = "Oddział Alfa (Demo)",
                    groupKeyBase64 = "yGXcddukkpOdtigrwfuypg==",
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
            viewModelScope.launch(Dispatchers.IO) {
                appDao.upsertUserSession(
                    UserSessionEntity(
                        id = SESSION_ROW_ID,
                        userId = user.id,
                        loggedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
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
        viewModelScope.launch(Dispatchers.IO) {
            appDao.clearUserSession()
        }
    }

    fun getSoldiers(): List<UserAccount> = usersCache.filter { it.role == UserRole.SOLDIER }
    fun getAllUsersSnapshot(): List<UserAccount> = usersCache.toList()
    fun getAllCommandsSnapshot(): List<CommandMessage> = runBlocking(Dispatchers.IO) {
        appDao.getAllCommands().map { it.toCommandMessage() }
    }

    fun getDeviceAssignedToSoldier(soldierId: String): CombatDevice? =
        devices.firstOrNull { it.assignedSoldierId == soldierId }

    fun createGroup(groupName: String): Boolean {
        val name = groupName.trim()
        if (name.isEmpty()) {
            authError = "Nazwa grupy nie może być pusta."
            return false
        }
        val creatorId = currentUser?.id
        viewModelScope.launch(Dispatchers.IO) {
            val newSecretKey = encryptionManager.generateNewGroupKeyBase64()
            appDao.insertGroup(
                GroupEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    groupKeyBase64 = newSecretKey,
                    memberIds = creatorId?.let { listOf(it) } ?: emptyList(),
                ),
            )
            refreshGroupsFromDb()
        }
        authError = null
        return true
    }

    fun assignSoldierToGroup(soldierId: String, groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val groups = appDao.getAllGroups()
            groups.forEach { existingGroup ->
                if (existingGroup.id != groupId && existingGroup.memberIds.contains(soldierId)) {
                    appDao.insertGroup(
                        existingGroup.copy(
                            memberIds = existingGroup.memberIds.filter { it != soldierId },
                        ),
                    )
                }
            }
            val entity = appDao.getGroupById(groupId) ?: return@launch
            if (!entity.memberIds.contains(soldierId)) {
                val updated = entity.copy(memberIds = entity.memberIds + soldierId)
                appDao.insertGroup(updated)
            }
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

    data class SyncImportResult(
        val success: Boolean,
        val message: String,
    )

    fun importSyncFromQr(rawPayload: String, onComplete: (SyncImportResult) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { importSyncFromQrInternal(rawPayload) }
            if (result.success) {
                refreshUsersFromDb()
                refreshGroupsFromDb()
            }
            onComplete(result)
        }
    }

    private suspend fun importSyncFromQrInternal(rawPayload: String): SyncImportResult {
        val payload = rawPayload.trim()
        if (!payload.startsWith(SYNC_PREFIX)) {
            return SyncImportResult(false, "To nie jest poprawny kod synchronizacji.")
        }

        val compressedContent = payload.removePrefix(SYNC_PREFIX)
        if (compressedContent.isBlank()) {
            return SyncImportResult(false, "Kod synchronizacji jest pusty.")
        }

        return try {
            val jsonString = decodeAndDecompress(compressedContent)
            val root = JSONObject(jsonString)
            when (root.optInt("v", -1)) {
                1 -> importLegacyGroupSync(root)
                2 -> importFullDatabaseSync(root)
                else -> SyncImportResult(false, "Nieobsługiwana wersja pakietu synchronizacji.")
            }
        } catch (_: Exception) {
            SyncImportResult(false, "Nie udało się odczytać danych z kodu QR.")
        }
    }

    private suspend fun importFullDatabaseSync(root: JSONObject): SyncImportResult {
        val usersArray = root.optJSONArray("u") ?: JSONArray()
        val groupsArray = root.optJSONArray("g") ?: JSONArray()
        val commandsArray = root.optJSONArray("c") ?: JSONArray()

        val existingUsers = appDao.getAllUsers().associateBy { it.id }
        val roleByUserId = existingUsers.mapValuesTo(mutableMapOf()) { (_, userEntity) ->
            runCatching { UserRole.valueOf(userEntity.roleName) }.getOrDefault(UserRole.SOLDIER)
        }

        var importedUsersCount = 0
        for (index in 0 until usersArray.length()) {
            val userObj = usersArray.optJSONObject(index) ?: continue
            val userId = userObj.optString("id")
            val username = userObj.optString("un")
            val roleName = userObj.optString("r")
            if (userId.isBlank() || username.isBlank() || roleName.isBlank()) continue
            val role = runCatching { UserRole.valueOf(roleName) }.getOrNull() ?: continue
            roleByUserId[userId] = role
            val password = existingUsers[userId]?.password ?: "synced-user"
            appDao.upsertUser(
                UserEntity(
                    id = userId,
                    username = username,
                    password = password,
                    roleName = role.name,
                ),
            )
            importedUsersCount++
        }

        appDao.clearAllCommands()
        appDao.clearAllGroups()

        val parsedGroups = mutableListOf<GroupEntity>()
        val lastGroupIndexBySoldierId = mutableMapOf<String, Int>()

        for (index in 0 until groupsArray.length()) {
            val groupObj = groupsArray.optJSONObject(index) ?: continue
            val groupId = groupObj.optString("id")
            val groupName = groupObj.optString("n")
            val groupKey = groupObj.optString("k")
            if (groupId.isBlank() || groupName.isBlank() || groupKey.isBlank()) continue
            val memberIds = groupObj.optJSONArray("m")
                ?.toStringList()
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?: emptyList()
            val groupEntity = GroupEntity(
                id = groupId,
                name = groupName,
                groupKeyBase64 = groupKey,
                memberIds = memberIds,
            )
            parsedGroups.add(groupEntity)
            memberIds.forEach { memberId ->
                if (roleByUserId[memberId] == UserRole.SOLDIER) {
                    lastGroupIndexBySoldierId[memberId] = parsedGroups.lastIndex
                }
            }
        }

        parsedGroups.forEachIndexed { index, groupEntity ->
            val normalizedMembers = groupEntity.memberIds.filter { memberId ->
                if (roleByUserId[memberId] == UserRole.SOLDIER) {
                    lastGroupIndexBySoldierId[memberId] == index
                } else {
                    true
                }
            }
            appDao.insertGroup(groupEntity.copy(memberIds = normalizedMembers))
        }
        val importedGroupsCount = parsedGroups.size

        var importedCommandsCount = 0
        for (index in 0 until commandsArray.length()) {
            val commandObj = commandsArray.optJSONObject(index) ?: continue
            val commandId = commandObj.optString("id").ifBlank { UUID.randomUUID().toString() }
            val senderId = commandObj.optString("sid").ifBlank { "unknown" }
            val senderUsername = commandObj.optString("sun").ifBlank { "unknown" }
            val commandLabel = commandObj.optString("cmd").ifBlank { continue }
            val sentAt = commandObj.optLong("t", System.currentTimeMillis())
            val groupId = commandObj.optString("gid")
            if (groupId.isBlank()) continue
            val expectedAcks = commandObj.optInt("exp", 0)
            val receivedAcks = commandObj.optInt("ack", 0)
            val isFailed = commandObj.optBoolean("f", false)
            val expectedAckMemberIds = commandObj.optJSONArray("eam")
                ?.toStringList()
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val acknowledgedMemberIds = commandObj.optJSONArray("am")
                ?.toStringList()
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            appDao.insertCommand(
                CommandMessageEntity(
                    id = commandId,
                    senderId = senderId,
                    senderUsername = senderUsername,
                    commandLabel = commandLabel,
                    sentAtMillis = sentAt,
                    groupId = groupId,
                    expectedAcks = expectedAcks,
                    receivedAcks = receivedAcks,
                    expectedAckMemberIds = expectedAckMemberIds,
                    acknowledgedMemberIds = acknowledgedMemberIds,
                    isFailed = isFailed,
                ),
            )
            importedCommandsCount++
        }

        return SyncImportResult(
            success = true,
            message = "Pełna synchronizacja zakończona. Użytkownicy: $importedUsersCount, grupy: $importedGroupsCount, komendy: $importedCommandsCount.",
        )
    }

    private suspend fun importLegacyGroupSync(root: JSONObject): SyncImportResult {
        val groupObject = root.optJSONObject("g")
            ?: return SyncImportResult(false, "Brak danych grupy w kodzie QR.")
        val groupId = groupObject.optString("id")
        val groupName = groupObject.optString("n")
        val groupKey = groupObject.optString("k")
        if (groupId.isBlank() || groupName.isBlank() || groupKey.isBlank()) {
            return SyncImportResult(false, "Niekompletne dane grupy w kodzie QR.")
        }

        val memberIdsFromPayload = groupObject.optJSONArray("m")
            ?.toStringList()
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()

        val usersArray = root.optJSONArray("u")
        val existingUsers = appDao.getAllUsers().associateBy { it.id }
        val roleByUserId = existingUsers.mapValuesTo(mutableMapOf()) { (_, userEntity) ->
            runCatching { UserRole.valueOf(userEntity.roleName) }.getOrDefault(UserRole.SOLDIER)
        }
        val importedMemberIds = mutableListOf<String>()
        var importedUsersCount = 0
        if (usersArray != null) {
            for (index in 0 until usersArray.length()) {
                val userObj = usersArray.optJSONObject(index) ?: continue
                val userId = userObj.optString("id")
                val username = userObj.optString("un")
                val roleName = userObj.optString("r")
                if (userId.isBlank() || username.isBlank() || roleName.isBlank()) continue

                val role = runCatching { UserRole.valueOf(roleName) }.getOrNull() ?: continue
                roleByUserId[userId] = role
                val password = existingUsers[userId]?.password ?: "synced-user"
                appDao.upsertUser(
                    UserEntity(
                        id = userId,
                        username = username,
                        password = password,
                        roleName = role.name,
                    ),
                )
                importedUsersCount++
                importedMemberIds.add(userId)
            }
        }

        val effectiveMemberIds = if (memberIdsFromPayload.isNotEmpty()) {
            memberIdsFromPayload
        } else {
            importedMemberIds.distinct()
        }

        appDao.insertGroup(
            GroupEntity(
                id = groupId,
                name = groupName,
                groupKeyBase64 = groupKey,
                memberIds = effectiveMemberIds,
            ),
        )

        val allGroups = appDao.getAllGroups()
        allGroups
            .asSequence()
            .filter { it.id != groupId }
            .forEach { existingGroup ->
                val updatedMembers = existingGroup.memberIds.filterNot { memberId ->
                    memberId in effectiveMemberIds && roleByUserId[memberId] == UserRole.SOLDIER
                }
                if (updatedMembers.size != existingGroup.memberIds.size) {
                    appDao.insertGroup(existingGroup.copy(memberIds = updatedMembers))
                }
            }

        val commandsArray = root.optJSONArray("c")
        var importedCommandsCount = 0
        if (commandsArray != null) {
            for (index in 0 until commandsArray.length()) {
                val commandObj = commandsArray.optJSONObject(index) ?: continue
                val commandId = commandObj.optString("id").ifBlank { UUID.randomUUID().toString() }
                val senderId = commandObj.optString("sid").ifBlank { "unknown" }
                val senderUsername = commandObj.optString("sun").ifBlank { "unknown" }
                val commandLabel = commandObj.optString("cmd").ifBlank { continue }
                val sentAt = commandObj.optLong("t", System.currentTimeMillis())
                val expectedAcks = commandObj.optInt("exp", 0)
                val receivedAcks = commandObj.optInt("ack", 0)
                val isFailed = commandObj.optBoolean("f", false)

                appDao.insertCommand(
                    CommandMessageEntity(
                        id = commandId,
                        senderId = senderId,
                        senderUsername = senderUsername,
                        commandLabel = commandLabel,
                        sentAtMillis = sentAt,
                        groupId = groupId,
                        expectedAcks = expectedAcks,
                        receivedAcks = receivedAcks,
                        isFailed = isFailed,
                    ),
                )
                importedCommandsCount++
            }
        }

        return SyncImportResult(
            success = true,
            message = "Synchronizacja zakończona. Użytkownicy: $importedUsersCount, komendy: $importedCommandsCount.",
        )
    }

    private fun decodeAndDecompress(base64Data: String): String {
        val compressed = android.util.Base64.decode(
            base64Data,
            android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE,
        )
        val input = GZIPInputStream(ByteArrayInputStream(compressed))
        return input.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    companion object {
        private const val DEMO_GROUP_ID = "ALFA-1234-5678-9012"
        private const val SYNC_PREFIX = "BCSYNC1:"
        private const val SESSION_ROW_ID = 1
    }

    private suspend fun restoreLoggedUserFromSession() {
        val session = appDao.getUserSession() ?: return
        val user = appDao.getAllUsers()
            .map { it.toUserAccount() }
            .firstOrNull { it.id == session.userId }
            ?: return

        withContext(Dispatchers.Main.immediate) {
            currentUser = user
            authError = null
            SessionRepository.setUser(user)
        }
    }
}

private fun JSONArray.toStringList(): List<String> {
    val result = mutableListOf<String>()
    for (index in 0 until length()) {
        result.add(optString(index))
    }
    return result
}
