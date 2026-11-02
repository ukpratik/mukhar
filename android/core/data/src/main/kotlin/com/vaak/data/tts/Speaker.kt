package com.vaak.data.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import com.vaak.domain.model.Lang
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** On-device pronunciation (US-020): en-IN or hi-IN, no network. */
interface Speaker {
    /** Per language: true = a voice is installed, false = none, missing key = still initialising. */
    val voices: StateFlow<Map<Lang, Boolean>>
    fun warmUp()
    fun speak(text: String, lang: Lang)
}

/** Whether the speaker shows and whether the one-time "add a voice" tip shows (S14 state 4). */
data class SpeakerUi(val showSpeaker: Boolean, val showVoiceTip: Boolean)

object TtsSupport {
    val EN_IN: Locale = Locale.forLanguageTag("en-IN")
    val HI_IN: Locale = Locale.forLanguageTag("hi-IN")

    fun locale(lang: Lang): Locale = if (lang == Lang.HI) HI_IN else EN_IN

    /** `TextToSpeech.isLanguageAvailable` codes: ≥ LANG_AVAILABLE (0) means a usable voice; MISSING_DATA/NOT_SUPPORTED don't. */
    fun hasVoice(code: Int): Boolean = code >= TextToSpeech.LANG_AVAILABLE

    /**
     * The speaker is hidden (not greyed) when no voice is installed, and while the engine starts its 48 dp slot stays
     * reserved and empty; the tip shows once per language.
     */
    fun ui(voice: Boolean?, tipAlreadyShown: Boolean): SpeakerUi = when (voice) {
        true -> SpeakerUi(showSpeaker = true, showVoiceTip = false)
        false -> SpeakerUi(showSpeaker = false, showVoiceTip = !tipAlreadyShown)
        null -> SpeakerUi(showSpeaker = false, showVoiceTip = false)
    }

    /** "Open voice settings": install TTS data; the caller falls back to [SYSTEM_TTS_SETTINGS] if nothing handles it. */
    fun installVoiceIntent(): Intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    const val SYSTEM_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
}

/** Android [TextToSpeech], started on first use and kept for the app's lifetime. */
@Singleton
class AndroidSpeaker @Inject constructor(@param:ApplicationContext private val context: Context) : Speaker {
    private val _voices = MutableStateFlow<Map<Lang, Boolean>>(emptyMap())
    override val voices: StateFlow<Map<Lang, Boolean>> = _voices.asStateFlow()
    private var tts: TextToSpeech? = null
    private var ready = false

    @Synchronized
    override fun warmUp() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            ready = status == TextToSpeech.SUCCESS && engine != null
            _voices.value = Lang.entries.associateWith { lang ->
                ready && runCatching { TtsSupport.hasVoice(engine!!.isLanguageAvailable(TtsSupport.locale(lang))) }.getOrDefault(false)
            }
        }
    }

    override fun speak(text: String, lang: Lang) {
        val engine = tts ?: return warmUp()
        if (!ready || _voices.value[lang] != true) return
        engine.language = TtsSupport.locale(lang)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vaak-word")
    }
}
