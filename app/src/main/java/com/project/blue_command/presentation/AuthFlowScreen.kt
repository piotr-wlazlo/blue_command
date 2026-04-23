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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project.blue_command.R
import com.project.blue_command.logic.AuthController
import com.project.blue_command.model.CombatGroup
import com.project.blue_command.model.CommandMessage
import com.project.blue_command.model.UserRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.collectAsState
import com.project.blue_command.logic.CommandController
import androidx.compose.runtime.LaunchedEffect


@Composable
fun AuthFlowScreen(authController: AuthController = viewModel()) {
    val user = authController.currentUser
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        AppTopLogo()
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (user == null) {
                LoginScreen(authController = authController)
            } else {
                when (user.role) {
                    UserRole.SOLDIER -> SoldierScreen(authController = authController)
                    UserRole.COMMANDER -> CommanderScreen(authController = authController)
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(authController: AuthController) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.blue_command_logo),
                    contentDescription = "Blue Command Main Logo",
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .padding(bottom = 8.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = "Logowanie",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Login") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Haslo") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { authController.login(username, password) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Zaloguj")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Demo: commander/commander123, soldier1/soldier123",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        authController.authError?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SoldierScreen(authController: AuthController, commandController: CommandController = viewModel()) {
    val user = authController.currentUser ?: return
    val assignedDevice = authController.getDeviceAssignedToSoldier(user.id)

    val myGroup = authController.groups.firstOrNull { it.memberIds.contains(user.id) }
    var selectedSoldierView by remember { mutableStateOf(SoldierMainView.COMMANDS) }
    val receivedBleCommands by commandController.receivedCommands.collectAsState()

    LaunchedEffect(myGroup?.id) {
        commandController.setActiveGroup(myGroup?.id)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Rola: SOLDIER", style = MaterialTheme.typography.titleMedium)
                    Text("Uzytkownik: ${user.username}")
                    Text(
                        text = "Oddział: ${myGroup?.name ?: "BRAK PRZYDZIAŁU!"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (myGroup == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isCommandsSelected = selectedSoldierView == SoldierMainView.COMMANDS
                Button(
                    onClick = { selectedSoldierView = SoldierMainView.COMMANDS },
                    modifier = Modifier.weight(1f),
                    colors = if (isCommandsSelected) androidx.compose.material3.ButtonDefaults.buttonColors()
                    else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                ) { Text("Komendy") }

                val isInboxSelected = selectedSoldierView == SoldierMainView.INBOX
                Button(
                    onClick = { selectedSoldierView = SoldierMainView.INBOX },
                    modifier = Modifier.weight(1f),
                    colors = if (isInboxSelected) androidx.compose.material3.ButtonDefaults.buttonColors()
                    else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                ) { Text("Odebrane") }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Box(modifier = Modifier.padding(4.dp)) {
                when (selectedSoldierView) {
                    SoldierMainView.COMMANDS -> CommandScreen(controller = commandController)
                    SoldierMainView.INBOX -> CommandsInboxScreen(messages = receivedBleCommands)
                }
            }
        }
    }
}

@Composable
private fun CommandsInboxScreen(messages: List<CommandMessage>) {
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

@Composable
private fun CommanderScreen(authController: AuthController) {
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
                if (created) {
                    newGroupName = ""
                }
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
                Text("Panel dowodcy", style = MaterialTheme.typography.titleLarge)
                Text("Tworzenie i podglad grup")
            }
            Button(onClick = { authController.logout() }) {
                Text("Wyloguj")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Utworz grupe", style = MaterialTheme.typography.titleMedium)
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
                ) {
                    Text("Utworz")
                }
            }
        }

        Text("Twoje grupy", style = MaterialTheme.typography.titleMedium)
        if (authController.groups.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("Brak utworzonych grup.", modifier = Modifier.padding(12.dp))
            }
        } else {
            authController.groups.forEach { group ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(group.name, style = MaterialTheme.typography.titleSmall)
                            Text("Liczba czlonkow: ${group.memberIds.size}")
                        }
                        Button(onClick = { onGroupSelected(group.id) }) {
                            Text("Wybierz")
                        }
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
    onBack: () -> Unit,
    onManageGroup: () -> Unit,
    commandController: CommandController = viewModel()
) {
    var selectedView by remember { mutableStateOf(SoldierMainView.COMMANDS) }
    val receivedBleCommands by commandController.receivedCommands.collectAsState()

    LaunchedEffect(group.id) {
        commandController.setActiveGroup(group.id)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("Powrot") }
            Button(onClick = { authController.logout() }) { Text("Wyloguj") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Nasłuch radiowy: ${group.name}", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Button(onClick = onManageGroup, modifier = Modifier.fillMaxWidth()) { Text("Zarzadzaj grupa") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { selectedView = SoldierMainView.COMMANDS },
                modifier = Modifier.weight(1f),
                colors = if (selectedView == SoldierMainView.COMMANDS) androidx.compose.material3.ButtonDefaults.buttonColors()
                else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
            ) { Text("Komendy") }

            Button(
                onClick = { selectedView = SoldierMainView.INBOX },
                modifier = Modifier.weight(1f),
                colors = if (selectedView == SoldierMainView.INBOX) androidx.compose.material3.ButtonDefaults.buttonColors()
                else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
            ) { Text("Odebrane") }
        }

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)
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
            TextButton(onClick = onBack) {
                Text("Powrot")
            }
            Button(onClick = { authController.logout() }) {
                Text("Wyloguj")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Zarzadzanie: ${group.name}", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Uzytkownicy grupy", style = MaterialTheme.typography.titleSmall)
                if (members.isEmpty()) {
                    Text("Brak uzytkownikow w grupie.")
                } else {
                    members.forEach { member ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(member.username)
                            TextButton(
                                onClick = { authController.removeSoldierFromGroup(member.id, group.id) }
                            ) {
                                Text("Usun")
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Dodaj uzytkownika", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                if (availableToAdd.isEmpty()) {
                    Text("Wszyscy soldierzy sa juz w tej grupie.")
                } else {
                    availableToAdd.forEach { soldier ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(soldier.username)
                            TextButton(
                                onClick = { authController.assignSoldierToGroup(soldier.id, group.id) }
                            ) {
                                Text("Dodaj")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTopLogo() {
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

private fun formatMessageTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private enum class SoldierMainView {
    COMMANDS,
    INBOX
}
