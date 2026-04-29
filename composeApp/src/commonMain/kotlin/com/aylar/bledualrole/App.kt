package com.aylar.bledualrole

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aylar.bledualrole.ui.Screen
import com.aylar.bledualrole.ui.screen.ChatScreen
import com.aylar.bledualrole.ui.screen.DebugScreen
import com.aylar.bledualrole.ui.screen.FileTransferScreen
import com.aylar.bledualrole.ui.screen.PeerListScreen

/**
 * Root composable. Screens are navigated via a simple state variable — no external
 * navigation library needed at this stage.
 *
 * [viewModelProvider] is the platform-specific DI entry point; each platform creates
 * ViewModels backed by the real BLE stack and SQLDelight repositories.
 */
@Composable
fun App(viewModelProvider: AppViewModelProvider) {
    MaterialTheme {
        var screen by remember { mutableStateOf<Screen>(Screen.PeerList) }

        when (val s = screen) {
            Screen.PeerList -> PeerListScreen(
                vm = viewModelProvider.peerListViewModel(),
                onOpenChat = { id, name -> screen = Screen.Chat(id, name) },
                onOpenDebug = { screen = Screen.Debug },
            )
            is Screen.Chat -> ChatScreen(
                peerName = s.peerName,
                vm = viewModelProvider.chatViewModel(s.peerId),
                onBack = { screen = Screen.PeerList },
                onOpenFileTransfer = { screen = Screen.FileTransfer(s.peerId) },
            )
            is Screen.FileTransfer -> FileTransferScreen(
                vm = viewModelProvider.fileTransferViewModel(s.peerId),
                onBack = { screen = Screen.PeerList },
                onPickFile = { callback ->
                    // Platform-specific file picker is injected via viewModelProvider
                    viewModelProvider.pickFile(callback)
                },
            )
            Screen.Debug -> DebugScreen(
                vm = viewModelProvider.debugViewModel(),
                onBack = { screen = Screen.PeerList },
            )
        }
    }
}