# ADR-0005: Threading and Coroutine Dispatch Model

**Status:** Accepted  
**Date:** 2026-04-29

## Context

BLE callbacks arrive on different threads depending on the platform:

- **Android:** `BluetoothGattCallback` runs on a binder thread pool; the thread is
  not the main thread and is not a coroutine dispatcher.
- **iOS:** `CBCentralManagerDelegate` / `CBPeripheralDelegate` run on the dispatch
  queue you pass to the manager (we use the main queue by default, but a dedicated
  background queue is preferred in production).

ViewModels need to update UI state on the main thread; BLE I/O should stay off the
main thread.

## Decision

**Platform layer (BLE callbacks):**  
Both `GattConnection` and `CbCentralConnection` bridge the callback world into
coroutines using `Channel<ByteArray>` and `kotlinx.coroutines.channels.trySend`.
`trySend` is thread-safe and non-suspending, safe to call from any callback thread.

**Business logic (ReliableSession, FileTransferSession):**  
These run inside the `CoroutineScope` passed by the caller.  For Android this is
typically `CoroutineScope(Dispatchers.IO + SupervisorJob())` to stay off the main
thread.  For iOS the default dispatcher is backed by a dedicated `NSOperationQueue`.

**ViewModels:**  
Launched in `viewModelScope` which uses `Dispatchers.Main.immediate` on both
Android and iOS (via the `androidx.lifecycle` KMP artifact).  Flows are collected
using `stateIn(WhileSubscribed(5_000))` so they stop when there are no observers.

**iOS memory model:**  
As of Kotlin 1.9 / 2.x the new memory model is the default.  Objects no longer
need to be frozen before crossing thread boundaries.  We do NOT call `freeze()`
anywhere.  Coroutines running on a background dispatcher can freely reference shared
mutable state as long as access is serialised (which our Mutex/Channel usage ensures).

## Alternatives Considered

- **Single-threaded Kotlin coroutines (runBlocking / Dispatchers.Unconfined):**
  Simpler for tests but not safe for production BLE on Android where the binder
  thread is separate.
- **Reactive streams (RxKotlin / RxSwift):** Would work but introduces a large
  dependency and a different mental model than coroutines.  KMP coroutines are the
  idiomatic choice.

## Consequences

- The `scope` parameter of `ReliableSession`, `HeartbeatController`, and
  `FileTransferSession` must be cancelled when the connection closes to avoid
  coroutine leaks.  Callers are responsible for this; typically tied to the
  `ViewModel.onCleared()` via `viewModelScope` or a custom `SupervisorJob`.
- Tests use `UnconfinedTestDispatcher` to execute coroutines eagerly and avoid
  timing-dependent flakiness.