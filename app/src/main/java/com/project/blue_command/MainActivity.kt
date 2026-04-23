package com.project.blue_command

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.google.firebase.firestore.FirebaseFirestore
import com.project.blue_command.presentation.AuthFlowScreen
import com.project.blue_command.ui.theme.Blue_commandTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val db = FirebaseFirestore.getInstance()
        val testCommand = hashMapOf(
            "status" to "aktywne",
            "wiadomosc" to "BlueCommand zgłasza gotowość!",
            "dowodca" to "Maciek"
        )

        db.collection("test_polaczenia")
            .add(testCommand)
            .addOnSuccessListener { documentReference ->
                Log.d("FIREBASE_TEST", "SUKCES! Dodano dokument o ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e("FIREBASE_TEST", "BŁĄD! Nie udało się dodać dokumentu", e)
            }

        setContent {
            Blue_commandTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        AuthFlowScreen()
                    }
                }
            }
        }
    }
}