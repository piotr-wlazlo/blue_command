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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.project.blue_command.model.CombatGroup

@Composable
fun CommanderScreen(authController: AuthController) {
    val commandController: CommandController = viewModel(factory = CommandController.factory(authController))
    var newGroupName by remember { mutableStateOf("") }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var isManageMode by remember { mutableStateOf(false) }
    val selectedGroup = selectedGroupId?.let { authController.getGroupById(it) }

    if (selectedGroup == null) {
        CommanderMainPanel(
            authController = authController,
            newGroupName = newGroupName,
            onNewGroupNameChanged = { newGroupName = it },
            onGroupCreated = { created ->
                if (created) newGroupName = ""
            },
            onGroupSelected = { groupId ->
                selectedGroupId = groupId
                isManageMode = false
            }
        )
    } else if (!isManageMode) {
        GroupDetailsScreen(
            authController = authController,
            group = selectedGroup,
            commandController = commandController,
            onBack = {
                selectedGroupId = null
                isManageMode = false
            },
            onManageGroup = { isManageMode = true }
        )
    } else {
        ManageGroupScreen(
            authController = authController,
            group = selectedGroup,
            onBack = { isManageMode = false }
        )
    }
}

@Composable
private fun CommanderMainPanel(
    authController: AuthController,
    newGroupName: String,
    onNewGroupNameChanged: (String) -> Unit,
    onGroupCreated: (Boolean) -> Unit,
    onGroupSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Panel dowódcy", style = MaterialTheme.typography.titleLarge)
                Text("Tworzenie i podgląd grup")
            }
            Button(onClick = { authController.logout() }) { Text("Wyloguj") }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Utwórz grupe", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = onNewGroupNameChanged,
                    label = { Text("Nazwa grupy") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onGroupCreated(authController.createGroup(newGroupName)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Utwórz") }
            }
        }

        Text("Twoje grupy", style = MaterialTheme.typography.titleMedium)
        if (authController.groups.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Text("Brak utworzonych grup.", modifier = Modifier.padding(12.dp))
            }
        } else {
            authController.groups.forEach { group ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(group.name, style = MaterialTheme.typography.titleSmall)
                            Text("Liczba czlonków: ${group.memberIds.size}")
                        }
                        Button(onClick = { onGroupSelected(group.id) }) { Text("Wybierz") }
                    }
                }
            }
        }

        authController.authError?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun GroupDetailsScreen(
    authController: AuthController,
    group: CombatGroup,
    commandController: CommandController,
    onBack: () -> Unit,
    onManageGroup: () -> Unit,
) {
    var selectedView by remember { mutableStateOf(SoldierMainView.COMMANDS) }
    val receivedBleCommands by commandController.receivedCommands.collectAsState()
    val user = authController.currentUser ?: return

    LaunchedEffect(group) {
        commandController.setActiveGroup(group)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("Powrót") }
            Button(onClick = { authController.logout() }) { Text("Wyloguj") }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Nasłuch radiowy: ${group.name}", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Button(onClick = onManageGroup, modifier = Modifier.fillMaxWidth()) {
                    Text("Zarzadzaj grupa")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            MainViewToggle(
                selectedView = selectedView,
                onViewSelected = { selectedView = it }
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(modifier = Modifier.padding(4.dp)) {
                if (selectedView == SoldierMainView.COMMANDS) {
                    CommandScreen(controller = commandController)
                } else {
                    CommandsInboxScreen(messages = receivedBleCommands)
                }
            }
        }
    }
}

@Composable
private fun ManageGroupScreen(
    authController: AuthController,
    group: CombatGroup,
    onBack: () -> Unit
) {
    val allSoldiers = authController.getSoldiers()
    val members = group.memberIds.mapNotNull { authController.getUserById(it) }
    val availableToAdd = allSoldiers.filter { it.id !in group.memberIds }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("Powrót") }
            Button(onClick = { authController.logout() }) { Text("Wyloguj") }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Zarządzanie: ${group.name}", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Użytkownicy grupy", style = MaterialTheme.typography.titleSmall)
                if (members.isEmpty()) {
                    Text("Brak użytkowników w grupie.")
                } else {
                    members.forEach { member ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(member.username)
                            TextButton(onClick = {
                                authController.removeSoldierFromGroup(member.id, group.id)
                            }) {
                                Text("Usuń")
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Dodaj użytkownika", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                if (availableToAdd.isEmpty()) {
                    Text("Wszyscy żołnierze są już w tej grupie.")
                } else {
                    availableToAdd.forEach { soldier ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(soldier.username)
                            TextButton(onClick = {
                                authController.assignSoldierToGroup(soldier.id, group.id)
                            }) {
                                Text("Dodaj")
                            }
                        }
                    }
                }
            }
        }
    }
}
