package com.aylar.bledualrole.ble

import com.aylar.bledualrole.protocol.BleUuids
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithoutResponse
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSError
import platform.darwin.NSObject

internal class CbCentralConnection(
    private val peripheral: CBPeripheral,
) : Connection {

    override val peerId: PeerId = PeerId(peripheral.identifier.UUIDString)

    private val _state = MutableStateFlow(ConnectionState.CONNECTING)
    override val state: StateFlow<ConnectionState> = _state

    private val _mtu = MutableStateFlow(DEFAULT_MTU)
    override val mtu: StateFlow<Int> = _mtu

    private val _incoming = Channel<ByteArray>(Channel.BUFFERED)
    override val incoming: Flow<ByteArray> = _incoming.receiveAsFlow()

    private var txChar: CBCharacteristic? = null

    var pendingConnectContinuation: ((Result<Unit>) -> Unit)? = null

    val peripheralDelegate: CBPeripheralDelegateProtocol = object : NSObject(), CBPeripheralDelegateProtocol {

        override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
            if (didDiscoverServices != null) {
                pendingConnectContinuation?.invoke(
                    Result.failure(BleError.ConnectionFailed(peerId, didDiscoverServices.localizedDescription)),
                )
                return
            }
            val service = peripheral.services
                ?.filterIsInstance<CBService>()
                ?.firstOrNull { it.UUID.UUIDString.equals(BleUuids.SERVICE, ignoreCase = true) }
                ?: run {
                    pendingConnectContinuation?.invoke(
                        Result.failure(BleError.ConnectionFailed(peerId, "BLE service not found")),
                    )
                    return
                }
            _state.value = ConnectionState.MTU_NEGOTIATING
            peripheral.discoverCharacteristics(
                listOf(
                    CBUUID.UUIDWithString(BleUuids.CHAR_TX),
                    CBUUID.UUIDWithString(BleUuids.CHAR_RX),
                ),
                forService = service,
            )
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverCharacteristicsForService: CBService,
            error: NSError?,
        ) {
            if (error != null) {
                pendingConnectContinuation?.invoke(
                    Result.failure(BleError.ConnectionFailed(peerId, error.localizedDescription)),
                )
                return
            }
            val chars = didDiscoverCharacteristicsForService.characteristics
                ?.filterIsInstance<CBCharacteristic>() ?: emptyList()
            txChar = chars.firstOrNull { it.UUID.UUIDString.equals(BleUuids.CHAR_TX, ignoreCase = true) }
            val rxChar = chars.firstOrNull { it.UUID.UUIDString.equals(BleUuids.CHAR_RX, ignoreCase = true) }
            rxChar?.let { peripheral.setNotifyValue(true, forCharacteristic = it) }

            // iOS negotiates MTU automatically; read effective payload size
            val payloadLen = peripheral.maximumWriteValueLengthForType(CBCharacteristicWriteWithoutResponse).toInt()
            _mtu.value = payloadLen + ATT_HEADER_BYTES

            _state.value = ConnectionState.READY
            pendingConnectContinuation?.invoke(Result.success(Unit))
            pendingConnectContinuation = null
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateValueForCharacteristic: CBCharacteristic,
            error: NSError?,
        ) {
            if (error != null) return
            val data = didUpdateValueForCharacteristic.value?.toByteArray() ?: return
            _incoming.trySend(data)
        }
    }

    fun onConnected() {
        _state.value = ConnectionState.DISCOVERING
        peripheral.delegate = peripheralDelegate
        peripheral.discoverServices(listOf(CBUUID.UUIDWithString(BleUuids.SERVICE)))
    }

    fun onDisconnected(error: NSError?) {
        _state.value = ConnectionState.DISCONNECTED
        if (error != null) {
            pendingConnectContinuation?.invoke(
                Result.failure(BleError.ConnectionLost(peerId, error.localizedDescription)),
            )
        }
        _incoming.close()
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun send(bytes: ByteArray) {
        val char = txChar ?: throw BleError.OperationFailed("Not connected")
        peripheral.writeValue(bytes.toNSData(), forCharacteristic = char, type = CBCharacteristicWriteWithResponse)
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun sendNoAck(bytes: ByteArray) {
        val char = txChar ?: throw BleError.OperationFailed("Not connected")
        peripheral.writeValue(bytes.toNSData(), forCharacteristic = char, type = CBCharacteristicWriteWithoutResponse)
    }

    override suspend fun requestMtu(size: Int): Int = _mtu.value

    override suspend fun bond(): BondResult = BondResult.AlreadyBonded

    override suspend fun close() {
        _state.value = ConnectionState.DISCONNECTING
        _incoming.close()
    }

    companion object {
        private const val DEFAULT_MTU = 23
        private const val ATT_HEADER_BYTES = 3
    }
}