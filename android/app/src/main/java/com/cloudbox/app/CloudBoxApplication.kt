package com.cloudbox.app

import android.app.Application
import coil.Coil
import coil.ImageLoader
import com.cloudbox.app.data.api.ApiClient
import com.cloudbox.app.data.local.TokenManager

class CloudBoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(ApiClient.okHttpClient)
                .build()
        )
    }
}
