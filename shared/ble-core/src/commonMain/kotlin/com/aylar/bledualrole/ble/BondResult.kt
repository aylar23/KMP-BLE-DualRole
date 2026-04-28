package com.aylar.bledualrole.ble

sealed interface BondResult {
    data object Bonded : BondResult
    data object AlreadyBonded : BondResult
    data class Failed(val reason: String) : BondResult
}