package com.librecar.rust

import android.app.Application
import com.librecar.rust.data.CarCache
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class LibreCarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CarCache.init(this)
        MapLibre.getInstance(this)
        HttpRequestUtil.setLogEnabled(false)
        HttpRequestUtil.setOkHttpClient(CarCache.tileHttpClient)
    }
}
