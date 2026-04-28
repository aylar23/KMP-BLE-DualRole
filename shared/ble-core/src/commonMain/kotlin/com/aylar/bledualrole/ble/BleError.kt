package com.aylar.bledualrole.ble

sealed class BleError(message: String) : Exception(message) {
    class NotSupported(message: String = "BLE not supported on this device") : BleError(message)
    class PermissionDenied(message: String = "BLE permission denied") : BleError(message)
    class BluetoothDisabled(message: String = "Bluetooth is disabled") : BleError(message)
    class ConnectionFailed(val peerId: PeerId, message: String) : BleError(message)
    class ConnectionLost(val peerId: PeerId, message: String) : BleError(message)
    class OperationFailed(message: String) : BleError(message)
    class BondFailed(val peerId: PeerId, message: String) : BleError(message)
}