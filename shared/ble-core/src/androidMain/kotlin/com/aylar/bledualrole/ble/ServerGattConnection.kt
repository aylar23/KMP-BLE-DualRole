package com.aylar.bledualrole.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattServer
import android.os.Build
import com.aylar.bledualrole.protocol.BleUuids
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.UUID

internal class ServerGattConnection(
    private val device: BluetoothDevice,
    private val server: BluetoothGattServer,
) : Connection {

    override val peerId: PeerId = PeerId(device.address)

    private val _state = MutableStateFlow(ConnectionState.READY)
    override val state: StateFlow<ConnectionState> = _state

    private val _mtu = MutableStateFlow(DEFAULT_MTU)
    override val mtu: StateFlow<Int> = _mtu

    private val _incoming = Channel<ByteArray>(Channel.BUFFERED)
    override val incoming: Flow<ByteArray> = _incoming.receiveAsFlow()

    fun onDataReceived(bytes: ByteArray) {
        _incoming.trySend(bytes)
    }

    fun onMtuChanged(mtu: Int) {
        _mtu.value = mtu
    }

    @Suppress("DEPRECATION")
    override suspend fun send(bytes: ByteArray) {
        val rxChar = server.getService(UUID.fromString(BleUuids.SERVICE))
            ?.getCharacteristic(UUID.fromString(BleUuids.CHAR_RX))
            ?: throw BleError.OperationFailed("RX characteristic not found")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            server.notifyCharacteristicChanged(device, rxChar, false, bytes)
        } else {
            rxChar.value = bytes
            server.notifyCharacteristicChanged(device, rxChar, false)
        }
    }

    override suspend fun requestMtu(size: Int): Int = _mtu.value

    override suspend fun bond(): BondResult {
        return when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> BondResult.AlreadyBonded
            else -> {
                device.createBond()
                BondResult.Bonded
            }
        }
    }

    override suspend fun close() {
        _state.value = ConnectionState.DISCONNECTING
        server.cancelConnection(device)
        _state.value = ConnectionState.DISCONNECTED
        _incoming.close()
    }

    companion object {
        private const val DEFAULT_MTU = 23
    }
}