# ADR-0001: Use Kotlin Multiplatform for BLE Dual-Role App

## Status
Accepted

## Context

This project implements a device-to-device BLE mesh app that must run on Android and iOS. The core
concern is where to draw the line between shared and platform-specific code, given that BLE APIs
differ significantly between the two platforms.

Key options considered:

| Approach | Shared Logic | Platform BLE | Notes |
|---|---|---|---|
| Native Android + Native iOS | None | Full native | Maximum duplication |
| React Native / Flutter | UI + business logic | Via plugins | JS bridge overhead, limited BLE control |
| **KMP** | Business logic, protocol, state machines | Platform-specific adapters | Best of both |
| KMP + Compose Multiplatform (full) | Everything including UI | Platform adapters | iOS feels non-native |

## Decision

Use **Kotlin Multiplatform** with:
- Shared `commonMain`: protocol framing, state machines, domain models, use cases, ViewModels
- Platform-specific `androidMain` / `iosMain`: BLE stack adapters only
- **Compose Multiplatform** for Android and Desktop UI
- **SwiftUI** for iOS UI — keeps iOS native feel and avoids the Compose/UIKit bridge overhead

BLE **cannot** be meaningfully shared at the OS API level:
- Android uses `BluetoothGatt` / `BluetoothGattServer` with callback-based APIs
- iOS uses `CBCentralManager` / `CBPeripheralManager` with delegate callbacks
- Threading models differ (binder thread vs. GCD queue)

What makes KMP the right call here is that the *interesting* code — protocol framing, fragmentation,
reassembly, state machines, encryption, ACK/retry logic — is all pure Kotlin and lives in
`commonMain`. The BLE adapters are thin wrappers that bridge platform callbacks into coroutine
`Flow`s consumed by shared code.

## Consequences

- Shared code is tested on JVM without real BLE hardware, using `FakeBleCentral`/`FakeBlePeripheral`
  in-memory fakes — fast, deterministic, CI-friendly.
- iOS UI is SwiftUI, so KMP-NativeCoroutines or a manual `@Observable` bridge is needed to consume
  shared `StateFlow`s.
- Desktop (JVM) target added for a development sniffer/debugger view.
- Two separate implementations of the BLE adapter must be kept in sync via the shared `Connection`
  interface — this is the ongoing maintenance cost.
