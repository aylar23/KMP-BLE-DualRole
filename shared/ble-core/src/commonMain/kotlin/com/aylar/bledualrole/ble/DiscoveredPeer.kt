package com.aylar.bledualrole.ble

data class DiscoveredPeer(
    val id: PeerId,
    val name: String,
    val rssi: Int,
)