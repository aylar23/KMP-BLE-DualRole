package com.aylar.bledualrole.ble

/**
 * Exponential-backoff parameters for automatic reconnection.
 *
 * Delay sequence: initialDelayMs, initialDelayMs*multiplier, …, capped at maxDelayMs.
 * [maxAttempts] = Int.MAX_VALUE means retry indefinitely.
 */
data class ReconnectPolicy(
    val initialDelayMs: Long = 1_000L,
    val maxDelayMs: Long = 30_000L,
    val multiplier: Double = 2.0,
    val maxAttempts: Int = Int.MAX_VALUE,
)
