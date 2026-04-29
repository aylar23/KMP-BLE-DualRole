package com.aylar.bledualrole

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    App(IosStubViewModelProvider())
}