package com.librecar.rust.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class LocalAudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val contentUri: Uri,
)

data class LocalPlayerState(
    val tracks: List<LocalAudioTrack> = emptyList(),
    val currentIndex: Int = -1,
    val currentTitle: String = "",
    val currentArtist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val isRemoteStream: Boolean = false,
)

class LocalAudioPlayer(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false

    private val _state = MutableStateFlow(LocalPlayerState())
    val state: StateFlow<LocalPlayerState> = _state.asStateFlow()

    fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scanDeviceTracks(): List<LocalAudioTrack> = withContext(Dispatchers.IO) {
        if (!hasAudioPermission()) return@withContext emptyList()
        val list = mutableListOf<LocalAudioTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 5000"
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext() && list.size < 400) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Track"
                    val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Local Artist"
                    val dur = cursor.getLong(durCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    list.add(LocalAudioTrack(id, title, artist, dur, uri))
                }
            }
        }
        _state.value = _state.value.copy(tracks = list)
        list
    }

    private fun requestFocus(): Boolean {
        val existing = focusRequest
        if (existing != null) {
            return audioManager.requestAudioFocus(existing) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        runCatching { mediaPlayer?.setVolume(0.25f, 0.25f) }
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        if (mediaPlayer?.isPlaying == true) {
                            resumeOnFocusGain = true
                            pauseInternal()
                        }
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        runCatching { mediaPlayer?.setVolume(1.0f, 1.0f) }
                        if (resumeOnFocusGain) {
                            resumeOnFocusGain = false
                            resume()
                        }
                    }
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        resumeOnFocusGain = false
                        pauseInternal()
                    }
                }
            }
            .build()
        focusRequest = req
        return audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun playLocalIndex(index: Int) {
        val list = _state.value.tracks
        if (index !in list.indices) return
        val track = list[index]
        startUri(
            uri = track.contentUri,
            isHttp = false,
            title = track.title,
            artist = track.artist,
            durationMs = track.durationMs,
            localIdx = index,
        )
    }

    fun playPreviewUrl(url: String, title: String, artist: String) {
        startUri(
            uri = Uri.parse(url),
            isHttp = true,
            title = title,
            artist = "$artist · Preview",
            durationMs = 30_000L,
            localIdx = _state.value.currentIndex,
        )
    }

    fun playByQuery(query: String): Boolean {
        val list = _state.value.tracks
        if (list.isEmpty()) return false
        if (query.isBlank()) {
            val idx = if (_state.value.currentIndex in list.indices) _state.value.currentIndex else 0
            playLocalIndex(idx)
            return true
        }
        val q = query.lowercase()
        val matchIdx = list.indexOfFirst {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
        return if (matchIdx >= 0) {
            playLocalIndex(matchIdx)
            true
        } else {
            false
        }
    }

    private fun startUri(
        uri: Uri,
        isHttp: Boolean,
        title: String,
        artist: String,
        durationMs: Long,
        localIdx: Int,
    ) {
        requestFocus()
        resumeOnFocusGain = false
        releasePlayer()
        runCatching {
            val mp = MediaPlayer().apply {
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                if (isHttp) {
                    setDataSource(uri.toString())
                } else {
                    setDataSource(context, uri)
                }
                setOnPreparedListener { player ->
                    player.setVolume(1.0f, 1.0f)
                    player.start()
                    _state.value = _state.value.copy(
                        isPlaying = true,
                        durationMs = player.duration.toLong().coerceAtLeast(durationMs),
                        positionMs = 0L,
                    )
                }
                setOnCompletionListener {
                    if (!isHttp) next() else _state.value = _state.value.copy(isPlaying = false, positionMs = 0L)
                }
                setOnErrorListener { _, _, _ ->
                    _state.value = _state.value.copy(isPlaying = false)
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
            _state.value = _state.value.copy(
                currentIndex = localIdx,
                currentTitle = title,
                currentArtist = artist,
                isPlaying = true,
                durationMs = durationMs,
                positionMs = 0L,
                isRemoteStream = isHttp,
            )
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer
        if (mp == null) {
            if (_state.value.tracks.isNotEmpty()) playLocalIndex(0)
            return
        }
        if (mp.isPlaying) {
            resumeOnFocusGain = false
            pauseInternal()
        } else {
            requestFocus()
            mp.setVolume(1.0f, 1.0f)
            mp.start()
            _state.value = _state.value.copy(isPlaying = true)
        }
    }

    fun pause() {
        resumeOnFocusGain = false
        pauseInternal()
    }

    private fun pauseInternal() {
        runCatching {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    fun resume() {
        runCatching {
            if (mediaPlayer != null && mediaPlayer?.isPlaying == false) {
                requestFocus()
                mediaPlayer?.setVolume(1.0f, 1.0f)
                mediaPlayer?.start()
                _state.value = _state.value.copy(isPlaying = true)
            } else if (mediaPlayer == null && _state.value.tracks.isNotEmpty()) {
                playLocalIndex(0)
            }
        }
    }

    fun next() {
        val list = _state.value.tracks
        if (list.isEmpty()) return
        val nextIdx = if (_state.value.shuffle && list.size > 1) {
            list.indices.random()
        } else {
            (_state.value.currentIndex + 1) % list.size
        }
        playLocalIndex(nextIdx)
    }

    fun prev() {
        val list = _state.value.tracks
        if (list.isEmpty()) return
        val cur = _state.value.currentIndex
        val prevIdx = if (cur <= 0) list.lastIndex else cur - 1
        playLocalIndex(prevIdx)
    }

    fun seekTo(ms: Long) {
        runCatching {
            mediaPlayer?.seekTo(ms.toInt().coerceAtLeast(0))
            _state.value = _state.value.copy(positionMs = ms)
        }
    }

    fun toggleShuffle() {
        _state.value = _state.value.copy(shuffle = !_state.value.shuffle)
    }

    fun pollProgress() {
        val mp = mediaPlayer ?: return
        runCatching {
            if (mp.isPlaying) {
                val pos = mp.currentPosition.toLong()
                val dur = mp.duration.toLong().coerceAtLeast(_state.value.durationMs)
                if (pos != _state.value.positionMs || dur != _state.value.durationMs) {
                    _state.value = _state.value.copy(positionMs = pos, durationMs = dur)
                }
            }
        }
    }

    fun releasePlayer() {
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }

    fun destroy() {
        releasePlayer()
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }
}
