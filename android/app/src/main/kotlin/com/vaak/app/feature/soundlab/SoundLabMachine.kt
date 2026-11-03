package com.vaak.app.feature.soundlab

import kotlin.random.Random

/** S22's three steps per pair: Listen → Pick → Say. */
enum class LabStep { LISTEN, PICK, SAY }

/** One "which one did you hear?" clip: [word] plays, the user chooses between [choices] (side A, side B). */
data class PickRound(val word: PairWord, val choices: Pair<PairWord, PairWord>)

/** The user's own take in the Say step. On the device only: never uploaded, never scored, deleted on leaving. */
sealed interface Take {
    data object None : Take
    data class Recording(val elapsedMs: Long = 0) : Take
    data class Ready(val path: String, val durationMs: Long) : Take

    /** Too little sound, or the mic couldn't open. Never phrased as a failure of the user. */
    data object Retry : Take
    data object MicDenied : Take
}

data class PairSession(
    val pair: MinimalPair,
    val step: LabStep = LabStep.LISTEN,
    val rounds: List<PickRound>,
    val round: Int = 0,
    /** The word the user picked in this round; null until they pick. */
    val picked: PairWord? = null,
    val take: Take = Take.None,
) {
    val current: PickRound? get() = rounds.getOrNull(round)
    val isLastRound: Boolean get() = round >= rounds.lastIndex

    /** Revealed after a pick. There is no tally: nothing about it is kept once the round moves on. */
    val pickedRight: Boolean? get() = picked?.let { it.id == current?.word?.id }

    /** The model word in the Say step, then the one to contrast it with ("Say wine, then vine"). */
    val sayTarget: PairWord? get() = pair.say.firstOrNull()?.let(pair::word)
    val sayThen: PairWord? get() = pair.say.getOrNull(1)?.let(pair::word)

    /** The take file to delete when it's replaced or the pair is left (S22: deleted when you leave the pair). */
    val takePath: String? get() = (take as? Take.Ready)?.path
}

data class SoundLabState(
    val loading: Boolean = true,
    val pairs: List<MinimalPair> = emptyList(),
    /** Pairs the user has finished (pair ids only, kept by the ViewModel): a quiet "Practised" chip, never a score. */
    val practised: Set<String> = emptySet(),
    val session: PairSession? = null,
)

sealed interface LabEvent {
    data class Loaded(val pairs: List<MinimalPair>) : LabEvent
    data class Open(val pairId: String) : LabEvent
    data object TestMyEar : LabEvent
    data class Pick(val wordId: String) : LabEvent
    data object NextClip : LabEvent
    data object RecordStarted : LabEvent
    data class RecordProgress(val elapsedMs: Long) : LabEvent
    data class TakeReady(val path: String, val durationMs: Long) : LabEvent
    data object TakeRetry : LabEvent
    data object MicDenied : LabEvent
    data object RecordAgain : LabEvent
    data object Done : LabEvent
    data object Back : LabEvent
}

/**
 * Sound Lab lite (US-070) as a pure reducer, so every transition is unit-tested. Side effects (playback, the
 * recorder, deleting take files) live in [SoundLabViewModel]. Nothing here scores: a pick reveals the answer for that
 * clip only, and the Say step only offers playback next to the model.
 */
class SoundLabMachine(private val random: Random = Random.Default) {

    fun reduce(state: SoundLabState, event: LabEvent): SoundLabState {
        val s = state.session
        return when (event) {
            is LabEvent.Loaded -> state.copy(loading = false, pairs = event.pairs)
            is LabEvent.Open -> {
                val pair = state.pairs.firstOrNull { it.id == event.pairId } ?: return state
                state.copy(session = PairSession(pair, rounds = rounds(pair)))
            }
            LabEvent.Back -> state.copy(session = null)
            LabEvent.Done -> if (s == null) state else state.copy(session = null, practised = state.practised + s.pair.id)
            else -> if (s == null) state else state.copy(session = session(s, event))
        }
    }

    private fun session(s: PairSession, e: LabEvent): PairSession = when (e) {
        LabEvent.TestMyEar -> if (s.step == LabStep.LISTEN) s.copy(step = LabStep.PICK, round = 0, picked = null) else s
        is LabEvent.Pick -> {
            val r = s.current
            val choice = r?.choices?.toList()?.firstOrNull { it.id == e.wordId }
            if (s.step != LabStep.PICK || s.picked != null || choice == null) s else s.copy(picked = choice)
        }
        LabEvent.NextClip -> when {
            s.step != LabStep.PICK || s.picked == null -> s
            s.isLastRound -> s.copy(step = LabStep.SAY, picked = null)
            else -> s.copy(round = s.round + 1, picked = null)
        }
        LabEvent.RecordStarted -> if (s.step == LabStep.SAY) s.copy(take = Take.Recording()) else s
        is LabEvent.RecordProgress -> if (s.take is Take.Recording) s.copy(take = Take.Recording(e.elapsedMs)) else s
        is LabEvent.TakeReady -> if (s.step == LabStep.SAY) s.copy(take = Take.Ready(e.path, e.durationMs)) else s
        LabEvent.TakeRetry -> if (s.step == LabStep.SAY) s.copy(take = Take.Retry) else s
        LabEvent.MicDenied -> if (s.step == LabStep.SAY) s.copy(take = Take.MicDenied) else s
        LabEvent.RecordAgain -> if (s.step == LabStep.SAY) s.copy(take = Take.None) else s
        else -> s
    }

    /**
     * 4 clips in random order, balanced: each word pair is heard once from each side (with two word pairs, that's all
     * four words). Choices are always the two words of the clip's own pair.
     */
    fun rounds(pair: MinimalPair): List<PickRound> {
        val wp = pair.wordPairs
        if (wp.isEmpty()) return emptyList()
        return (0 until ROUNDS).map { i ->
            val p = wp[(i / 2) % wp.size]
            PickRound(if (i % 2 == 0) p.first else p.second, p)
        }.shuffled(random)
    }

    companion object {
        const val ROUNDS = 4

        /** S22: a take is at most 3 s. */
        const val TAKE_CAP_MS = 3_000L

        /** A take shorter than this is offered again ("Didn't catch that"), never judged. */
        const val MIN_TAKE_MS = 300L
    }
}
