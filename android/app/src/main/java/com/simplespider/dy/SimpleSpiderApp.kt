package com.simplespider.dy

import android.app.Application
import com.simplespider.dy.data.ApiClient

class SimpleSpiderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
    }
}
