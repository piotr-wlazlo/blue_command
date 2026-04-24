package com.project.blue_command.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project.blue_command.logic.AuthController
import com.project.blue_command.model.UserRole


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
