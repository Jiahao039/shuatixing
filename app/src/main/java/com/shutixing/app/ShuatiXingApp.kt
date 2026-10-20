package com.shutixing.app

import android.app.Application
import com.shutixing.app.di.AppContainer

class ShuatiXingApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
