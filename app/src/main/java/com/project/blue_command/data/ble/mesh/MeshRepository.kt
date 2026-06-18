package com.project.blue_command.data.ble.mesh

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import no.nordicsemi.android.mesh.MeshManagerApi
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.MeshStatusCallbacks
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.transport.ControlMessage
import no.nordicsemi.android.mesh.transport.MeshMessage
import no.nordicsemi.android.mesh.transport.GenericOnOffSetUnacknowledged
import no.nordicsemi.android.mesh.transport.VendorModelMessageUnacked

open class MeshRepository(context: Context) {
    val meshManagerApi = MeshManagerApi(context)
    
    private val _incomingMessages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<ByteArray> = _incomingMessages.asSharedFlow()

    private val _outgoingPdu = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val outgoingPdu: SharedFlow<ByteArray> = _outgoingPdu.asSharedFlow()

    private val _outgoingProvisioningPdu = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val outgoingProvisioningPdu: SharedFlow<ByteArray> = _outgoingProvisioningPdu.asSharedFlow()

    private val _incomingMeshMessages = MutableSharedFlow<MeshMessage>(extraBufferCapacity = 64)
    val incomingMeshMessages: SharedFlow<MeshMessage> = _incomingMeshMessages.asSharedFlow()

    val configurationManager by lazy { BleMeshConfigurationManager(context, this) }



    init {
        meshManagerApi.setMeshManagerCallbacks(object : no.nordicsemi.android.mesh.MeshManagerCallbacks {
            override fun onNetworkLoaded(meshNetwork: MeshNetwork) {
                Log.d("MeshRepository", "Sieć Mesh pomyślnie załadowana: ${meshNetwork.meshUUID}")
            }
            override fun onNetworkUpdated(meshNetwork: MeshNetwork) {
                Log.d("MeshRepository", "Sieć Mesh zaktualizowana")
            }
            override fun onNetworkLoadFailed(error: String) {
                Log.e("MeshRepository", "Błąd ładowania sieci: $error. Generowanie nowej sieci...")
            }
            override fun onNetworkImported(meshNetwork: MeshNetwork) {}
            override fun onNetworkImportFailed(error: String) {}
            override fun sendProvisioningPdu(meshNode: UnprovisionedMeshNode, pdu: ByteArray) {
                Log.d("MeshRepository", "Wygenerowano pakiet PROVISIONING PDU, przekazuję do wysłania: ${pdu.size} bajtów")
                _outgoingProvisioningPdu.tryEmit(pdu)
            }
            override fun onMeshPduCreated(pdu: ByteArray) {
                Log.d("MeshRepository", "Wygenerowano pakiet PDU, przekazuję do wysłania: ${pdu.size} bajtów")
                _outgoingPdu.tryEmit(pdu)
            }
            override fun getMtu(): Int = 20
        })

        meshManagerApi.setMeshStatusCallbacks(object : MeshStatusCallbacks {
            override fun onTransactionFailed(dst: Int, hasIncompleteTimerExpired: Boolean) {}
            override fun onUnknownPduReceived(src: Int, accessPayload: ByteArray?) {}
            override fun onBlockAcknowledgementProcessed(dst: Int, message: ControlMessage) {}
            override fun onBlockAcknowledgementReceived(src: Int, message: ControlMessage) {}
            override fun onMeshMessageProcessed(dst: Int, meshMessage: MeshMessage) {
                if (meshMessage is VendorModelMessageUnacked) {
                    val payload = meshMessage.parameters
                    if (payload != null) {
                        Log.d("MeshRepository", "Odebrano wiadomość VendorModel, payload = ${payload.size} bajtów")
                        _incomingMessages.tryEmit(payload)
                    }
                }
            }
            override fun onMeshMessageReceived(src: Int, meshMessage: MeshMessage) {
                Log.d("MeshRepository", "Odebrano MeshMessage od $src")
                _incomingMeshMessages.tryEmit(meshMessage)
            }
            override fun onMessageDecryptionFailed(meshLayer: String, errorMessage: String) {}
        })

        meshManagerApi.setProvisioningStatusCallbacks(object : no.nordicsemi.android.mesh.MeshProvisioningStatusCallbacks {
            override fun onProvisioningStateChanged(meshNode: UnprovisionedMeshNode, state: no.nordicsemi.android.mesh.provisionerstates.ProvisioningState.States, data: ByteArray?) {
                Log.d("MeshRepository", "Stan provisioningu: $state")
                if (state == no.nordicsemi.android.mesh.provisionerstates.ProvisioningState.States.PROVISIONING_CAPABILITIES) {
                    Log.i("MeshRepository", "Węzeł podał swoje Capabilities. Automatycznie rozpoczynam właściwy Provisioning (No OOB)...")
                    try {
                        meshManagerApi.startProvisioning(meshNode)
                    } catch (e: Exception) {
                        Log.e("MeshRepository", "Błąd startProvisioning: ${e.message}")
                    }
                }
            }
            override fun onProvisioningFailed(meshNode: UnprovisionedMeshNode, state: no.nordicsemi.android.mesh.provisionerstates.ProvisioningState.States, data: ByteArray?) {
                Log.e("MeshRepository", "Provisioning NIE POWIÓDŁ SIĘ na stanie: $state")
            }
            override fun onProvisioningCompleted(meshNode: no.nordicsemi.android.mesh.transport.ProvisionedMeshNode, state: no.nordicsemi.android.mesh.provisionerstates.ProvisioningState.States, data: ByteArray?) {
                Log.i("MeshRepository", "Provisioning SUKCES! Węzeł został dodany do sieci.")
                // Konfiguracja rozpocznie się z opóźnieniem, gdy ESP32 zrestartuje się jako Proxy
                configurationManager.startConfiguration(meshNode.unicastAddress)
            }
        })

        
        try {
            meshManagerApi.loadMeshNetwork()
        } catch (e: Exception) {
            Log.e("MeshRepository", "Brak istniejącej sieci Mesh lub błąd inicjalizacji: ${e.message}")
            // Tutaj powołamy nową sieć
            setupNewNetwork()
        }
    }

