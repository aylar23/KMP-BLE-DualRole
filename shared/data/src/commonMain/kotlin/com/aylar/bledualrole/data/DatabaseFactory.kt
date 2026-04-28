package com.aylar.bledualrole.data

import com.aylar.bledualrole.data.db.BleDatabase

object DatabaseFactory {
    fun create(driverFactory: DriverFactory): BleDatabase =
        BleDatabase(driverFactory.createDriver())
}