package com.omnitex.twinlab

import android.app.Application

/**
 * Manual DI — no Hilt. Later tasks add `settings`, `api`, and ViewModel
 * factory methods to [AppContainer].
 */
class AppContainer(app: Application) {
    // filled in by Task 6 (settings) and Task 7 (api, factories)
}

class TwinLabApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
