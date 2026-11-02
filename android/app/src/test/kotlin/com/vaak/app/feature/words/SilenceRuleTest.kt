package com.vaak.app.feature.words

import com.google.common.truth.Truth.assertThat
import com.vaak.app.feature.words.review.ReviewViewModel
import com.vaak.app.feature.words.review.SentenceFailure
import com.vaak.app.feature.words.review.SilenceRule
import com.vaak.data.net.AppError
import org.junit.Test

/** S13: the sentence clip auto-stops after 1.5 s of silence once speech started; errors map to their states. */
class SilenceRuleTest {
    private val loud = List(40) { 0.5f }
    private val quiet = List(30) { 0.01f }

    @Test
    fun stopsOnlyAfterSpeechAndAFullSecondAndAHalfOfQuiet() {
        assertThat(SilenceRule.shouldStop(2_000, loud + quiet)).isTrue()
        assertThat(SilenceRule.shouldStop(2_000, loud + quiet.drop(1))).isFalse()
        assertThat(SilenceRule.shouldStop(500, loud + quiet)).isFalse() // nothing said yet: keep listening
    }

    @Test
    fun failuresMapToTheirStates() {
        assertThat(ReviewViewModel.failureOf(AppError.Offline)).isEqualTo(SentenceFailure.OFFLINE)
        assertThat(ReviewViewModel.failureOf(AppError.Quota(null))).isEqualTo(SentenceFailure.QUOTA)
        assertThat(ReviewViewModel.failureOf(AppError.TooLittleSpeech)).isEqualTo(SentenceFailure.TOO_SHORT)
        assertThat(ReviewViewModel.failureOf(AppError.Busy(null))).isEqualTo(SentenceFailure.ERROR)
    }
}
