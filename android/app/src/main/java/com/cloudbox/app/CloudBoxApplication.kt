package com.cloudbox.app

import android.app.Application
import com.cloudbox.app.data.local.TokenManager

class CloudBoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)
    }
}
