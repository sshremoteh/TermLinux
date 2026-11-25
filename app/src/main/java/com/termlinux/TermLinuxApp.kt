package com.termlinux

import android.app.Application
import com.termlinux.core.AppPaths
import com.termlinux.distro.DistroManager
import com.termlinux.session.SessionManager
import com.termlinux.settings.SettingsRepository
import com.termlinux.storage.StorageBridge

class AppContainer(application: Application) {
    val paths = AppPaths(application)
    val settingsRepository = SettingsRepository(application)
    val distroManager = DistroManager(application, paths)
    val storageBridge = StorageBridge(application, paths)
    val sessionManager = SessionManager(application, paths, distroManager, storageBridge)
}

class TermLinuxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
