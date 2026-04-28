package com.aylar.bledualrole.ble

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import platform.CoreBluetooth.CBCentral
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBPeripheralManager

internal class CbServerConnection(
    private val central: CBCentral,
    private val peripheralManager: CBPeripheralManager,
    private val rxChar: CBMutableCharacteristic,
) : Connection {

    override val peerId: PeerId = PeerId(central.identifier.UUIDString)

    private val _state = MutableStateFlow(ConnectionState.READY)
    override val state: StateFlow<ConnectionState> = _state

    private val _mtu = MutableStateFlow(central.maximumUpdateValueLength.toInt())
    override val mtu: StateFlow<Int> = _mtu

    private val _incoming = Channel<ByteArray>(Channel.BUFFERED)
    override val incoming: Flow<ByteArray> = _incoming.receiveAsFlow()

    fun onDataReceived(bytes: ByteArray) {
        _incoming.trySend(bytes)
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun send(bytes: ByteArray) {
        peripheralManager.updateValue(
            bytes.toNSData(),
            forCharacteristic = rxChar,
            onSubscribedCentrals = null,
        )
    }

    override suspend fun requestMtu(size: Int): Int = _mtu.value

    override suspend fun bond(): BondResult = BondResult.AlreadyBonded

    override suspend fun close() {
        _state.value = ConnectionState.DISCONNECTING
        _incoming.close()
        _state.value = ConnectionState.DISCONNECTED
    }
}