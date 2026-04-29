package com.aylar.bledualrole

import com.aylar.bledualrole.presentation.ChatViewModel
import com.aylar.bledualrole.presentation.DebugViewModel
import com.aylar.bledualrole.presentation.FileTransferViewModel
import com.aylar.bledualrole.presentation.PeerListViewModel

/**
 * Platform-supplied factory for shared ViewModels and platform-specific actions.
 *
 * Android creates this in MainActivity with the real BLE stack and Room/SQLDelight
 * repositories.  Desktop and tests supply a no-op or stub implementation.
 */
interface AppViewModelProvider {
    fun peerListViewModel(): PeerListViewModel
    fun chatViewModel(peerId: String): ChatViewModel
    fun fileTransferViewModel(peerId: String): FileTransferViewModel
    fun debugViewModel(): DebugViewModel

    fun pickFile(onPicked: (fileName: String, data: ByteArray) -> Unit)
}