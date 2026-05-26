package com.project.blue_command.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.project.blue_command.R
import com.project.blue_command.logic.AuthController

@Composable
fun LoginScreen(authController: AuthController) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var syncMessage by remember { mutableStateOf("Jeśli jesteś żołnierzem, najpierw zsynchronizuj dane przez QR.") }
    var importInProgress by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val focusManager = LocalFocusManager.current

    fun submitLogin() {
        focusManager.clearFocus()
        authController.login(username, password)
    }

    val scanner = remember(activity) {
        activity?.let {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()
            GmsBarcodeScanning.getClient(it, options)
        }
    }

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
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Haslo") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitLogin() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { submitLogin() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Zaloguj")
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (scanner == null) {
                            syncMessage = "Nie można uruchomić skanera na tym urządzeniu."
                            return@Button
                        }
                        importInProgress = true
                        syncMessage = "Skanowanie kodu QR..."
                        scanner.startScan()
                            .addOnSuccessListener { barcode ->
                                val rawPayload = barcode.rawValue
                                if (rawPayload.isNullOrBlank()) {
                                    importInProgress = false
                                    syncMessage = "Kod QR jest pusty."
                                } else {
                                    authController.importSyncFromQr(rawPayload) { result ->
                                        importInProgress = false
                                        syncMessage = result.message
                                    }
                                }
                            }
                            .addOnCanceledListener {
                                importInProgress = false
                                syncMessage = "Skanowanie anulowane."
                            }
                            .addOnFailureListener { error ->
                                importInProgress = false
                                syncMessage = error.message ?: "Nie udało się zeskanować kodu QR."
                            }
                    },
                    enabled = !importInProgress,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (importInProgress) "Trwa import..." else "Skanuj QR synchronizacji")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Demo: commander/commander123, soldier1/soldier123",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = syncMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
        authController.authError?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
