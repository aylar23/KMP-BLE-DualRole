package com.aylar.bledualrole.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aylar.bledualrole.domain.session.PacketLogEntry

/**
 * Desktop-only sniffer view: shows live GATT packet log across all active connections.
 * Useful during development — surface all frame bytes in a scrollable log.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnifferScreen(
    logFlow: kotlinx.coroutines.flow.Flow<PacketLogEntry>,
) {
    val log = remember { mutableStateListOf<PacketLogEntry>() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        logFlow.collect { entry ->
            log.add(entry)
            if (log.size > 1_000) log.removeFirst()
        }
    }

    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE Sniffer — ${log.size} packets") },
                actions = {
                    Button(
                        onClick = { log.clear() },
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Clear") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items(log) { entry ->
                SnifferRow(entry)
            }
        }
    }
}

@Composable
private fun SnifferRow(entry: PacketLogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            entry.direction,
            style = MaterialTheme.typography.labelSmall,
            color = if (entry.direction == "TX") MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            entry.peerId.takeLast(8),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${entry.bytes}B",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            "@${entry.timestampMs % 100_000}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}