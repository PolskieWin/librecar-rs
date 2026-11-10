package com.librecar.rust.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.core.content.ContextCompat
import com.librecar.rust.ui.theme.AppLang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class VoiceUiState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val errorMsg: String? = null,
    val speechAvailable: Boolean = true,
)

class VoiceAssistant(
    private val context: Context,
    private val onRecognizedText: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val _state = MutableStateFlow(
        VoiceUiState(speechAvailable = SpeechRecognizer.isRecognitionAvailable(context))
    )
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
            }
        }
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun startListening(voiceLang: AppLang) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = _state.value.copy(speechAvailable = false, errorMsg = "System speech service unavailable — type below")
            return
        }
        stopListening()
        runCatching {
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = _state.value.copy(isListening = true, errorMsg = null, partialText = "")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _state.value = _state.value.copy(isListening = false)
                }
                override fun onError(error: Int) {
                    _state.value = _state.value.copy(
                        isListening = false,
                        errorMsg = "Voice error ($error) — try again or type",
                    )
                }
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    _state.value = _state.value.copy(isListening = false, partialText = text)
                    if (text.isNotBlank()) onRecognizedText(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _state.value = _state.value.copy(partialText = partial)
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val localeTag = when (voiceLang) {
                AppLang.PL -> "pl-PL"
                AppLang.EN -> "en-US"
                AppLang.AUTO -> {
                    if (Locale.getDefault().language.lowercase().startsWith("pl")) "pl-PL" else "en-US"
                }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer = sr
            sr.startListening(intent)
        }.onFailure {
            _state.value = _state.value.copy(isListening = false, errorMsg = it.message)
        }
    }

    fun stopListening() {
        runCatching {
            recognizer?.stopListening()
            recognizer?.destroy()
        }
        recognizer = null
        _state.value = _state.value.copy(isListening = false)
    }

    fun speak(
        text: String,
        langCode: String,
        preset: AssistantVoicePreset = AssistantVoicePreset.NOVA,
    ) {
        if (!ttsReady || text.isBlank()) return
        runCatching {
            val engine = tts ?: return
            val targetLocale = if (langCode == "pl") Locale.forLanguageTag("pl-PL") else Locale.US
            engine.language = targetLocale
            pickGeminiStyleVoice(engine, targetLocale, preset)?.let { engine.voice = it }
            engine.setPitch(preset.pitch)
            engine.setSpeechRate(preset.rate)
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "librecar_tts")
        }
    }

    fun speakPreview(preset: AssistantVoicePreset, isPolish: Boolean) {
        val name = preset.id.replaceFirstChar { it.uppercase() }
        val sample = if (isPolish) {
            "Cześć, tu $name. Asystent LibreCar jest gotowy do drogi."
        } else {
            "Hi, I am $name. LibreCar assistant is ready for your drive."
        }
        speak(sample, if (isPolish) "pl" else "en", preset)
    }

    private fun pickGeminiStyleVoice(
        engine: TextToSpeech,
        targetLocale: Locale,
        preset: AssistantVoicePreset,
    ): Voice? {
        val available = runCatching { engine.voices }.getOrNull().orEmpty()
        if (available.isEmpty()) return null
        val targetLang = targetLocale.language.lowercase()
        val matching = available
            .filter { v ->
                v.locale.language.lowercase() == targetLang && !v.isNetworkConnectionRequired
            }
            .sortedBy { it.name }
        if (matching.isEmpty()) return null

        val gendered = matching.filter { v ->
            val n = v.name.lowercase()
            if (preset.preferFemale) {
                n.contains("female") || n.contains("-x-iob") || n.contains("-x-sfg") ||
                    n.contains("-x-tpf") || n.contains("-x-pfc") || n.contains("-x-pfd")
            } else {
                n.contains("male") && !n.contains("female") ||
                    n.contains("-x-iom") || n.contains("-x-tpd") || n.contains("-x-gba") || n.contains("-x-pfa")
            }
        }
        val pool = if (gendered.isNotEmpty()) gendered else matching
        return pool[preset.ordinal % pool.size]
    }

    fun destroy() {
        stopListening()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
    }
}