    private fun setupNewNetwork() {
        Log.i("MeshRepository", "Generowanie nowej sieci bazującej na aktywnym kluczu grupy...")
        val activeGroup = com.project.blue_command.data.SessionRepository.activeGroup.value
        val groupKeyBase64 = activeGroup?.groupKeyBase64 ?: return

        try {
            val keyBytes = com.project.blue_command.security.EncryptionManager().decodeKeyFromBase64(groupKeyBase64)
            // Z klucza szyfrowania robimy dwa niezależne 16-bajtowe klucze (NetKey, AppKey)
            // Jeśli ma 32 bajty (AES-256), dzielimy na pół. Jeśli 16, używamy tego samego lub hashowanego.
            val netKeyBytes = ByteArray(16)
            val appKeyBytes = ByteArray(16)
            
            if (keyBytes.size >= 32) {
                System.arraycopy(keyBytes, 0, netKeyBytes, 0, 16)
                System.arraycopy(keyBytes, 16, appKeyBytes, 0, 16)
            } else {
                // Awaryjne, dla mniejszych kluczy - powielamy lub kopiujemy
                for (i in 0 until 16) {
                    netKeyBytes[i] = keyBytes[i % keyBytes.size]
                    appKeyBytes[i] = keyBytes[(i + 1) % keyBytes.size]
                }
            }
            
        } catch (e: Exception) {
            Log.e("MeshRepository", "Błąd tworzenia sieci: ${e.message}")
        }
    }

    fun handleNotifications(data: ByteArray) {
        try {
            meshManagerApi.handleNotifications(20, data)
        } catch (e: Exception) {
            Log.e("MeshRepository", "Błąd podczas przetwarzania pakietu PDU: ${e.message}")
        }
    }

    fun handleProvisioningNotifications(data: ByteArray) {
        try {
            meshManagerApi.handleNotifications(20, data)
        } catch (e: Exception) {
            Log.e("MeshRepository", "Błąd podczas przetwarzania Provisioning PDU: ${e.message}")
        }
    }

    fun startProvisioning(uuidBytes: ByteArray) {
        try {
            val bb = java.nio.ByteBuffer.wrap(uuidBytes)
            val most = bb.long
            val least = bb.long
            val deviceUuid = java.util.UUID(most, least)
            Log.i("MeshRepository", "Zlecam identyfikację urządzenia o UUID: $deviceUuid")
            meshManagerApi.identifyNode(deviceUuid)
        } catch (e: Exception) {
            Log.e("MeshRepository", "Błąd podczas identyfikacji: ${e.message}")
        }
    }

    fun sendGenericOnOffMessage(isOn: Boolean, appKeyIndex: Int = 0) {
        val appKey = meshManagerApi.meshNetwork?.appKeys?.getOrNull(appKeyIndex)
        if (appKey == null) {
            Log.e("MeshRepository", "Brak dostępnego AppKey, nie można wysłać wiadomości.")
            return
        }

        val activeGroup = com.project.blue_command.data.SessionRepository.activeGroup.value
        val dstAddress = if (activeGroup != null) {
            0xC000 + (activeGroup.id.hashCode() and 0x3EFF)
        } else {
            0xC000
        }
        
        val unackedMessage = GenericOnOffSetUnacknowledged(appKey, isOn, (System.currentTimeMillis() and 0xFF).toInt())
        
        try {
            meshManagerApi.createMeshPdu(dstAddress, unackedMessage)
        } catch (e: Exception) {
            Log.e("MeshRepository", "Błąd tworzenia PDU: ${e.message}")
        }
    }

    /**
     * Wrapper do wysyłania konfiguracyjnych wiadomości Control Plane.
     * Wywoływany przez BleMeshConfigurationManager.
     * Osobna metoda umożliwia łatwe mockowanie w testach jednostkowych.
     */
    open fun sendConfigPdu(unicastAddress: Int, message: no.nordicsemi.android.mesh.transport.MeshMessage) {
        try {
            meshManagerApi.createMeshPdu(unicastAddress, message)
        } catch (e: Exception) {
            Log.e("MeshRepository", "Błąd wysyłania konfiguracyjnego PDU: ${e.message}")
        }
    }

    /**
     * Zwraca AppKey z sieci, lub null jeśli sieć nie jest załadowana.
     * Osobna metoda umożliwia mockowanie w testach.
     */
    open fun getFirstAppKey() = meshManagerApi.meshNetwork?.appKeys?.firstOrNull()

    /**
     * Zwraca PrimaryNetworkKey z sieci, lub null jeśli sieć nie jest załadowana.
     * Osobna metoda umożliwia mockowanie w testach.
     */
    open fun getPrimaryNetworkKey() = meshManagerApi.meshNetwork?.primaryNetworkKey

    /**
     * Zwraca adres subskrypcji zsynchronizowany z aktywną grupą.
     */
    fun getGroupSubscriptionAddress(): Int {
        val activeGroup = com.project.blue_command.data.SessionRepository.activeGroup.value
        return if (activeGroup != null) {
            0xC000 + (activeGroup.id.hashCode() and 0x3EFF)
        } else {
            0xC000
        }
    }
}
