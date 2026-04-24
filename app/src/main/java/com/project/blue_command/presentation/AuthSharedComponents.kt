package com.project.blue_command.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.project.blue_command.R
import com.project.blue_command.model.CommandMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            colors = if (isCommandsSelected) androidx.compose.material3.ButtonDefaults.buttonColors()
            else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
        ) { Text("Komendy") }

        val isHistorySelected = selectedView == SoldierMainView.INBOX
        Button(
            onClick = { onViewSelected(SoldierMainView.INBOX) },
            modifier = Modifier.weight(1f),
            colors = if (isHistorySelected) androidx.compose.material3.ButtonDefaults.buttonColors()
            else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
        ) { Text("Historia komend") }
    }
}

@Composable
fun CommandsInboxScreen(messages: List<CommandMessage>) {
    if (messages.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Brak odebranych komend.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = message.senderUsername,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.commandLabel,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatMessageTime(message.sentAtMillis),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

enum class SoldierMainView {
    COMMANDS,
    INBOX
}
