package com.project.blue_command.data.ble.mesh

import android.content.Context
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import no.nordicsemi.android.mesh.transport.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Testy jednostkowe dla BleMeshConfigurationManager.
 *
 * Weryfikują maszynę stanów konfiguracji węzła Mesh:
 *   startConfiguration
 *     -> (3s delay) -> sendConfigPdu(ConfigCompositionDataGet)
 *     -> [CompositionDataStatus] -> sendConfigPdu(ConfigAppKeyAdd)
 *     -> [AppKeyStatus OK] -> sendConfigPdu(ConfigModelAppBind)
 *     -> [ModelAppStatus OK] -> sendConfigPdu(ConfigModelSubscriptionAdd)
 *     -> [ModelSubscriptionStatus OK] -> konfiguracja zakończona
 *
 * Wzorzec testowy:
 * - BleMeshConfigurationManager otrzymuje `backgroundScope` z `runTest {}` jako externalScope.
 * - Dzięki temu nieskończona coroutyna collect{} nie powoduje UncompletedCoroutinesError.
 * - advanceTimeBy() kontroluje wirtualny czas opóźnień wewnątrz klasy.
 * - MeshRepository jest mockowany bez dotykania niemockowalnego MeshManagerApi (JNI).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BleMeshConfigurationManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var context: Context
    private lateinit var meshRepository: MeshRepository

    private val incomingMeshMessages = MutableSharedFlow<MeshMessage>(extraBufferCapacity = 16)
    private val targetNodeAddress = 0x0002

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        meshRepository = mockk(relaxed = true)
        every { meshRepository.incomingMeshMessages } returns incomingMeshMessages
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // Pomocnicza metoda: tworzy manager z backgroundScope (długotrwała coroutyna collect{})
    private fun TestScope.createManager() = BleMeshConfigurationManager(
        context,
        meshRepository,
        externalScope = backgroundScope
    )

    /**
     * Test 1: ConfigCompositionDataGet jest wysyłany po 3 sekundach.
     */
    @Test
    fun `startConfiguration sends ConfigCompositionDataGet after 3s delay`() = testScope.runTest {
        val manager = createManager()
        manager.startConfiguration(targetNodeAddress)
        advanceTimeBy(3100)

        verify(exactly = 1) {
            meshRepository.sendConfigPdu(eq(targetNodeAddress), ofType(ConfigCompositionDataGet::class))
        }
    }

    /**
     * Test 2: Przed upłynięciem 3s opóźnienia nie powinno być żadnych PDU.
     */
    @Test
    fun `startConfiguration does NOT send PDU before delay expires`() = testScope.runTest {
        val manager = createManager()
        manager.startConfiguration(targetNodeAddress)
        advanceTimeBy(1500)

        verify(exactly = 0) {
            meshRepository.sendConfigPdu(any(), any())
        }
    }

    /**
     * Test 3: Po otrzymaniu CompositionDataStatus manager przechodzi do kroku 2 (AppKeyAdd).
     * UWAGA: getFirstAppKey() i getPrimaryNetworkKey() zwracają null (relaxed mock) —
     * test weryfikuje że AppKeyAdd NIE jest wysyłane gdy brakuje kluczy.
     * Test 3b (poniżej) weryfikuje wysłanie AppKeyAdd gdy klucze istnieją.
     */
    @Test
    fun `CompositionDataStatus with missing keys does not proceed to AppKeyAdd`() = testScope.runTest {
        // Zmuszamy mocka do zwracania null, żeby symulować brak kluczy (relaxed mock zwracałby niepoprawne instancje)
        every { meshRepository.getFirstAppKey() } returns null
        every { meshRepository.getPrimaryNetworkKey() } returns null

        val manager = createManager()
        manager.startConfiguration(targetNodeAddress)
        advanceTimeBy(3100)

        incomingMeshMessages.emit(mockk<ConfigCompositionDataStatus>(relaxed = true))
        advanceTimeBy(600)

        // Brak kluczy → AppKeyAdd nie zostaje wysłane
        verify(exactly = 0) {
            meshRepository.sendConfigPdu(eq(targetNodeAddress), ofType(ConfigAppKeyAdd::class))
        }
    }

    /**
     * Test 3b: Gdy klucze są dostępne, CompositionDataStatus powoduje wysłanie AppKeyAdd.
     */
    @Test
    fun `CompositionDataStatus with keys triggers ConfigAppKeyAdd`() = testScope.runTest {
        // Mockujemy dostępność kluczy (AppKey i NetKey) - muszą mieć 16 bajtów
        val fakeAppKey = mockk<no.nordicsemi.android.mesh.ApplicationKey>(relaxed = true) {
            every { key } returns ByteArray(16)
        }
        val fakeNetKey = mockk<no.nordicsemi.android.mesh.NetworkKey>(relaxed = true) {
            every { key } returns ByteArray(16)
        }
        every { meshRepository.getFirstAppKey() } returns fakeAppKey
        every { meshRepository.getPrimaryNetworkKey() } returns fakeNetKey

        val manager = createManager()
        manager.startConfiguration(targetNodeAddress)
        advanceTimeBy(3100)

        incomingMeshMessages.emit(mockk<ConfigCompositionDataStatus>(relaxed = true))
        advanceTimeBy(600)

        verify(exactly = 1) {
            meshRepository.sendConfigPdu(eq(targetNodeAddress), ofType(ConfigAppKeyAdd::class))
        }
    }

    /**
     * Test 4: Po sukcesie AppKeyStatus manager wysyła ConfigModelAppBind.
     */
    @Test
    fun `successful AppKeyStatus triggers ConfigModelAppBind`() = testScope.runTest {
        val fakeAppKey = mockk<no.nordicsemi.android.mesh.ApplicationKey>(relaxed = true) {
            every { key } returns ByteArray(16)
        }
        val fakeNetKey = mockk<no.nordicsemi.android.mesh.NetworkKey>(relaxed = true) {
            every { key } returns ByteArray(16)
        }
        every { meshRepository.getFirstAppKey() } returns fakeAppKey
        every { meshRepository.getPrimaryNetworkKey() } returns fakeNetKey

        val manager = createManager()
        manager.startConfiguration(targetNodeAddress)
        advanceTimeBy(3100)

        incomingMeshMessages.emit(mockk<ConfigCompositionDataStatus>(relaxed = true))
        advanceTimeBy(600)

        val appKeyStatus = mockk<ConfigAppKeyStatus>(relaxed = true)
        every { appKeyStatus.isSuccessful } returns true
        incomingMeshMessages.emit(appKeyStatus)
        advanceTimeBy(600)

        verify(exactly = 1) {
            meshRepository.sendConfigPdu(eq(targetNodeAddress), ofType(ConfigModelAppBind::class))
        }
    }

    /**
     * Test 5: Po sukcesie ModelAppStatus manager wysyła ConfigModelSubscriptionAdd.
     */
    @Test
    fun `successful ModelAppStatus triggers ConfigModelSubscriptionAdd`() = testScope.runTest {
        val fakeAppKey = mockk<no.nordicsemi.android.mesh.ApplicationKey>(relaxed = true) {
            every { key } returns ByteArray(16)
        }
        val fakeNetKey = mockk<no.nordicsemi.android.mesh.NetworkKey>(relaxed = true) {
            every { key } returns ByteArray(16)
        }
        every { meshRepository.getFirstAppKey() } returns fakeAppKey
        every { meshRepository.getPrimaryNetworkKey() } returns fakeNetKey

        val manager = createManager()
        manager.startConfiguration(targetNodeAddress)
        advanceTimeBy(3100)

        incomingMeshMessages.emit(mockk<ConfigCompositionDataStatus>(relaxed = true))
        advanceTimeBy(600)

        val appKeyStatus = mockk<ConfigAppKeyStatus>(relaxed = true)
        every { appKeyStatus.isSuccessful } returns true
        incomingMeshMessages.emit(appKeyStatus)
        advanceTimeBy(600)

        val appBindStatus = mockk<ConfigModelAppStatus>(relaxed = true)
        every { appBindStatus.isSuccessful } returns true
        incomingMeshMessages.emit(appBindStatus)
        advanceTimeBy(600)

        verify(exactly = 1) {
            meshRepository.sendConfigPdu(eq(targetNodeAddress), ofType(ConfigModelSubscriptionAdd::class))
        }
    }

    /**
     * Test 6: Pełna maszyna stanów — łącznie 4 wywołania sendConfigPdu.
     */
    @Test
    fun `full configuration state machine sends 4 PDUs total`() = testScope.runTest {
        val fakeAppKey = mockk<no.nordicsemi.android.mesh.ApplicationKey>(relaxed = true) {
            every { key } returns ByteArray(16)
        }
        val fakeNetKey = mockk<no.nordicsemi.android.mesh.NetworkKey>(relaxed = true) {
            every { key } returns ByteArray(16)
        }
        every { meshRepository.getFirstAppKey() } returns fakeAppKey
        every { meshRepository.getPrimaryNetworkKey() } returns fakeNetKey

        val manager = createManager()
        manager.startConfiguration(targetNodeAddress)
        advanceTimeBy(3100)

        incomingMeshMessages.emit(mockk<ConfigCompositionDataStatus>(relaxed = true))
        advanceTimeBy(600)

        val appKeyStatus = mockk<ConfigAppKeyStatus>(relaxed = true)
        every { appKeyStatus.isSuccessful } returns true
        incomingMeshMessages.emit(appKeyStatus)
        advanceTimeBy(600)

        val appBindStatus = mockk<ConfigModelAppStatus>(relaxed = true)
        every { appBindStatus.isSuccessful } returns true
        incomingMeshMessages.emit(appBindStatus)
        advanceTimeBy(600)

        val subscriptionStatus = mockk<ConfigModelSubscriptionStatus>(relaxed = true)
        every { subscriptionStatus.isSuccessful } returns true
        incomingMeshMessages.emit(subscriptionStatus)
        advanceTimeBy(200)

        // CompositionDataGet + AppKeyAdd + ModelAppBind + SubscriptionAdd = 4
        verify(exactly = 4) {
            meshRepository.sendConfigPdu(eq(targetNodeAddress), any())
        }
    }

    /**
     * Test 7: Niezwiązane wiadomości nie powodują dalszego postępu konfiguracji.
     */
    @Test
    fun `unrelated mesh messages do not trigger AppKeyAdd`() = testScope.runTest {
        val manager = createManager()
        manager.startConfiguration(targetNodeAddress)
        advanceTimeBy(3100)

        // Emitowanie wiadomości innego typu (GenericOnOffStatus nie pasuje do żadnego when-case)
        incomingMeshMessages.emit(mockk<GenericOnOffStatus>(relaxed = true))
        advanceTimeBy(600)

        // Tylko ConfigCompositionDataGet — żadnego AppKeyAdd
        verify(exactly = 0) {
            meshRepository.sendConfigPdu(any(), ofType(ConfigAppKeyAdd::class))
        }
    }
}
