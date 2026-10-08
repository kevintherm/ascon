package com.ascon.app

import android.app.Application

class AsconApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
