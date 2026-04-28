package com.aylar.bledualrole.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.aylar.bledualrole.data.db.BleDatabase

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(BleDatabase.Schema, context, "ble.db")
}