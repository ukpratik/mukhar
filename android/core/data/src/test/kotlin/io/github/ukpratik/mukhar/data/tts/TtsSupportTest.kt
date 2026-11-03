package io.github.ukpratik.mukhar.data.tts

import android.speech.tts.TextToSpeech
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.model.Lang
import org.junit.Test

/** US-020: the speaker is hidden, not greyed, when no voice is installed; the tip shows once per language. */
class TtsSupportTest {
    @Test
    fun onlyInstalledVoicesCount() {
        assertThat(TtsSupport.hasVoice(TextToSpeech.LANG_AVAILABLE)).isTrue()
        assertThat(TtsSupport.hasVoice(TextToSpeech.LANG_COUNTRY_AVAILABLE)).isTrue()
        assertThat(TtsSupport.hasVoice(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE)).isTrue()
        assertThat(TtsSupport.hasVoice(TextToSpeech.LANG_MISSING_DATA)).isFalse()
        assertThat(TtsSupport.hasVoice(TextToSpeech.LANG_NOT_SUPPORTED)).isFalse()
    }

    @Test
    fun noVoiceHidesTheSpeakerAndShowsTheTipOnce() {
        assertThat(TtsSupport.ui(voice = true, tipAlreadyShown = false)).isEqualTo(SpeakerUi(showSpeaker = true, showVoiceTip = false))
        assertThat(TtsSupport.ui(voice = false, tipAlreadyShown = false)).isEqualTo(SpeakerUi(showSpeaker = false, showVoiceTip = true))
        assertThat(TtsSupport.ui(voice = false, tipAlreadyShown = true)).isEqualTo(SpeakerUi(showSpeaker = false, showVoiceTip = false))
        // While the engine starts: nothing yet (the UI keeps the 48 dp slot reserved).
        assertThat(TtsSupport.ui(voice = null, tipAlreadyShown = false)).isEqualTo(SpeakerUi(showSpeaker = false, showVoiceTip = false))
    }

    @Test
    fun indianLocales() {
        assertThat(TtsSupport.locale(Lang.EN).toLanguageTag()).isEqualTo("en-IN")
        assertThat(TtsSupport.locale(Lang.HI).toLanguageTag()).isEqualTo("hi-IN")
    }
}
