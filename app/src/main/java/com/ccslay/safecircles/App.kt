package com.ccslay.safecircles

import android.app.Application
import java.io.File

// App.kt
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val cfg = org.osmdroid.config.Configuration.getInstance()
        cfg.userAgentValue = "SafeZonePH/1.0 (contact@example.com)" // set your UA
        cfg.osmdroidBasePath = File(filesDir, "osmdroid")
        cfg.osmdroidTileCache = File(cfg.osmdroidBasePath, "tiles")
    }
}
