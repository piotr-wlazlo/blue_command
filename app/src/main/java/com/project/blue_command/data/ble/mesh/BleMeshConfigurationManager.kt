package com.project.blue_command.data.ble.mesh

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import no.nordicsemi.android.mesh.transport.*

class BleMeshConfigurationManager(
    private val context: Context,
    private val meshRepository: MeshRepository,
    // Wstrzykiwany scope umożliwia kontrolę z testów (TestScope) lub produkcji (IO)
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private var configurationJob: Job? = null

    init {
        externalScope.launch {
            meshRepository.incomingMeshMessages.collect { message ->
                handleIncomingMessage(message)
            }
        }
    }

    private var targetNodeAddress: Int = -1
    private var isConfiguring = false

    fun startConfiguration(unicastAddress: Int) {
        if (isConfiguring) return
        targetNodeAddress = unicastAddress
        isConfiguring = true
        Log.i("BleMeshConfig", "Rozpoczynam konfigurację węzła $targetNodeAddress")

        configurationJob?.cancel()
        configurationJob = externalScope.launch {
            var attempts = 0
            while (isActive && isConfiguring && attempts < 10) {
                delay(3000)
                Log.i("BleMeshConfig", "Krok 1 (próba ${attempts + 1}): Wysłanie ConfigCompositionDataGet")
                meshRepository.sendConfigPdu(targetNodeAddress, ConfigCompositionDataGet())
                attempts++
            }
            if (isConfiguring && attempts >= 10) {
                Log.e("BleMeshConfig", "Konfiguracja węzła $targetNodeAddress NIE POWIODŁA SIĘ.")
                isConfiguring = false
            }
        }
    }

    private fun handleIncomingMessage(message: MeshMessage) {
        if (!isConfiguring) return

        when (message) {
            is ConfigCompositionDataStatus -> {
                Log.i("BleMeshConfig", "Odebrano ConfigCompositionDataStatus!")
                configurationJob?.cancel()

                externalScope.launch {
                    delay(500)
                    val appKey = meshRepository.getFirstAppKey()
                    val netKey = meshRepository.getPrimaryNetworkKey()
                    if (appKey != null && netKey != null) {
                        Log.i("BleMeshConfig", "Krok 2: Wysłanie ConfigAppKeyAdd")
                        meshRepository.sendConfigPdu(targetNodeAddress, ConfigAppKeyAdd(netKey, appKey))
                    } else {
                        Log.e("BleMeshConfig", "Brak AppKey lub NetKey w sieci!")
                        isConfiguring = false
                    }
                }
            }
            is ConfigAppKeyStatus -> {
                if (message.isSuccessful) {
                    Log.i("BleMeshConfig", "Odebrano ConfigAppKeyStatus (Sukces)!")
                    externalScope.launch {
                        delay(500)
                        Log.i("BleMeshConfig", "Krok 3: Wysłanie ConfigModelAppBind")
                        val appKeyIndex = meshRepository.getFirstAppKey()?.keyIndex ?: 0
                        meshRepository.sendConfigPdu(
                            targetNodeAddress,
                            ConfigModelAppBind(targetNodeAddress, 0x1000, appKeyIndex)
                        )
                    }
                } else {
                    Log.e("BleMeshConfig", "ConfigAppKeyAdd błąd: ${message.statusCodeName}")
                    isConfiguring = false
                }
            }
            is ConfigModelAppStatus -> {
                if (message.isSuccessful) {
                    Log.i("BleMeshConfig", "Odebrano ConfigModelAppStatus (Sukces)!")
                    externalScope.launch {
                        delay(500)
                        val groupAddress = meshRepository.getGroupSubscriptionAddress()
                        Log.i("BleMeshConfig", "Krok 4: Wysłanie ConfigModelSubscriptionAdd (0x${groupAddress.toString(16)})")
                        meshRepository.sendConfigPdu(
                            targetNodeAddress,
                            ConfigModelSubscriptionAdd(targetNodeAddress, groupAddress, 0x1000)
                        )
                    }
                } else {
                    Log.e("BleMeshConfig", "ConfigModelAppBind błąd: ${message.statusCodeName}")
                    isConfiguring = false
                }
            }
            is ConfigModelSubscriptionStatus -> {
                if (message.isSuccessful) {
                    Log.i("BleMeshConfig", "KONFIGURACJA ZAKOŃCZONA! Węzeł gotowy do odbierania rozkazów.")
                    isConfiguring = false
                } else {
                    Log.e("BleMeshConfig", "ConfigModelSubscriptionAdd błąd: ${message.statusCodeName}")
                    isConfiguring = false
                }
            }
        }
    }
}
