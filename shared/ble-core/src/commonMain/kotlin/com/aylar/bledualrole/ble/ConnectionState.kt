package com.aylar.bledualrole.ble

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    DISCOVERING,
    MTU_NEGOTIATING,
    READY,
    DISCONNECTING,
}