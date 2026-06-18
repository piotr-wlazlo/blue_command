package com.project.blue_command.data.ble.mesh

/**
 * Zarys abstrakcyjnego fizycznego połączenia BLE GATT używanego przez MeshManagerApi.
 * Ten interfejs umożliwia niezależne mockowanie fizycznych peryferiów Bluetooth w testach
 * bez potrzeby wciągania Androidowych klas BluetoothDevice czy BluetoothGatt.
 */
interface MeshBleConnection {
    /**
     * Nawiązuje połączenie GATT z węzłem Proxy o podanym adresie MAC.
     */
    fun connectToProxy(macAddress: String)
    
    /**
     * Zamyka połączenie i odpina wszelkie powiązane zasoby BLE.
     */
    fun disconnectProxy()
    
    /**
     * Rejestruje nasłuchiwanie na nowo odebrane bajty z charakterystyki Mesh Proxy Data Out.
     */
    fun setOnDataReceivedListener(listener: (ByteArray) -> Unit)
    
    /**
     * Pisze bajty bezpośrednio do charakterystyki Mesh Proxy Data In węzła.
     */
    fun sendData(data: ByteArray)
    
    /**
     * Rejestruje nasłuch na moment, w którym połączenie BLE jest w pełni gotowe do wymiany danych (pojawienie się charakterystyk).
     */
    fun setOnDeviceReadyListener(listener: () -> Unit)
}
