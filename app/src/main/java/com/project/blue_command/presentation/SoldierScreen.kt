package com.project.blue_command.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project.blue_command.logic.AuthController
import com.project.blue_command.logic.CommandController
import com.project.blue_command.model.TacticalCommand

private val SOLDIER_ALLOWED_COMMANDS = listOf(
    TacticalCommand.ENEMY,
    TacticalCommand.SNIPER,
    TacticalCommand.PISTOL,
    TacticalCommand.RIFLE,
    TacticalCommand.SHOTGUN,
    TacticalCommand.VEHICLE
)

@Composable
fun SoldierScreen(authController: AuthController, commandController: CommandController = viewModel()) {
    val user = authController.currentUser ?: return
    val group = authController.groups.firstOrNull { it.memberIds.contains(user.id) }
    var selectedSoldierView by remember { mutableStateOf(SoldierMainView.COMMANDS) }
    val receivedBleCommands by commandController.receivedCommands.collectAsState()

    LaunchedEffect(group) {
        commandController.setActiveGroup(group)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Rola: SOLDIER", style = MaterialTheme.typography.titleMedium)
                    Text("Użytkownik: ${user.username}")
                    Text(
                        text = "Oddział: ${group?.name ?: "BRAK PRZYDZIAŁU!"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (group == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                Button(onClick = { authController.logout() }) {
                    Text("Wyloguj")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            MainViewToggle(
                selectedView = selectedSoldierView,
                onViewSelected = { selectedSoldierView = it }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(modifier = Modifier.padding(4.dp)) {
                when (selectedSoldierView) {
                    SoldierMainView.COMMANDS -> CommandScreen(
                        controller = commandController,
                        availableCommands = SOLDIER_ALLOWED_COMMANDS
                    )
                    SoldierMainView.INBOX -> CommandsInboxScreen(messages = receivedBleCommands)
                }
            }
        }
    }
}