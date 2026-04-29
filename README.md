# KMP BLE Dual-Role

A Kotlin Multiplatform application demonstrating **simultaneous BLE Central + Peripheral** operation: two devices can connect to each other regardless of which side initiated the connection.  Shared business logic runs on Android and iOS; a desktop (JVM) build provides a live packet sniffer for development.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       composeApp                            │
│  PeerListScreen · ChatScreen · FileTransferScreen · Debug   │
│                    AppViewModelProvider                     │
└──────────────┬──────────────────────────┬───────────────────┘
               │                          │
        shared/presentation         shared/domain
        PeerListViewModel           Peer · Message · Transfer
        ChatViewModel               PeerRepository
        FileTransferViewModel       MessageRepository
        DebugViewModel              TransferRepository
               │                   BleSessionController (iface)
               └──────────────┬────────────────────────────────
                              │
                       shared/ble-core
              ┌────────────────────────────────────────┐
              │  ReliableSession   FileTransferSession  │
              │  HeartbeatController  ConnectionSupervisor│
              │  BleCentral (iface)  BlePeripheral (iface)│
              │  Connection (iface)                     │
              └──────────┬──────────────────────────────┘
                         │
              ┌──────────┴──────────────────────┐
              │ androidMain          iosMain     │
              │ AndroidBleCentral   IosBleCentral│
              │ AndroidBlePeripheral IosBlePeripheral│
              │ GattConnection      CbCentralConnection│
              │ ServerGattConnection CbServerConnection│
              └─────────────────────────────────┘
                         │
                  shared/protocol
              Frame · FrameCodec · Fragmenter
              Reassembler · MessageType · BleUuids
                         │
                   shared/crypto
              BleSessionCrypto (ECDH + AES-256-GCM)
                         │
                    shared/data
              SQLDelight repositories
              PeerRepositoryImpl · MessageRepositoryImpl
              TransferRepositoryImpl · DriverFactory
```

## GATT Schema

| Characteristic | UUID suffix | Properties | Purpose |
|---|---|---|---|
| TX | `...0001` | WRITE, WRITE_NO_RESP | Client → Server data |
| RX | `...0002` | NOTIFY | Server → Client data |
| CONTROL | `...0003` | WRITE, INDICATE (encrypted) | Handshake frames |
| INFO | `...0004` | READ | Protocol version, capabilities |

## Protocol Frame Format

```
┌────────┬────────┬──────────┬──────────┬────────────┬──────────┬────────────────┬───────┐
│version │ type   │  seqNum  │ totalLen │ fragOffset │ fragLen  │    payload     │ CRC16 │
│ 1 byte │ 1 byte │  2 bytes │  2 bytes │   2 bytes  │  2 bytes │    N bytes     │2 bytes│
└────────┴────────┴──────────┴──────────┴────────────┴──────────┴────────────────┴───────┘
Total overhead: 12 bytes.  Effective payload per frame: MTU − 12 bytes.
```

## Message Types

| Type | ID | Path | Description |
|---|---|---|---|
| HELLO | 0x01 | fire-and-forget | Initiator handshake |
| HELLO_ACK | 0x02 | fire-and-forget | Responder handshake reply |
| MTU_REQUEST | 0x03 | fire-and-forget | Request MTU negotiation |
| DATA | 0x10 | **reliable (ACK/retry)** | Application message |
| DATA_ACK | 0x11 | fire-and-forget | Acknowledgement for DATA |
| FILE_OFFER | 0x20 | reliable | Announce file transfer |
| FILE_CHUNK | 0x21 | write-no-resp | File data chunk |
| FILE_COMPLETE | 0x22 | reliable | All chunks sent |
| FILE_ACK | 0x23 | reliable | Transfer accepted / confirmed |
| FILE_ABORT | 0x24 | reliable | Transfer aborted |
| FILE_PAUSE | 0x25 | fire-and-forget | Receiver requests pause |
| FILE_RESUME | 0x26 | fire-and-forget | Receiver requests resume |
| PING | 0x30 | fire-and-forget | Heartbeat probe |
| PONG | 0x31 | fire-and-forget | Heartbeat reply |
| BYE | 0xFF | fire-and-forget | Graceful disconnect |

## Handshake Sequence

```
Alice (Central)                          Bob (Peripheral)
      │  ──── connect GATT ────────────────────▶ │
      │  ◀─── services discovered ──────────────  │
      │  ──── requestMtu(517) ──────────────────▶ │
      │  ◀─── onMtuChanged(185) ────────────────  │
      │  ──── HELLO {protocolVersion} ──────────▶ │
      │  ◀─── HELLO_ACK ────────────────────────  │
      │  ──── ECDH public key (CONTROL char) ───▶ │
      │  ◀─── ECDH public key ──────────────────  │
      │  [both derive session key via HKDF-SHA256] │
      │  [DATA frames encrypted with AES-256-GCM]  │
