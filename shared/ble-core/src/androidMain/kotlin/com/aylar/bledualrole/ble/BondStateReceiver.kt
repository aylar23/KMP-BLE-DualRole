package com.aylar.bledualrole.ble

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class BondStateChange(
    val peerId: PeerId,
    val previousState: Int,
    val newState: Int,
)

fun bondStateChanges(context: Context): Flow<BondStateChange> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                ?: return
            val prev = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
            val curr = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
            trySend(BondStateChange(PeerId(device.address), prev, curr))
        }
    }
    context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    awaitClose { context.unregisterReceiver(receiver) }
}