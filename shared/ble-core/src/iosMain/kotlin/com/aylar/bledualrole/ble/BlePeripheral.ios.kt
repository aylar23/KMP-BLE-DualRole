package com.aylar.bledualrole.ble

import com.aylar.bledualrole.protocol.BleUuids
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBCentral
import platform.CoreBluetooth.CBCharacteristicPropertyIndicate
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBPeripheralManagerStatePoweredOn
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSError
import platform.darwin.NSObject

class IosBlePeripheral : BlePeripheral {

    private var peripheralManager: CBPeripheralManager? = null

    override suspend fun startAdvertising(config: AdvertiseConfig) {
        // Advertising starts inside incomingConnections() after power-on
    }

    override fun incomingConnections(): Flow<Connection> = callbackFlow {
        // Track centrals that subscribed to RX. Note: didSubscribeToCharacteristic and
        // didUnsubscribeFromCharacteristic map to the same Kotlin/Native function signature
        // (known ObjC overload conflict). Subscribe is handled via write requests as a fallback;
        // proper subscription tracking added in Phase 6.
        val connections = mutableMapOf<String, CbServerConnection>()
        var rxCharHolder: CBMutableCharacteristic? = null

        fun buildService(pm: CBPeripheralManager) {
            val txChar = CBMutableCharacteristic(
                type = CBUUID.UUIDWithString(BleUuids.CHAR_TX),
                properties = CBCharacteristicPropertyWrite or CBCharacteristicPropertyWriteWithoutResponse,
                value = null,
                permissions = 0x06u,
            )
            val rxChar = CBMutableCharacteristic(
                type = CBUUID.UUIDWithString(BleUuids.CHAR_RX),
                properties = CBCharacteristicPropertyNotify,
                value = null,
                permissions = 0x01u,
            )
            rxCharHolder = rxChar
            val controlChar = CBMutableCharacteristic(
                type = CBUUID.UUIDWithString(BleUuids.CHAR_CONTROL),
                properties = CBCharacteristicPropertyWrite or CBCharacteristicPropertyIndicate,
                value = null,
                permissions = 0x04u,
            )
            val infoChar = CBMutableCharacteristic(
                type = CBUUID.UUIDWithString(BleUuids.CHAR_INFO),
                properties = CBCharacteristicPropertyRead,
                value = null,
                permissions = 0x01u,
            )
            val service = CBMutableService(type = CBUUID.UUIDWithString(BleUuids.SERVICE), primary = true)
            service.setCharacteristics(listOf(txChar, rxChar, controlChar, infoChar))
            pm.addService(service)
        }

        val delegate: CBPeripheralManagerDelegateProtocol = object : NSObject(), CBPeripheralManagerDelegateProtocol {

            override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
                if (peripheral.state == CBPeripheralManagerStatePoweredOn) {
                    buildService(peripheral)
                    peripheral.startAdvertising(
                        mapOf("CBAdvertisementDataServiceUUIDsKey" to listOf(CBUUID.UUIDWithString(BleUuids.SERVICE))),
                    )
                }
            }

            override fun peripheralManager(peripheral: CBPeripheralManager, didAddService: CBService, error: NSError?) {}

            // Incoming writes from centrals — create a connection on first write if not yet tracked
            override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveWriteRequests: List<*>) {
                didReceiveWriteRequests.filterIsInstance<CBATTRequest>().forEach { request ->
                    @OptIn(ExperimentalForeignApi::class)
                    val bytes = request.value?.toByteArray() ?: ByteArray(0)
                    val centralId = request.central.identifier.UUIDString
                    val conn = connections.getOrPut(centralId) {
                        val rxChar = rxCharHolder ?: return@forEach
                        val newConn = CbServerConnection(request.central, peripheral, rxChar)
                        trySend(newConn)
                        newConn
                    }
                    conn.onDataReceived(bytes)
                    peripheral.respondToRequest(request, withResult = CBATTErrorSuccess)
                }
            }
        }

        val manager = CBPeripheralManager(delegate = delegate, queue = null)
        peripheralManager = manager

        awaitClose {
            manager.stopAdvertising()
            manager.removeAllServices()
            peripheralManager = null
        }
    }

    override suspend fun stopAdvertising() {
        peripheralManager?.stopAdvertising()
    }
}