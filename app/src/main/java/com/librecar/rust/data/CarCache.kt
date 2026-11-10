package com.librecar.rust.data

import android.content.Context
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit

/**
 * Two-tier memory + disk TTL cache paired with a shared connection-pooled OkHttpClient.
 * Keeps network latency low and avoids duplicate API requests on low-end devices.
 */
object CarCache {
    private const val MAX_MEM_ENTRIES = 64
    private const val MAX_DISK_ENTRIES = 80
    private val memCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Pair<Long, String>>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, String>>?): Boolean =
                size > MAX_MEM_ENTRIES
        }
    )
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var cacheDir: File? = null
    @Volatile
    private var clientInstance: OkHttpClient? = null
    @Volatile
    private var tileClientInstance: OkHttpClient? = null
    private val initLock = Any()

    /** Fast zero-disk-I/O HTTP/2 pooled client dedicated to MapLibre raster/DEM tiles (MapLibre caches tiles in SQLite natively). */
    val tileHttpClient: OkHttpClient
        get() = tileClientInstance ?: synchronized(initLock) {
            tileClientInstance ?: OkHttpClient.Builder()
                .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
                .dispatcher(Dispatcher().apply {
                    maxRequests = 48
                    maxRequestsPerHost = 16
                })
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(7, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
                .also { tileClientInstance = it }
        }

    fun init(context: Context) {
        // Store appContext immediately with zero blocking disk I/O on cold-start main thread
        appContext = context.applicationContext
    }

    private fun ensureDiskDir(): File? {
        cacheDir?.let { return it }
        val ctx = appContext ?: return null
        return synchronized(initLock) {
            cacheDir ?: File(ctx.cacheDir, "librecar_api_cache").apply { mkdirs() }.also { cacheDir = it }
        }
    }

    val httpClient: OkHttpClient
        get() = clientInstance ?: synchronized(initLock) {
            clientInstance ?: run {
                val builder = OkHttpClient.Builder()
                    .connectTimeout(7, TimeUnit.SECONDS)
                    .readTimeout(9, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                appContext?.let { ctx ->
                    val httpDir = File(ctx.cacheDir, "librecar_http_cache").apply { mkdirs() }
                    builder.cache(Cache(httpDir, 12L * 1024L * 1024L))
                }
                builder.build().also { clientInstance = it }
            }
        }

    fun get(key: String, maxAgeMs: Long, allowStaleFallback: Boolean = false): String? {
        val now = System.currentTimeMillis()
        memCache.get(key)?.let { (ts, payload) ->
            if (now - ts <= maxAgeMs || allowStaleFallback) return payload
        }
        val dir = ensureDiskDir() ?: return null
        val file = File(dir, hashKey(key))
        if (!file.exists()) return null
        return runCatching {
            val raw = file.readText()
            val sep = raw.indexOf('\n')
            if (sep <= 0) return null
            val ts = raw.substring(0, sep).toLongOrNull() ?: return null
            val payload = raw.substring(sep + 1)
            if (now - ts <= maxAgeMs || allowStaleFallback) {
                memCache.put(key, ts to payload)
                payload
            } else {
                null
            }
        }.getOrNull()
    }

    fun put(key: String, payload: String) {
        val now = System.currentTimeMillis()
        memCache.put(key, now to payload)
        val dir = ensureDiskDir() ?: return
        runCatching {
            val file = File(dir, hashKey(key))
            file.writeText("$now\n$payload")
            pruneIfNeeded(dir)
        }
    }

    fun fetchCachedGet(
        url: String,
        cacheKey: String,
        ttlMs: Long,
        userAgent: String,
        allowStaleOnOffline: Boolean = true,
    ): String? {
        get(cacheKey, ttlMs, allowStaleFallback = false)?.let { return it }
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()
        val live = runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
        if (!live.isNullOrBlank()) {
            put(cacheKey, live)
            return live
        }
        return if (allowStaleOnOffline) get(cacheKey, ttlMs, allowStaleFallback = true) else null
    }

    private fun pruneIfNeeded(dir: File) {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_DISK_ENTRIES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_DISK_ENTRIES)
            .forEach { it.delete() }
    }

    private fun hashKey(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) } + ".json"
    }
}