```

## File Transfer Sequence

```
Sender                                    Receiver
  │  ──── FILE_OFFER(id, name, size) ───▶ │
  │  ◀─── FILE_ACK(id) ──────────────────  │
  │  ──── FILE_CHUNK × N (no-resp) ──────▶ │  ← bulk, no per-chunk ACK
  │  ──── FILE_CHUNK (with-resp flush) ──▶ │  ← every 16 chunks
  │  ──── FILE_COMPLETE(id, size) ────────▶ │
  │  ◀─── FILE_ACK  or  FILE_ABORT ───────  │
```

## Module Structure

| Module | Targets | Key contents |
|---|---|---|
| `shared/protocol` | JVM, Android, iOS | Frame, codec, fragmenter — pure Kotlin, no platform deps |
| `shared/ble-core` | Android, iOS | BLE abstractions, ReliableSession, FileTransferSession |
| `shared/domain` | JVM, Android, iOS | Domain models, repository interfaces, BleSessionController |
| `shared/data` | Android, iOS | SQLDelight schemas + repository implementations |
| `shared/crypto` | Android, iOS | ECDH key exchange, AES-256-GCM session encryption |
| `shared/presentation` | Android, iOS | KMP ViewModels |
| `composeApp` | Android, iOS, Desktop | Compose Multiplatform UI |

## Hard Problems and How They're Handled

**Android STATUS_133**  
`GattConnection` catches non-`GATT_SUCCESS` status in `onConnectionStateChange`, calls
`gatt.close()` immediately, and surfaces a `BleError.ConnectionFailed`.  Never reuse
a `BluetoothGatt` after disconnect.

**iOS background advertising**  
`CBPeripheralManager` moves the service UUID to the overflow area and drops the local
name when backgrounded.  Scan filter must use service UUID, not device name.

**MTU asymmetry**  
Android initiates MTU negotiation via `requestMtu()`; iOS does not expose a request
API.  `CbCentralConnection` reads `maximumWriteValueLengthForType` after service
discovery to report the effective MTU.

**Bond loss**  
An encrypted-write failure indicates the bond was deleted by the OS.
`GattConnection.handleGattError()` propagates this as `ConnectionFailed`; the upper
layer should trigger `bond()` again.

**Threading**  
Android GATT callbacks arrive on a binder thread; iOS on the CB delegate queue.
Both platforms bridge into coroutines via `Channel.trySend()` which is thread-safe.
No `freeze()` calls needed — Kotlin 2.x uses the new memory model by default.

## Testing Strategy

Most tests run in `commonTest` using `FakeConnection` — two in-memory channels wired
together.  This gives full end-to-end coverage of the protocol, reliability, and file
transfer stacks **without real hardware or an emulator**.

Real-hardware smoke tests (Android instrumented / iOS XCTest) are documented but
intentionally minimal: BLE is non-deterministic and requires physical devices.

## Performance (estimated)

| Scenario | BLE 4.2 (MTU 185) | BLE 5.0 DLE (MTU 512) |
|---|---|---|
| DATA message throughput | ~2–4 KB/s | ~8–15 KB/s |
| File transfer (write-no-resp) | ~5–12 KB/s | ~30–50 KB/s |
| Scan-to-READY latency | 2–5 s | 1–3 s |

## Known Issues

- **No partial transfer resume:** A failed file transfer restarts from byte 0.
- **File integrity:** Size check only; no CRC32/SHA-256 for the full file.
- **iOS background advertising:** Service UUID moves to overflow area; clients must
  filter by UUID not name.
- **Desktop:** The JVM entry point shows the Compose UI with stub data; real BLE on
  macOS requires CoreBluetooth wiring that is not yet implemented.

## Building

```shell
# Android debug APK
./gradlew :composeApp:assembleDebug

# Desktop run
./gradlew :composeApp:run

# Common tests (no hardware)
./gradlew :shared:protocol:jvmTest
./gradlew :shared:ble-core:jvmTest   # not yet — requires test runner setup
```

## Architecture Decision Records

- [ADR-0001](docs/adr/ADR-0001-kmp-rationale.md) — Why KMP
- [ADR-0002](docs/adr/ADR-0002-custom-framing.md) — Custom framing protocol
- [ADR-0003](docs/adr/ADR-0003-reliability-layer.md) — Reliability layer design
- [ADR-0004](docs/adr/ADR-0004-file-transfer.md) — File transfer protocol
- [ADR-0005](docs/adr/ADR-0005-threading-model.md) — Threading and coroutine dispatch
