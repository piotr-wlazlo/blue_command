package com.project.blue_command.presentation

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.project.blue_command.R
import com.project.blue_command.model.CombatGroup
import com.project.blue_command.model.CommandMessage
import com.project.blue_command.model.TacticalCommand
import com.project.blue_command.model.UserAccount
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPOutputStream

@Composable
fun AppTopLogo() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.blue_command_logo_2),
                contentDescription = "Blue Command Top Logo",
                modifier = Modifier.size(width = 140.dp, height = 40.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
fun MainViewToggle(
    selectedView: SoldierMainView,
    onViewSelected: (SoldierMainView) -> Unit
) {
    val containerShape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val isCommandsSelected = selectedView == SoldierMainView.COMMANDS
        Button(
            onClick = { onViewSelected(SoldierMainView.COMMANDS) },
            modifier = Modifier.weight(1f),
            shape = containerShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isCommandsSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isCommandsSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        ) { Text("Komendy") }

        val isHistorySelected = selectedView == SoldierMainView.INBOX
        Button(
            onClick = { onViewSelected(SoldierMainView.INBOX) },
            modifier = Modifier.weight(1f),
            shape = containerShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isHistorySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isHistorySelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        ) { Text("Historia komend") }
    }
}

@Composable
fun CommandsInboxScreen(
    messages: List<CommandMessage>,
    resolveUsername: (String) -> String = { it },
) {
    if (messages.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Brak odebranych komend.")
        }
        return
    }

    val expandedItems = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            val isExpanded = expandedItems[message.id] ?: false
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedItems[message.id] = !isExpanded },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = message.senderUsername,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = message.commandLabel,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatMessageTime(message.sentAtMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            message.statusLabel?.let { status ->
                                StatusBadge(status = status)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${message.receivedAcks}/${message.expectedAcks}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isExpanded) "Dotknij, aby zwinąć szczegóły" else "Dotknij, aby rozwinąć szczegóły",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                        Spacer(modifier = Modifier.height(10.dp))
                        if (message.expectedAcks > 0) {
                            val acknowledged = message.acknowledgedMemberIds.distinct()
                            val pending = message.expectedAckMemberIds
                                .filterNot { it in acknowledged }
                                .distinct()

                            Text(
                                text = "Odczytali:",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = formatUserList(acknowledged, resolveUsername),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF2E7D32)
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nie odczytali:",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = formatUserList(pending, resolveUsername),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (pending.isEmpty()) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        } else {
                            Text(
                                text = "Dla tej komendy brak śledzenia odczytu.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LatestCommandPreviewCard(messages: List<CommandMessage>) {
    val latestMessage = messages.firstOrNull()
    val command = latestMessage?.let { message ->
        TacticalCommand.entries.firstOrNull { it.label == message.commandLabel }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = "Ostatnia komenda w grupie",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (latestMessage == null) {
                Text(
                    text = "Brak komend.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (command != null) {
                        Image(
                            painter = painterResource(id = command.iconRes),
                            contentDescription = latestMessage.commandLabel,
                            modifier = Modifier.size(56.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("?", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = latestMessage.commandLabel,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Od: ${latestMessage.senderUsername}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = formatMessageTime(latestMessage.sentAtMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DatabaseSyncQrCard(
    groups: List<CombatGroup>,
    users: List<UserAccount>,
    messages: List<CommandMessage>,
) {
    val syncData = remember(groups, users, messages) {
        buildSyncQrPayload(groups = groups, users = users, messages = messages)
    }
    val qrImage = remember(syncData.payload) { createQrImageBitmap(syncData.payload, size = 500) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Kod QR synchronizacji bazy",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (qrImage != null) {
                Image(
                    bitmap = qrImage,
                    contentDescription = "QR synchronizacji bazy danych",
                    modifier = Modifier.size(220.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = "Nie udało się wygenerować kodu QR.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Zawiera: pełną bazę (użytkownicy, grupy, komendy).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Text(
                text = "Grupy: ${syncData.includedGroups}/${groups.size}, użytkownicy: ${syncData.includedUsers}/${users.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Text(
                text = "Komendy w pakiecie: ${syncData.includedCommands}/${messages.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            if (syncData.truncated) {
                Text(
                    text = "Pakiet został skrócony, aby zmieścić się w jednym kodzie QR.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF6C00),
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val badgeColor = when (status) {
        "approved" -> Color(0xFF2E7D32)
        "partial approved" -> Color(0xFFEF6C00)
        "failed" -> Color(0xFFC62828)
        else -> Color.Gray
    }
    Surface(
        color = badgeColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = status,
            color = badgeColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun formatUserList(memberIds: List<String>, resolveUsername: (String) -> String): String {
    if (memberIds.isEmpty()) return "-"
    return memberIds.joinToString(separator = "\n") { "\u2022 ${resolveUsername(it)}" }
}

private data class SyncQrData(
    val payload: String,
    val includedUsers: Int,
    val includedGroups: Int,
    val includedCommands: Int,
    val truncated: Boolean,
)

private fun buildSyncQrPayload(
    groups: List<CombatGroup>,
    users: List<UserAccount>,
    messages: List<CommandMessage>,
): SyncQrData {
    val sortedMessages = messages.sortedByDescending { it.sentAtMillis }
    var commandLimit = minOf(sortedMessages.size, 24)
    while (commandLimit >= 0) {
        val json = JSONObject().apply {
            put("v", 2)
            put("ts", System.currentTimeMillis())
            put("g", JSONArray().apply {
                groups.forEach { group ->
                    put(JSONObject().apply {
                        put("id", group.id)
                        put("n", group.name)
                        put("k", group.groupKeyBase64)
                        put("m", JSONArray().apply {
                            group.memberIds.forEach { memberId -> put(memberId) }
                        })
                    })
                }
            })
            put("u", JSONArray().apply {
                users.forEach { member ->
                    put(JSONObject().apply {
                        put("id", member.id)
                        put("un", member.username)
                        put("r", member.role.name)
                    })
                }
            })
            put("c", JSONArray().apply {
                sortedMessages.take(commandLimit).forEach { message ->
                    put(JSONObject().apply {
                        put("id", message.id)
                        put("sid", message.senderId)
                        put("sun", message.senderUsername)
                        put("cmd", message.commandLabel)
                        put("gid", message.groupId)
                        put("t", message.sentAtMillis)
                        put("exp", message.expectedAcks)
                        put("ack", message.receivedAcks)
                        put("eam", JSONArray().apply {
                            message.expectedAckMemberIds.forEach { put(it) }
                        })
                        put("am", JSONArray().apply {
                            message.acknowledgedMemberIds.forEach { put(it) }
                        })
                        put("f", message.isFailed)
                    })
                }
            })
        }.toString()

        val compressedPayload = compressToBase64(json)
        val qrPayload = "BCSYNC1:$compressedPayload"
        if (qrPayload.length <= 2500 || commandLimit == 0) {
            return SyncQrData(
                payload = qrPayload,
                includedUsers = users.size,
                includedGroups = groups.size,
                includedCommands = commandLimit,
                truncated = commandLimit < sortedMessages.size,
            )
        }
        commandLimit--
    }

    return SyncQrData(
        payload = "BCSYNC1:",
        includedUsers = users.size,
        includedGroups = groups.size,
        includedCommands = 0,
        truncated = true,
    )
}

private fun compressToBase64(raw: String): String {
    val outputStream = ByteArrayOutputStream()
    GZIPOutputStream(outputStream).use { gzip ->
        gzip.write(raw.toByteArray(Charsets.UTF_8))
    }
    return Base64.encodeToString(
        outputStream.toByteArray(),
        Base64.NO_WRAP or Base64.URL_SAFE,
    )
}

private fun createQrImageBitmap(payload: String, size: Int): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

enum class SoldierMainView {
    COMMANDS,
    INBOX
}
