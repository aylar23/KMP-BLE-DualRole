package com.aylar.bledualrole.presentation

import platform.Foundation.NSDate

actual fun currentTimeMs(): Long = (NSDate.date().timeIntervalSince1970 * 1000).toLong()