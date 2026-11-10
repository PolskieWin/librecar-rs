package com.librecar.rust.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

data class SpotifyTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val previewUrl: String?,
    val durationMs: Long,
)

data class SpotifyPlaybackState(
    val isPlaying: Boolean = false,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val track: SpotifyTrack? = null,
    val deviceName: String = "",
    val hasActiveDevice: Boolean = false,
)

class SpotifyClient(private val prefs: CarPreferences) {
    companion object {
        const val REDIRECT_URI = "librecar://spotify-callback"
        private const val SCOPES =
            "user-read-playback-state user-modify-playback-state user-read-currently-playing playlist-read-private user-library-read"
    }

    // Small 4MB image LRU cache so album art never OOMs 1-2GB RAM devices
    private val artCache = object : LruCache<String, Bitmap>(8) {}

    fun isConfigured(): Boolean = prefs.spotifyClientId.isNotBlank()
    fun isConnected(): Boolean = prefs.spotifyAccessToken.isNotBlank()

    fun buildAuthorizeUrl(): String? {
        val clientId = prefs.spotifyClientId.trim()
        if (clientId.isEmpty()) return null

        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        val verifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        prefs.spotifyPkceVerifier = verifier

        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        return Uri.Builder()
            .scheme("https")
            .authority("accounts.spotify.com")
            .path("authorize")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("scope", SCOPES)
            .build()
            .toString()
    }

    suspend fun exchangeCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val clientId = prefs.spotifyClientId
        val verifier = prefs.spotifyPkceVerifier
        if (clientId.isBlank() || verifier.isBlank()) return@withContext false

        val body = "client_id=${enc(clientId)}&grant_type=authorization_code" +
            "&code=${enc(code)}&redirect_uri=${enc(REDIRECT_URI)}&code_verifier=${enc(verifier)}"
        postToken(body)
    }

    private fun refreshTokenIfNeeded(): Boolean {
        if (prefs.spotifyAccessToken.isBlank()) return false
        if (System.currentTimeMillis() < prefs.spotifyExpiresAtMs - 30_000L) return true
        val refresh = prefs.spotifyRefreshToken
        val clientId = prefs.spotifyClientId
        if (refresh.isBlank() || clientId.isBlank()) return false
        val body = "grant_type=refresh_token&refresh_token=${enc(refresh)}&client_id=${enc(clientId)}"
        return postToken(body)
    }

    private fun postToken(formBody: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            OutputStreamWriter(conn.outputStream).use { it.write(formBody) }
            if (conn.responseCode !in 200..299) return false
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(raw)
            prefs.spotifyAccessToken = json.getString("access_token")
            if (json.has("refresh_token")) {
                prefs.spotifyRefreshToken = json.getString("refresh_token")
            }
            val expiresSec = json.optLong("expires_in", 3600L)
            prefs.spotifyExpiresAtMs = System.currentTimeMillis() + expiresSec * 1000L
            true
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun apiCall(endpoint: String, method: String = "GET", jsonBody: String? = null): Pair<Int, String?> {
        if (!refreshTokenIfNeeded()) return 401 to null
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("https://api.spotify.com/v1$endpoint").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Authorization", "Bearer ${prefs.spotifyAccessToken}")
                if (jsonBody != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            if (jsonBody != null) {
                OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }
            }
            val code = conn.responseCode
            val text = if (code in 200..299 && code != 204) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
            code to text
        } catch (_: Exception) {
            -1 to null
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun fetchPlaybackState(): SpotifyPlaybackState? = withContext(Dispatchers.IO) {
        val (code, raw) = apiCall("/me/player")
        if (code == 204) return@withContext SpotifyPlaybackState(hasActiveDevice = false)
        if (raw.isNullOrBlank()) return@withContext null
        runCatching {
            val obj = JSONObject(raw)
            val item = obj.optJSONObject("item")
            val track = item?.let { parseTrack(it) }
            val device = obj.optJSONObject("device")?.optString("name").orEmpty()
            SpotifyPlaybackState(
                isPlaying = obj.optBoolean("is_playing", false),
                progressMs = obj.optLong("progress_ms", 0L),
                durationMs = track?.durationMs ?: 0L,
                track = track,
                deviceName = device,
                hasActiveDevice = true,
            )
        }.getOrNull()
    }

    suspend fun searchTracks(query: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val (_, raw) = apiCall("/search?q=${enc(query)}&type=track&limit=12")
        if (raw.isNullOrBlank()) return@withContext emptyList()
        runCatching {
            val items = JSONObject(raw).getJSONObject("tracks").getJSONArray("items")
            buildList {
                for (i in 0 until items.length()) {
                    add(parseTrack(items.getJSONObject(i)))
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fetchSavedOrTopTracks(): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val (_, raw) = apiCall("/me/tracks?limit=15")
        if (raw.isNullOrBlank()) return@withContext emptyList()
        runCatching {
            val items = JSONObject(raw).getJSONArray("items")
            buildList {
                for (i in 0 until items.length()) {
                    add(parseTrack(items.getJSONObject(i).getJSONObject("track")))
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun playUri(uri: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put("uris", org.json.JSONArray().put(uri)).toString()
        val (code, _) = apiCall("/me/player/play", method = "PUT", jsonBody = body)
        code in 200..299
    }

    suspend fun setPlaying(play: Boolean): Boolean = withContext(Dispatchers.IO) {
        val (code, _) = apiCall(if (play) "/me/player/play" else "/me/player/pause", method = "PUT", jsonBody = if (play) "{}" else null)
        code in 200..299
    }

    suspend fun skipNext(): Boolean = withContext(Dispatchers.IO) {
        apiCall("/me/player/next", method = "POST", jsonBody = "").first in 200..299
    }

    suspend fun skipPrev(): Boolean = withContext(Dispatchers.IO) {
        apiCall("/me/player/previous", method = "POST", jsonBody = "").first in 200..299
    }

    suspend fun seekTo(ms: Long): Boolean = withContext(Dispatchers.IO) {
        apiCall("/me/player/seek?position_ms=$ms", method = "PUT", jsonBody = "").first in 200..299
    }

    suspend fun loadArtwork(url: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        artCache.get(url)?.let { return@withContext it }
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
            }
            val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
            conn.disconnect()
            if (bmp != null) artCache.put(url, bmp)
            bmp
        }.getOrNull()
    }

    fun disconnect() {
        prefs.spotifyAccessToken = ""
        prefs.spotifyRefreshToken = ""
        prefs.spotifyExpiresAtMs = 0L
    }

    private fun parseTrack(obj: JSONObject): SpotifyTrack {
        val artists = obj.optJSONArray("artists")
        val artistName = buildList {
            if (artists != null) {
                for (i in 0 until artists.length()) add(artists.getJSONObject(i).optString("name"))
            }
        }.joinToString(", ")
        val albumObj = obj.optJSONObject("album")
        val images = albumObj?.optJSONArray("images")
        val artUrl = if (images != null && images.length() > 0) {
            images.getJSONObject(minOf(1, images.length() - 1)).optString("url")
        } else null
        return SpotifyTrack(
            id = obj.optString("id"),
            uri = obj.optString("uri"),
            title = obj.optString("name"),
            artist = artistName.ifBlank { "Unknown" },
            album = albumObj?.optString("name").orEmpty(),
            artworkUrl = artUrl,
            previewUrl = obj.optString("preview_url").takeIf { it.isNotBlank() && it != "null" },
            durationMs = obj.optLong("duration_ms", 0L),
        )
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
}
