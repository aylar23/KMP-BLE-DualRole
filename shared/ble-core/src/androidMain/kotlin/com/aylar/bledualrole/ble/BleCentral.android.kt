package com.aylar.bledualrole.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter as AndroidScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

class AndroidBleCentral(private val context: Context) : BleCentral {

    private val adapter by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    override fun scan(filter: ScanFilter): Flow<DiscoveredPeer> = callbackFlow {
        val scanner = adapter.bluetoothLeScanner
            ?: throw BleError.BluetoothDisabled()

        val scanFilter = AndroidScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(filter.serviceUuid)))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val peer = DiscoveredPeer(
                    id = PeerId(device.address),
                    name = result.scanRecord?.deviceName ?: device.address,
                    rssi = result.rssi,
                )
                trySend(peer)
            }

            override fun onScanFailed(errorCode: Int) {
                close(BleError.OperationFailed("Scan failed errorCode=$errorCode"))
            }
        }

        scanner.startScan(listOf(scanFilter), settings, callback)
        awaitClose { scanner.stopScan(callback) }
    }

    override suspend fun stopScan() {
        // Scan stops automatically via callbackFlow awaitClose when the collector cancels
    }

    override suspend fun connect(peer: DiscoveredPeer): Connection {
        val device = adapter.getRemoteDevice(peer.id.value)
        val connection = GattConnection(device, context)
        connection.connect()
        return connection
    }
}
