# ADR-0003: Reliability Layer Design

**Status:** Accepted  
**Date:** 2026-04-29

## Context

BLE GATT notifications are fire-and-forget at the ATT layer; there is no built-in
acknowledgement, retransmission, or flow control for notification-delivered data.
An application sending large messages or files over BLE can silently lose packets
if the peer's input buffer is full or if a transient radio error occurs.

## Decision

Implement a reliability layer inside `ReliableSession` that sits above `Connection`
and below the application.

**Selective reliability:** Only `DATA` messages go through the ACK/retry/window path.
Control messages (PING, PONG, FILE_*, HELLO*) are sent fire-and-forget — the
overhead of tracking ACKs for tiny control frames is not worth the cost.

**ACK/retry:** The sender waits up to `ackTimeoutMs` for a `DATA_ACK` containing
the matching sequence number.  On timeout it retransmits all fragments for that
message, up to `maxRetries` times.

**Sliding window:** A `kotlinx.coroutines.sync.Semaphore` with `windowSize` permits
limits the number of unacknowledged in-flight DATA messages.  Callers that try to
send beyond the window are suspended automatically (natural backpressure — no
explicit rate-limiting API needed).

**Deduplication:** The last 128 received sequence numbers are stored in a ring
buffer.  If a duplicate DATA frame arrives (because our ACK was lost and the sender
retransmitted), the payload is silently dropped but the ACK is re-sent, breaking
the retry loop on the sender side.

**Heartbeat:** `HeartbeatController` sends `PING` every 5 s and marks the session
dead if no `PONG` is received within 15 s.  This catches zombie connections where
the OS still believes the link is up but the remote device has rebooted.

**Auto-reconnect:** `BleCentral.supervisedConnect()` returns a `Flow<Connection?>`
that re-establishes the connection after each drop with exponential backoff.

## Alternatives Considered

- **L2CAP CoC (Credit-Based Flow Control):** Available on BLE 4.1+ but not exposed
  by Android's public BluetoothSocket API for BLE; would require NDK.  Rejected.
- **GATT Write With Response for every frame:** Simple but throughput is limited by
  one round-trip per ATT PDU (~40–100 ms per frame in the worst case).  We use this
  only for DATA messages where reliability matters; FILE_CHUNK uses write-without-
  response for bulk throughput.
- **Full TCP-over-BLE (sequence numbers per byte):** Overkill for message-oriented
  payloads.  Our message-level sequencing is sufficient.

## Consequences

- Effective DATA throughput is reduced compared to raw write-without-response;
  measured roughly 2–4 KB/s with window=8, MTU=185 on BLE 4.2.
- Applications that need maximum throughput (file transfer) bypass the reliability
  layer for `FILE_CHUNK` frames and use their own final-ACK approach.
- The reliability layer adds ~50 lines of state per session; it is fully testable
  without real hardware via `FakeConnection`.
