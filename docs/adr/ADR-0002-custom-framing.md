# ADR-0002: Custom Framing Protocol on Top of GATT

## Status
Accepted

## Context

BLE GATT provides a transport: you can write bytes to a characteristic and receive notifications.
It does not provide:
- Message framing (how does the receiver know where a message ends?)
- Fragmentation (GATT write/notify size is limited by ATT MTU, typically 20–517 bytes)
- Sequencing (out-of-order delivery is possible over BLE)
- Reliability (GATT notifications are fire-and-forget; no ACK at the GATT layer)
- Message types (how does the receiver know if this is a chat message vs. a file chunk?)

Options considered:

| Option | Pros | Cons |
|---|---|---|
| Raw GATT writes, app handles parsing ad hoc | Simple to start | Falls apart with large messages, no framing |
| Use Nordic UART Service (NUS) pattern | Well-known, tooling support | Still needs framing on top; no control channel |
| **Custom framing + control characteristic** | Full control, extensible | More code to write and test |

## Decision

Define a custom binary framing protocol over GATT with this frame layout:

```
[1B version][1B type][2B seqNum][2B totalLen][2B fragOffset][2B fragLen][N bytes payload][2B CRC16]
```

- **version**: protocol version for future compatibility
- **type**: message type enum (`HELLO`, `HELLO_ACK`, `MTU_REQUEST`, `DATA`, `DATA_ACK`,
  `FILE_OFFER`, `FILE_CHUNK`, `FILE_COMPLETE`, `BYE`)
- **seqNum**: 16-bit wrapping sequence number for ordering and deduplication
- **totalLen**: total message length before fragmentation
- **fragOffset** / **fragLen**: position and size of this fragment within the full message
- **CRC16**: detects corruption on the BLE link

GATT service uses four characteristics:
- `tx` (write / write-without-response): central → peripheral data
- `rx` (notify): peripheral → central data
- `control` (write + indicate): handshake, MTU negotiation, ACKs — encrypted
- `info` (read): device name, protocol version, capabilities

## Consequences

- **All protocol code lives in `shared/protocol` (pure Kotlin `commonMain`)** — fully unit-testable
  on JVM, no BLE hardware needed.
- Fragmentation/reassembly tested with property-based tests (roundtrip invariants).
- The framing adds 12 bytes of overhead per frame. At MTU=23 (minimum), effective payload is 11
  bytes — poor but functional. At MTU=517 (Android max request), effective payload is 505 bytes.
- CRC16 on every frame is cheap on modern hardware but could be removed under OS-level encryption
  if latency becomes a concern (out of scope for now).
- The custom protocol makes this incompatible with off-the-shelf BLE tools that expect NUS — that
  is an acceptable tradeoff given we fully own both ends.
