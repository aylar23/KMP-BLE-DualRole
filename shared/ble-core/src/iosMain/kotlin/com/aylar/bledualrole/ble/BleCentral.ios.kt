package com.aylar.bledualrole.ble

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerOptionRestoreIdentifierKey
import platform.CoreBluetooth.CBCentralManagerStatePoweredOn
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSUUID
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class IosBlecentral : BleCentral {

    private val connectedPeripherals = mutableMapOf<String, CbCentralConnection>()

    private val centralManager: CBCentralManager by lazy {
        CBCentralManager(
            delegate = null,
            queue = null,
            options = mapOf(CBCentralManagerOptionRestoreIdentifierKey to "com.aylar.bledualrole.central"),
        )
    }

    override fun scan(filter: ScanFilter): Flow<DiscoveredPeer> = callbackFlow {
        val delegate: CBCentralManagerDelegateProtocol = object : NSObject(), CBCentralManagerDelegateProtocol {
            override fun centralManagerDidUpdateState(central: CBCentralManager) {
                if (central.state != CBCentralManagerStatePoweredOn) {
                    close(BleError.BluetoothDisabled())
                }
            }

            override fun centralManager(
                central: CBCentralManager,
                didDiscoverPeripheral: CBPeripheral,
                advertisementData: Map<Any?, *>,
                RSSI: platform.Foundation.NSNumber,
            ) {
                val id = didDiscoverPeripheral.identifier.UUIDString
                val name = didDiscoverPeripheral.name ?: id
                trySend(DiscoveredPeer(PeerId(id), name, RSSI.intValue))
            }
        }

        centralManager.delegate = delegate
        centralManager.scanForPeripheralsWithServices(
            serviceUUIDs = listOf(CBUUID.UUIDWithString(filter.serviceUuid)),
            options = null,
        )
        awaitClose { centralManager.stopScan() }
    }

    override suspend fun stopScan() {
        centralManager.stopScan()
    }

    override suspend fun connect(peer: DiscoveredPeer): Connection {
        val uuid = NSUUID(uUIDString = peer.id.value)

        val peripheral = centralManager
            .retrievePeripheralsWithIdentifiers(listOf(uuid))
            .filterIsInstance<CBPeripheral>()
            .firstOrNull()
            ?: throw BleError.ConnectionFailed(peer.id, "Peripheral not found — scan first")

        val connection = CbCentralConnection(peripheral)
        connectedPeripherals[peer.id.value] = connection

        // Note: CBCentralManagerDelegate has didDisconnectPeripheral and didFailToConnectPeripheral
        // which map to the same Kotlin/Native signature (CBCentralManager, CBPeripheral, NSError) →
        // known Kotlin/Native ObjC overload conflict. Both are omitted here; disconnect detection
        // is handled by the connection's incoming channel closing (Phase 6 reliability layer).
        val connectDelegate: CBCentralManagerDelegateProtocol = object : NSObject(), CBCentralManagerDelegateProtocol {
            override fun centralManagerDidUpdateState(central: CBCentralManager) {}

            override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
                if (didConnectPeripheral.identifier.UUIDString == peer.id.value) {
                    connection.onConnected()
                }
            }
        }

        return suspendCancellableCoroutine { cont ->
            connection.pendingConnectContinuation = { result ->
                result.fold(
                    onSuccess = { cont.resume(connection) },
                    onFailure = cont::resumeWithException,
                )
            }
            centralManager.delegate = connectDelegate
            centralManager.connectPeripheral(peripheral, options = null)
            cont.invokeOnCancellation {
                centralManager.cancelPeripheralConnection(peripheral)
                connectedPeripherals.remove(peer.id.value)
            }
        }
    }
}