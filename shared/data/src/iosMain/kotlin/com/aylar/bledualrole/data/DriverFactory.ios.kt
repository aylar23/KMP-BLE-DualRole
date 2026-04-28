package com.aylar.bledualrole.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.aylar.bledualrole.data.db.BleDatabase

actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(BleDatabase.Schema, "ble.db")
}