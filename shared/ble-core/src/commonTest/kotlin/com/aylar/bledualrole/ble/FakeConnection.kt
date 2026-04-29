package com.aylar.bledualrole.ble

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * In-memory [Connection] pair for unit tests.
 *
 * [FakeConnection.alice] and [FakeConnection.bob] are wired together: bytes written by Alice
 * appear in Bob's [incoming] and vice-versa.  No real Bluetooth hardware required.
 *
 * Usage:
 * ```
 * val (alice, bob) = FakeConnection.pair()
 * val session = ReliableSession(alice, scope = testScope)
 * ```
 */
class FakeConnection private constructor(
    override val peerId: PeerId,
    private val outgoing: Channel<ByteArray>,
    private val incomingChannel: Channel<ByteArray>,
) : Connection {

    override val state: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.READY)
    override val mtu: StateFlow<Int> = MutableStateFlow(DEFAULT_MTU)
    override val incoming: Flow<ByteArray> = incomingChannel.receiveAsFlow()

    val sentBytes = mutableListOf<ByteArray>()
    val sentNoAckBytes = mutableListOf<ByteArray>()

    override suspend fun send(bytes: ByteArray) {
        sentBytes.add(bytes)
        outgoing.send(bytes)
    }

    override suspend fun sendNoAck(bytes: ByteArray) {
        sentNoAckBytes.add(bytes)
        outgoing.send(bytes)
    }

    override suspend fun requestMtu(size: Int): Int = mtu.value

    override suspend fun bond(): BondResult = BondResult.AlreadyBonded

    override suspend fun close() {
        (state as MutableStateFlow).value = ConnectionState.DISCONNECTED
        incomingChannel.close()
    }

    fun simulateIncoming(bytes: ByteArray) {
        incomingChannel.trySend(bytes)
    }

    companion object {
        private const val DEFAULT_MTU = 512

        fun pair(): Pair<FakeConnection, FakeConnection> {
            val aToB = Channel<ByteArray>(Channel.BUFFERED)
            val bToA = Channel<ByteArray>(Channel.BUFFERED)
            val alice = FakeConnection(PeerId("alice"), outgoing = aToB, incomingChannel = bToA)
            val bob = FakeConnection(PeerId("bob"), outgoing = bToA, incomingChannel = aToB)
            return alice to bob
        }
    }
}

/**
 * Fake [BleCentral] that returns pre-wired [FakeConnection] instances without any BLE hardware.
 */
class FakeBleCentral(private vararg val connections: FakeConnection) : BleCentral {
    private var connectIndex = 0
    val scanned = mutableListOf<ScanFilter>()

    override fun scan(filter: ScanFilter) = kotlinx.coroutines.flow.flow<DiscoveredPeer> {
        scanned += filter
    }

    override suspend fun stopScan() {}

    override suspend fun connect(peer: DiscoveredPeer): Connection =
        connections[connectIndex++ % connections.size]
}

/**
 * Fake [BlePeripheral] that can emit pre-built connections as if a remote central connected.
 */
class FakeBlePeripheral : BlePeripheral {
    private val connectionsChannel = Channel<Connection>(Channel.BUFFERED)

    override suspend fun startAdvertising(config: AdvertiseConfig) {}

    override fun incomingConnections(): Flow<Connection> = connectionsChannel.receiveAsFlow()

    override suspend fun stopAdvertising() {}

    fun simulateIncomingConnection(conn: Connection) {
        connectionsChannel.trySend(conn)
    }
}