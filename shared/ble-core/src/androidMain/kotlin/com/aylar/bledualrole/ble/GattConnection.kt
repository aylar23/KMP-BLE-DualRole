package com.aylar.bledualrole.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.aylar.bledualrole.protocol.BleUuids
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

internal class GattConnection(
    private val device: BluetoothDevice,
    private val context: Context,
) : Connection {

    override val peerId: PeerId = PeerId(device.address)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = _state

    private val _mtu = MutableStateFlow(DEFAULT_MTU)
    override val mtu: StateFlow<Int> = _mtu

    private val _incoming = Channel<ByteArray>(Channel.BUFFERED)
    override val incoming: Flow<ByteArray> = _incoming.receiveAsFlow()

    private var gatt: BluetoothGatt? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private var rxChar: BluetoothGattCharacteristic? = null

    private var pendingMtuContinuation: ((Result<Int>) -> Unit)? = null
    private var pendingConnectContinuation: ((Result<Unit>) -> Unit)? = null
    private var pendingBondContinuation: ((Result<BondResult>) -> Unit)? = null

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                // Android STATUS_133 and similar — close and surface the error
                handleGattError(status)
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = ConnectionState.DISCOVERING
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _state.value = ConnectionState.DISCONNECTED
                    pendingConnectContinuation?.invoke(
                        Result.failure(BleError.ConnectionLost(peerId, "Disconnected")),
                    )
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handleGattError(status)
                return
            }
            val service = gatt.getService(UUID.fromString(BleUuids.SERVICE))
            if (service == null) {
                pendingConnectContinuation?.invoke(
                    Result.failure(BleError.ConnectionFailed(peerId, "BleUuid service not found")),
                )
                return
            }
            txChar = service.getCharacteristic(UUID.fromString(BleUuids.CHAR_TX))
            rxChar = service.getCharacteristic(UUID.fromString(BleUuids.CHAR_RX))
            subscribeToRx(gatt)
            _state.value = ConnectionState.MTU_NEGOTIATING
            gatt.requestMtu(MAX_MTU)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            _mtu.value = mtu
            _state.value = ConnectionState.READY
            pendingConnectContinuation?.invoke(Result.success(Unit))
            pendingConnectContinuation = null
            pendingMtuContinuation?.invoke(Result.success(mtu))
            pendingMtuContinuation = null
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid == UUID.fromString(BleUuids.CHAR_RX)) {
                val value = characteristic.value ?: return
                scope.launch { _incoming.send(value) }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == UUID.fromString(BleUuids.CHAR_RX)) {
                scope.launch { _incoming.send(value) }
            }
        }
    }

    suspend fun connect() {
        _state.value = ConnectionState.CONNECTING
        suspendCancellableCoroutine { cont ->
            pendingConnectContinuation = { result ->
                result.fold(
                    onSuccess = { cont.resume(Unit) },
                    onFailure = { cont.resumeWithException(it) },
                )
            }
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            cont.invokeOnCancellation {
            gatt?.disconnect()
            gatt?.close()
            gatt = null
        }
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun send(bytes: ByteArray) {
        val char = txChar ?: throw BleError.OperationFailed("Not connected")
        val g = gatt ?: throw BleError.OperationFailed("Not connected")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            char.value = bytes
            g.writeCharacteristic(char)
        }
    }

    override suspend fun requestMtu(size: Int): Int {
        val g = gatt ?: throw BleError.OperationFailed("Not connected")
        return suspendCancellableCoroutine { cont ->
            pendingMtuContinuation = { result ->
                result.fold(onSuccess = cont::resume, onFailure = cont::resumeWithException)
            }
            g.requestMtu(size)
        }
    }

    override suspend fun bond(): BondResult {
        return when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> BondResult.AlreadyBonded
            else -> suspendCancellableCoroutine { cont ->
                pendingBondContinuation = { result ->
                    result.fold(onSuccess = cont::resume, onFailure = cont::resumeWithException)
                }
                device.createBond()
            }
        }
    }

    override suspend fun close() {
        _state.value = ConnectionState.DISCONNECTING
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.value = ConnectionState.DISCONNECTED
        _incoming.close()
    }

    private fun handleGattError(status: Int) {
        // STATUS_133 is the Android vendor-specific GATT error — close cleanly and signal failure
        gatt?.close()
        gatt = null
        _state.value = ConnectionState.DISCONNECTED
        pendingConnectContinuation?.invoke(
            Result.failure(BleError.ConnectionFailed(peerId, "GATT error status=$status")),
        )
        pendingConnectContinuation = null
    }

    @Suppress("DEPRECATION")
    private fun subscribeToRx(gatt: BluetoothGatt) {
        val char = rxChar ?: return
        gatt.setCharacteristicNotification(char, true)
        val descriptor = char.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    companion object {
        private const val DEFAULT_MTU = 23
        private const val MAX_MTU = 517
    }
}
