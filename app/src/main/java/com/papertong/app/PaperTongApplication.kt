package com.papertong.app

import android.app.Application
import com.papertong.app.di.AppContainer

class PaperTongApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
