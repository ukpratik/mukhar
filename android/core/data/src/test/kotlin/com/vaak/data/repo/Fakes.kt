package com.vaak.data.repo

import com.vaak.data.net.AppError
import com.vaak.data.net.Outcome
import com.vaak.data.prefs.TalkPrefs
import com.vaak.data.prefs.UserSettings
import com.vaak.data.work.AnalysisPipeline
import com.vaak.data.work.AnalysisScheduler
import com.vaak.data.work.FeedbackRequest
import com.vaak.data.work.FeedbackResult
import com.vaak.data.work.ReportRequest
import com.vaak.data.work.TranscribeRequest
import com.vaak.data.work.TranscribeResult
import com.vaak.domain.model.Lang
import com.vaak.domain.prompt.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** A pipeline whose answers are queued by the test; once a queue is empty the last answer repeats. */
class ScriptedPipeline : AnalysisPipeline {
    val transcribeAnswers = ArrayDeque<Outcome<TranscribeResult>>()
    val feedbackAnswers = ArrayDeque<(FeedbackRequest) -> Outcome<FeedbackResult>>()
    val transcribeCalls = mutableListOf<TranscribeRequest>()
    val feedbackCalls = mutableListOf<FeedbackRequest>()
    val reports = mutableListOf<ReportRequest>()

    override suspend fun transcribe(request: TranscribeRequest, audio: File): Outcome<TranscribeResult> {
        transcribeCalls += request
        return if (transcribeAnswers.size > 1) transcribeAnswers.removeFirst() else transcribeAnswers.firstOrNull() ?: Outcome.Err(AppError.Offline)
    }

    override suspend fun feedback(request: FeedbackRequest): Outcome<FeedbackResult> {
        feedbackCalls += request
        val f = if (feedbackAnswers.size > 1) feedbackAnswers.removeFirst() else feedbackAnswers.firstOrNull()
        return f?.invoke(request) ?: Outcome.Err(AppError.Offline)
    }

    override suspend fun report(request: ReportRequest): Outcome<Unit> {
        reports += request
        return Outcome.Ok(Unit)
    }
}

class RecordingScheduler : AnalysisScheduler {
    val enqueued = mutableListOf<String>()
    val delayed = mutableListOf<Pair<String, Long>>()
    val cancelled = mutableListOf<String>()
    override fun enqueue(sessionId: String) { enqueued += sessionId }
    override fun enqueueAfter(sessionId: String, delayMs: Long) { delayed += sessionId to delayMs }
    override fun cancel(sessionId: String) { cancelled += sessionId }
}

class MemorySettings(initial: TalkPrefs = TalkPrefs()) : UserSettings {
    val state = MutableStateFlow(initial)
    override val prefs: Flow<TalkPrefs> = state
    override suspend fun setLang(lang: Lang) { state.value = state.value.copy(lastLang = lang) }
    override suspend fun setTrack(lang: Lang, track: Track) { state.value = state.value.copy(lastTrack = state.value.lastTrack + (lang to track)) }
    override suspend fun setStutterFriendly(on: Boolean) { state.value = state.value.copy(stutterFriendly = on) }
    override suspend fun setTransliteration(on: Boolean) { state.value = state.value.copy(transliteration = on) }
    override suspend fun setStructureHint(on: Boolean) { state.value = state.value.copy(structureHint = on) }
    override suspend fun setThinkTime(on: Boolean) { state.value = state.value.copy(thinkTime = on) }
}
