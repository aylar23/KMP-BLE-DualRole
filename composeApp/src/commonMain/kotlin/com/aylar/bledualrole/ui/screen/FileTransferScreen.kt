package com.aylar.bledualrole.ui.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aylar.bledualrole.domain.model.Transfer
import com.aylar.bledualrole.domain.model.TransferDirection
import com.aylar.bledualrole.domain.model.TransferStatus
import com.aylar.bledualrole.presentation.FileTransferViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTransferScreen(
    vm: FileTransferView
    Model,
    onBack: () -> Unit,
    onPickFile: (onPicked: (fileName: String, data: ByteArray) -> Unit) -> Unit,
) {
    val transfers by vm.transfers.collectAsState()
    val error by vm.sendError.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        if (error != null) {
            snackbar.showSnackbar(error!!)
            vm.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("File Transfer") },
                actions = {
                    Button(
                        onClick = { onPickFile { name, data -> vm.sendFile(name, data) } },
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Send File") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(transfers, key = { it.id }) { transfer ->
                TransferRow(transfer)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TransferRow(transfer: Transfer) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(transfer.fileName, style = MaterialTheme.typography.bodyLarge)
            val dirLabel = if (transfer.direction == TransferDirection.SEND) "↑" else "↓"
            Text(
                "$dirLabel ${formatBytes(transfer.fileSizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (transfer.status == TransferStatus.IN_PROGRESS) {
            LinearProgressIndicator(
                progress = { transfer.progressFraction },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Text(
                "${formatBytes(transfer.transferredBytes)} / ${formatBytes(transfer.fileSizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        } else {
            val statusText = when (transfer.status) {
                TransferStatus.COMPLETED -> "Done"
                TransferStatus.FAILED -> "Failed"
                TransferStatus.PENDING -> "Waiting…"
                TransferStatus.IN_PROGRESS -> ""
            }
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = when (transfer.status) {
                    TransferStatus.FAILED -> MaterialTheme.colorScheme.error
                    TransferStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "${bytes / 1_048_576}.${(bytes % 1_048_576) * 10 / 1_048_576} MB"
    bytes >= 1_024 -> "${bytes / 1_024}.${(bytes % 1_024) * 10 / 1_024} KB"
    else -> "$bytes B"
}