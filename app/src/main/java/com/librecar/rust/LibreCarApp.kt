package com.librecar.rust

import android.app.Application
import com.librecar.rust.data.CarCache
import org.maplibre.android.MapLibre

class LibreCarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CarCache.init(this)
        MapLibre.getInstance(this)
    }
}
