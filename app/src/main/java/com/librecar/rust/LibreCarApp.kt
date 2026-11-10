package com.librecar.rust

import android.app.Application
import org.maplibre.android.MapLibre

class LibreCarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
