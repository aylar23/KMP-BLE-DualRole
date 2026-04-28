package com.aylar.bledualrole

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform