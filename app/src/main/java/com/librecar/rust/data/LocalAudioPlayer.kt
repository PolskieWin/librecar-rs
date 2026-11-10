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
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.librecar.rust.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Immutable
data class LocalAudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val contentUri: Uri,
    val isLoopBuiltIn: Boolean = false,
)

@Immutable
data class RadioStation(
    val id: String,
    val name: String,
    val genre: String,
    val streamUrl: String,
    val country: String,
)

val BUILT_IN_RADIO_STATIONS = listOf(
    RadioStation("rmf_fm", "RMF FM", "Pop / Hits", "https://rs201-krk.rmfstream.pl/rmf_fm", "PL"),
    RadioStation("eska_hit", "Radio Eska", "Dance / Chart", "https://waw.ic.smcdn.pl/2380-1.mp3", "PL"),
    RadioStation("nowy_swiat", "Radio Nowy Świat", "Alt / Independent", "https://stream.rcs.revma.com/ypqt40u0x1zuv", "PL"),
    RadioStation("somafm_groove", "SomaFM Groove Salad", "Ambient / Chill", "https://ice1.somafm.com/groovesalad-128-mp3", "US"),
    RadioStation("somafm_defcon", "SomaFM DEF CON", "Electronic / Synth", "https://ice1.somafm.com/defcon-128-mp3", "US"),
    RadioStation("radio_paradise", "Radio Paradise", "Eclectic / Rock", "https://stream.radioparadise.com/mp3-128", "US"),
)

enum class LoopMode {
    OFF,
    ONE,
    ALL,
}

@Immutable
data class LocalPlayerState(
    val tracks: List<LocalAudioTrack> = emptyList(),
    val radioStations: List<RadioStation> = BUILT_IN_RADIO_STATIONS,
    val currentIndex: Int = -1,
    val currentRadioId: String? = null,
    val currentTitle: String = "",
    val currentArtist: String = "",
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val loopMode: LoopMode = LoopMode.ALL,
    val isRemoteStream: Boolean = false,
    val isLiveRadio: Boolean = false,
    val lastError: String? = null,
)

class LocalAudioPlayer(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false
    @Volatile
    private var isPrepared = false
    @Volatile
    private var pauseRequestedWhilePreparing = false
    @Volatile
    private var isLoopTrack = false

    private val builtInTrack = LocalAudioTrack(
        id = -1L,
        title = "LibreCar Horizon (Built-in)",
        artist = "LibreCar Synth · 16kHz",
        durationMs = 6_000L,
        contentUri = Uri.parse("android.resource://${context.packageName}/${R.raw.librecar_chill_loop}"),
        isLoopBuiltIn = true,
    )

    private val _state = MutableStateFlow(
        LocalPlayerState(tracks = listOf(builtInTrack))
    )
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
        val list = mutableListOf(builtInTrack)
        if (hasAudioPermission()) {
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
                        if (isPrepared && mediaPlayer?.isPlaying == true) {
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
        if (list.isEmpty()) return
        val safeIdx = index.coerceIn(0, list.lastIndex)
        val track = list[safeIdx]
        startUri(
            uri = track.contentUri,
            isHttp = false,
            isRadio = false,
            radioId = null,
            title = track.title,
            artist = track.artist,
            durationMs = track.durationMs,
            localIdx = safeIdx,
            looping = track.isLoopBuiltIn,
        )
    }

    fun playRadioStation(station: RadioStation) {
        startUri(
            uri = Uri.parse(station.streamUrl),
            isHttp = true,
            isRadio = true,
            radioId = station.id,
            title = station.name,
            artist = "${station.genre} · LIVE Radio (${station.country})",
            durationMs = 0L,
            localIdx = _state.value.currentIndex,
            looping = false,
        )
    }

    fun playRadioByQuery(query: String): RadioStation {
        val stations = _state.value.radioStations
        val q = query.trim().lowercase()
        val match = if (q.isBlank()) {
            stations.firstOrNull { it.id == _state.value.currentRadioId } ?: stations.first()
        } else {
            stations.firstOrNull {
                it.name.lowercase().contains(q) || it.genre.lowercase().contains(q)
            } ?: stations.first()
        }
        playRadioStation(match)
        return match
    }

    fun addCustomRadioStation(name: String, url: String) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http")) return
        val cleanName = name.trim().ifBlank { "Custom Radio" }
        val custom = RadioStation(
            id = "custom_${System.currentTimeMillis()}",
            name = cleanName,
            genre = "Custom Stream",
            streamUrl = cleanUrl,
            country = "NET",
        )
        _state.value = _state.value.copy(radioStations = _state.value.radioStations + custom)
        playRadioStation(custom)
    }

    fun nextRadio() {
        val list = _state.value.radioStations
        if (list.isEmpty()) return
        val curIdx = list.indexOfFirst { it.id == _state.value.currentRadioId }
        val next = list[(curIdx + 1) % list.size]
        playRadioStation(next)
    }

    fun prevRadio() {
        val list = _state.value.radioStations
        if (list.isEmpty()) return
        val curIdx = list.indexOfFirst { it.id == _state.value.currentRadioId }
        val prevIdx = if (curIdx <= 0) list.lastIndex else curIdx - 1
        playRadioStation(list[prevIdx])
    }

    fun playPreviewUrl(url: String, title: String, artist: String) {
        startUri(
            uri = Uri.parse(url),
            isHttp = true,
            isRadio = false,
            radioId = null,
            title = title,
            artist = "$artist · Preview",
            durationMs = 30_000L,
            localIdx = _state.value.currentIndex,
            looping = false,
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
        isRadio: Boolean,
        radioId: String?,
        title: String,
        artist: String,
        durationMs: Long,
        localIdx: Int,
        looping: Boolean,
    ) {
        requestFocus()
        resumeOnFocusGain = false
        pauseRequestedWhilePreparing = false
        isPrepared = false
        isLoopTrack = looping
        val effectiveLoop = !isRadio && (_state.value.loopMode == LoopMode.ONE || (looping && _state.value.loopMode == LoopMode.ALL))
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
                isLooping = effectiveLoop
                if (isHttp) {
                    setDataSource(uri.toString())
                } else {
                    setDataSource(context, uri)
                }
                setOnPreparedListener { player ->
                    isPrepared = true
                    if (pauseRequestedWhilePreparing) {
                        pauseRequestedWhilePreparing = false
                        _state.value = _state.value.copy(isPlaying = false, isPreparing = false)
                        return@setOnPreparedListener
                    }
                    player.setVolume(1.0f, 1.0f)
                    player.start()
                    val dur = if (isRadio) 0L else player.duration.toLong().coerceAtLeast(durationMs)
                    _state.value = _state.value.copy(
                        isPlaying = true,
                        isPreparing = false,
                        durationMs = dur,
                        positionMs = 0L,
                        lastError = null,
                    )
                }
                setOnCompletionListener {
                    if (_state.value.loopMode == LoopMode.ONE && !isRadio) return@setOnCompletionListener
                    if (isRadio || isHttp) {
                        _state.value = _state.value.copy(isPlaying = false, isPreparing = false, positionMs = 0L)
                    } else {
                        val list = _state.value.tracks
                        val atEnd = _state.value.currentIndex >= list.lastIndex && !_state.value.shuffle
                        if (_state.value.loopMode == LoopMode.OFF && atEnd) {
                            _state.value = _state.value.copy(isPlaying = false, isPreparing = false, positionMs = 0L)
                        } else {
                            next()
                        }
                    }
                }
                setOnErrorListener { _, what, extra ->
                    isPrepared = false
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        isPreparing = false,
                        lastError = "Audio stream error ($what/$extra)",
                    )
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
            _state.value = _state.value.copy(
                currentIndex = localIdx,
                currentRadioId = radioId,
                currentTitle = title,
                currentArtist = artist,
                isPlaying = true,
                isPreparing = true,
                durationMs = durationMs,
                positionMs = 0L,
                isRemoteStream = isHttp,
                isLiveRadio = isRadio,
                lastError = null,
            )
        }.onFailure { err ->
            _state.value = _state.value.copy(
                isPlaying = false,
                isPreparing = false,
                lastError = err.message ?: "Playback unavailable",
            )
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer
        if (mp == null) {
            if (_state.value.isLiveRadio && _state.value.currentRadioId != null) {
                val st = _state.value.radioStations.firstOrNull { it.id == _state.value.currentRadioId }
                if (st != null) {
                    playRadioStation(st)
                    return
                }
            }
            if (_state.value.tracks.isNotEmpty()) {
                val idx = _state.value.currentIndex.takeIf { it in _state.value.tracks.indices } ?: 0
                playLocalIndex(idx)
            }
            return
        }
        if (!isPrepared) {
            // Respect tap while HTTP radio stream is still buffering!
            pauseRequestedWhilePreparing = !pauseRequestedWhilePreparing
            _state.value = _state.value.copy(isPlaying = !pauseRequestedWhilePreparing)
            return
        }
        runCatching {
            if (mp.isPlaying) {
                resumeOnFocusGain = false
                pauseInternal()
            } else {
                requestFocus()
                mp.setVolume(1.0f, 1.0f)
                mp.start()
                _state.value = _state.value.copy(isPlaying = true, lastError = null)
            }
        }.onFailure {
            // Re-prepare cleanly if player entered invalid state
            if (_state.value.isLiveRadio && _state.value.currentRadioId != null) {
                val st = _state.value.radioStations.firstOrNull { it.id == _state.value.currentRadioId }
                if (st != null) playRadioStation(st)
            } else if (_state.value.tracks.isNotEmpty()) {
                playLocalIndex(_state.value.currentIndex.coerceAtLeast(0))
            }
        }
    }

    fun pause() {
        resumeOnFocusGain = false
        if (!isPrepared && _state.value.isPreparing) {
            pauseRequestedWhilePreparing = true
            _state.value = _state.value.copy(isPlaying = false)
            return
        }
        pauseInternal()
    }

    private fun pauseInternal() {
        runCatching {
            if (isPrepared && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    fun resume() {
        if (!isPrepared && _state.value.isPreparing) {
            pauseRequestedWhilePreparing = false
            _state.value = _state.value.copy(isPlaying = true)
            return
        }
        runCatching {
            if (isPrepared && mediaPlayer != null && mediaPlayer?.isPlaying == false) {
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
        if (_state.value.isLiveRadio) {
            nextRadio()
            return
        }
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
        if (_state.value.isLiveRadio) {
            prevRadio()
            return
        }
        val list = _state.value.tracks
        if (list.isEmpty()) return
        val cur = _state.value.currentIndex
        val prevIdx = if (cur <= 0) list.lastIndex else cur - 1
        playLocalIndex(prevIdx)
    }

    fun seekTo(ms: Long) {
        if (!isPrepared || _state.value.isLiveRadio) return
        runCatching {
            mediaPlayer?.seekTo(ms.toInt().coerceAtLeast(0))
            _state.value = _state.value.copy(positionMs = ms)
        }
    }

    fun toggleShuffle() {
        _state.value = _state.value.copy(shuffle = !_state.value.shuffle)
    }

    fun toggleLoopMode(): LoopMode {
        val next = when (_state.value.loopMode) {
            LoopMode.ALL -> LoopMode.ONE
            LoopMode.ONE -> LoopMode.OFF
            LoopMode.OFF -> LoopMode.ALL
        }
        _state.value = _state.value.copy(loopMode = next)
        runCatching {
            if (isPrepared && !_state.value.isLiveRadio) {
                mediaPlayer?.isLooping = (next == LoopMode.ONE) || (isLoopTrack && next == LoopMode.ALL)
            }
        }
        return next
    }

    fun pollProgress() {
        val mp = mediaPlayer ?: return
        if (!isPrepared) return
        runCatching {
            if (mp.isPlaying) {
                val pos = mp.currentPosition.toLong().coerceAtLeast(0L)
                val dur = if (_state.value.isLiveRadio) 0L else mp.duration.toLong().coerceAtLeast(_state.value.durationMs)
                if (pos != _state.value.positionMs || dur != _state.value.durationMs) {
                    _state.value = _state.value.copy(positionMs = pos, durationMs = dur, isPlaying = true)
                }
            } else if (_state.value.isPlaying && !_state.value.isPreparing) {
                _state.value = _state.value.copy(isPlaying = false)
            }
        }
    }

    fun releasePlayer() {
        isPrepared = false
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }

    fun destroy() {
        releasePlayer()
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }
}
