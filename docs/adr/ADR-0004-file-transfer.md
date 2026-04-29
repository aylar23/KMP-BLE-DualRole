# ADR-0004: File Transfer Protocol

**Status:** Accepted  
**Date:** 2026-04-29

## Context

BLE's fragmented write model and typical MTU of 185–512 bytes means sending a file
requires chunking, progress tracking, and some form of error detection.  We need to
decide how to balance throughput against reliability.

## Decision

**Control plane vs data plane split:**  
`FILE_OFFER`, `FILE_ACK`, `FILE_COMPLETE`, and `FILE_ABORT` go through
`ReliableSession.send()` so they are guaranteed to be delivered.  
`FILE_CHUNK` frames bypass the reliability layer and are written via
`Connection.sendNoAck()` (write-without-response).

**Throughput optimisation:**  
Write-without-response allows the sender to pipeline up to the BLE TX buffer depth
without waiting for an ATT round-trip per frame.  Every `FLUSH_EVERY` (= 16) chunks,
one write-with-response frame is inserted to drain the pipeline and apply backpressure
if the receiver is falling behind.

**Error detection:**  
`FILE_COMPLETE` carries the total expected byte count.  The receiver compares
`received == expectedSize`; on mismatch it sends `FILE_ABORT` and the transfer must
be restarted.  A CRC32 could replace the size check but size is sufficient for a
first implementation — noted as a known limitation.

**Pause/resume:**  
The sender checks a `paused` flag before each chunk.  The receiver can signal the
sender via `FILE_PAUSE` / `FILE_RESUME` messages if it cannot keep up.

**Progress reporting:**  
`Flow<FileProgress>` is emitted on both sender and receiver sides, allowing the UI
to show a progress bar updated per-chunk without polling.

## Alternatives Considered

- **Per-chunk ACK:** Ensures every chunk is delivered but halves throughput.  The
  final-count check plus the option to re-send the whole file is a simpler trade-off
  for the expected file sizes (< 1 MB).
- **OBEX over BLE:** A standard protocol but unavailable in Android's public API for
  BLE peripherals without third-party libraries.
- **Resume from arbitrary offset:** Would require a more complex control protocol to
  negotiate the restart offset.  Deferred to a future iteration.

## Known Limitations

- No partial resume: a failed transfer must restart from byte 0.
- Size check only; no CRC32 or SHA-256 integrity verification.
- Pause signal is best-effort; in-flight chunks may already be queued in the BLE TX
  buffer when the pause arrives.

## Measured Performance (estimates)

| MTU  | BLE version | Approx throughput |
|------|-------------|-------------------|
| 23 B | 4.0 (min)   | ~1–2 KB/s         |
| 185 B| 4.2         | ~5–12 KB/s        |
| 512 B| 5.0 DLE     | ~30–50 KB/s       |

Actual numbers depend on connection interval, radio environment, and chipset.