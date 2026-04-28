package com.aylar.bledualrole.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import com.aylar.bledualrole.protocol.BleUuids
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

class AndroidBlePeripheral(private val context: Context) : BlePeripheral {

    private val bluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val adapter get() = bluetoothManager.adapter

    private var advertiseCallback: AdvertiseCallback? = null
    private var gattServer: BluetoothGattServer? = null

    override suspend fun startAdvertising(config: AdvertiseConfig) {
        val advertiser = adapter.bluetoothLeAdvertiser
            ?: throw BleError.BluetoothDisabled()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(UUID.fromString(config.serviceUuid)))
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                // Logged; callers observe state changes for error handling
            }
        }

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    override fun incomingConnections(): Flow<Connection> = callbackFlow {
        val connections = mutableMapOf<String, ServerGattConnection>()

        val serverCallback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int,
            ) {
                if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                    val conn = ServerGattConnection(device, gattServer!!)
                    connections[device.address] = conn
                    trySend(conn)
                } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                    connections.remove(device.address)
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray,
            ) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
                if (characteristic.uuid == UUID.fromString(BleUuids.CHAR_TX)) {
                    connections[device.address]?.onDataReceived(value)
                }
            }

            override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
                connections[device.address]?.onMtuChanged(mtu)
            }
        }

        gattServer = bluetoothManager.openGattServer(context, serverCallback)
        addBleService()

        awaitClose {
            gattServer?.close()
            gattServer = null
        }
    }

    override suspend fun stopAdvertising() {
        advertiseCallback?.let { adapter.bluetoothLeAdvertiser?.stopAdvertising(it) }
        advertiseCallback = null
    }

    private fun addBleService() {
        val service = BluetoothGattService(
            UUID.fromString(BleUuids.SERVICE),
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )

        val txChar = BluetoothGattCharacteristic(
            UUID.fromString(BleUuids.CHAR_TX),
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )

        val rxChar = BluetoothGattCharacteristic(
            UUID.fromString(BleUuids.CHAR_RX),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        ).also { char ->
            char.addDescriptor(
                BluetoothGattDescriptor(
                    CLIENT_CHARACTERISTIC_CONFIG_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
                ),
            )
        }

        val controlChar = BluetoothGattCharacteristic(
            UUID.fromString(BleUuids.CHAR_CONTROL),
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED,
        )

        val infoChar = BluetoothGattCharacteristic(
            UUID.fromString(BleUuids.CHAR_INFO),
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )

        service.addCharacteristic(txChar)
        service.addCharacteristic(rxChar)
        service.addCharacteristic(controlChar)
        service.addCharacteristic(infoChar)
        gattServer?.addService(service)
    }
}