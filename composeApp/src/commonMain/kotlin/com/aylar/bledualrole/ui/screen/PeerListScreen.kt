package com.aylar.bledualrole.ui.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aylar.bledualrole.domain.model.Peer
import com.aylar.bledualrole.domain.session.ConnectedPeerInfo
import com.aylar.bledualrole.domain.session.PeerConnectionStatus
import com.aylar.bledualrole.presentation.PeerListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerListScreen(
    vm: PeerListViewModel,
    onOpenChat: (peerId: String, peerName: String) -> Unit,
    onOpenDebug: () -> Unit,
) {
    val peers by vm.peers.collectAsState()
    val connected by vm.connectedPeers.collectAsState()
    val scanning by vm.isScanning.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devices") },
                actions = {
                    OutlinedButton(
                        onClick = onOpenDebug,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Debug") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (scanning) {
                    OutlinedButton(onClick = vm::stopScan, modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Scanning…")
                        }
                    }
                } else {
                    Button(onClick = vm::startScan, modifier = Modifier.weight(1f)) {
                        Text("Scan")
                    }
                }
            }

            if (connected.isNotEmpty()) {
                Text(
                    "Connected",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                connected.forEach { info ->
                    ConnectedPeerRow(info, onClick = { onOpenChat(info.id, info.name) })
                    HorizontalDivider()
                }
            }

            if (peers.isNotEmpty()) {
                Text(
                    "Bonded / Known",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.secondary,
                )
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(peers, key = { it.id }) { peer ->
                        KnownPeerRow(
                            peer = peer,
                            connected = connected.any { it.id == peer.id },
                            onConnect = { vm.connect(peer.id) },
                            onChat = { onOpenChat(peer.id, peer.name) },
                        )
                        HorizontalDivider()
                    }
                }
            } else if (!scanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No known devices. Tap Scan to discover nearby peers.")
                }
            }
        }
    }
}

@Composable
private fun ConnectedPeerRow(info: ConnectedPeerInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(info.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "MTU ${info.mtu} · RSSI ${info.rssiDbm} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusChip(info.status)
    }
}

@Composable
private fun KnownPeerRow(
    peer: Peer,
    connected: Boolean,
    onConnect: () -> Unit,
    onChat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(peer.name, style = MaterialTheme.typography.bodyLarge)
            Text(peer.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (connected) {
            OutlinedButton(onClick = onChat) { Text("Open") }
        } else {
            Button(onClick = onConnect) { Text("Connect") }
        }
    }
}

@Composable
private fun StatusChip(status: PeerConnectionStatus) {
    val (label, color) = when (status) {
        PeerConnectionStatus.CONNECTED -> "Connected" to MaterialTheme.colorScheme.primary
        PeerConnectionStatus.CONNECTING -> "Connecting" to MaterialTheme.colorScheme.secondary
        PeerConnectionStatus.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.outline
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = color)
}