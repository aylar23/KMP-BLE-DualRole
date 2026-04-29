package com.aylar.bledualrole.ui

sealed interface Screen {
    data object PeerList : Screen
    data class Chat(val peerId: String, val peerName: String) : Screen
    data class FileTransfer(val peerId: String) : Screen
    data object Debug : Screen
}