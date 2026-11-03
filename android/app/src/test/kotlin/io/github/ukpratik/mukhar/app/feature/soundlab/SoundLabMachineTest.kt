package io.github.ukpratik.mukhar.app.feature.soundlab

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.soundlab.SoundLabFixtures.rr
import io.github.ukpratik.mukhar.app.feature.soundlab.SoundLabFixtures.vw
import org.junit.Test
import kotlin.random.Random

/** S22 Sound Lab lite state machine (US-070): Listen → Pick → Say, no scoring. */
class SoundLabMachineTest {
    private val machine = SoundLabMachine(Random(7))
    private val loaded = machine.reduce(SoundLabState(), LabEvent.Loaded(listOf(vw, rr)))

    private fun SoundLabState.on(vararg events: LabEvent) = events.fold(this) { s, e -> machine.reduce(s, e) }
    private val SoundLabState.s get() = session!!

    @Test
    fun loadsThenOpensAPairOnListen() {
        assertThat(SoundLabState().loading).isTrue()
        assertThat(loaded.loading).isFalse()
        assertThat(loaded.session).isNull()
        val open = loaded.on(LabEvent.Open("en-v-w"))
        assertThat(open.s.pair).isEqualTo(vw)
        assertThat(open.s.step).isEqualTo(LabStep.LISTEN)
        assertThat(open.s.take).isEqualTo(Take.None)
        // An unknown pair is ignored.
        assertThat(loaded.on(LabEvent.Open("nope")).session).isNull()
    }

    @Test
    fun fourBalancedRoundsCoverEveryWordAndOfferTheClipsOwnPair() {
        val rounds = machine.rounds(vw)
        assertThat(rounds).hasSize(SoundLabMachine.ROUNDS)
        assertThat(rounds.map { it.word.text }).containsExactly("vest", "west", "vine", "wine")
        for (r in rounds) {
            assertThat(r.choices.toList()).contains(r.word)
            assertThat(vw.side(r.choices.first)).isEqualTo(Side.A)
            assertThat(vw.side(r.choices.second)).isEqualTo(Side.B)
        }
        // Random order, but deterministic for a seed.
        assertThat(SoundLabMachine(Random(7)).rounds(vw)).isEqualTo(rounds)
    }

    @Test
    fun aPickIsRevealedOnceAndKindlyWithNoTally() {
        val pick = loaded.on(LabEvent.Open("en-v-w"), LabEvent.TestMyEar)
        assertThat(pick.s.step).isEqualTo(LabStep.PICK)
        assertThat(pick.s.round).isEqualTo(0)
        assertThat(pick.s.pickedRight).isNull()

        val round = pick.s.current!!
        val other = round.choices.toList().first { it != round.word }
        val wrong = pick.on(LabEvent.Pick(other.id))
        assertThat(wrong.s.picked).isEqualTo(other)
        assertThat(wrong.s.pickedRight).isFalse()
        // A second pick in the same round is ignored (the answer is already shown).
        assertThat(wrong.on(LabEvent.Pick(round.word.id)).s.picked).isEqualTo(other)

        val right = pick.on(LabEvent.Pick(round.word.id))
        assertThat(right.s.pickedRight).isTrue()

        // The next clip starts fresh: nothing from the last pick is carried, so there's nothing to tally.
        val next = wrong.on(LabEvent.NextClip)
        assertThat(next.s.round).isEqualTo(1)
        assertThat(next.s.picked).isNull()
        assertThat(next.s.pickedRight).isNull()
    }

    @Test
    fun picksOutsideTheRoundOrBeforePickStepAreIgnored() {
        val listen = loaded.on(LabEvent.Open("en-v-w"))
        assertThat(listen.on(LabEvent.Pick("en_vest")).s.picked).isNull()
        val pick = listen.on(LabEvent.TestMyEar)
        assertThat(pick.on(LabEvent.Pick("hi_mor")).s.picked).isNull()
        // Next clip needs a pick first.
        assertThat(pick.on(LabEvent.NextClip).s.round).isEqualTo(0)
    }

    @Test
    fun afterTheLastClipTheSayStepOpens() {
        var st = loaded.on(LabEvent.Open("en-v-w"), LabEvent.TestMyEar)
        repeat(SoundLabMachine.ROUNDS) { i ->
            assertThat(st.s.step).isEqualTo(LabStep.PICK)
            assertThat(st.s.isLastRound).isEqualTo(i == SoundLabMachine.ROUNDS - 1)
            st = st.on(LabEvent.Pick(st.s.current!!.word.id), LabEvent.NextClip)
        }
        assertThat(st.s.step).isEqualTo(LabStep.SAY)
        assertThat(st.s.sayTarget?.text).isEqualTo("wine")
        assertThat(st.s.sayThen?.text).isEqualTo("vine")
    }

    @Test
    fun takeLifecycleInTheSayStep() {
        val say = toSay(vw)
        val rec = say.on(LabEvent.RecordStarted, LabEvent.RecordProgress(1_200))
        assertThat(rec.s.take).isEqualTo(Take.Recording(1_200))
        val ready = rec.on(LabEvent.TakeReady("/cache/soundlab/take-1.aac", 1_400))
        assertThat(ready.s.take).isEqualTo(Take.Ready("/cache/soundlab/take-1.aac", 1_400))
        assertThat(ready.s.takePath).isEqualTo("/cache/soundlab/take-1.aac")
        val again = ready.on(LabEvent.RecordAgain)
        assertThat(again.s.take).isEqualTo(Take.None)
        assertThat(again.s.takePath).isNull()
        assertThat(say.on(LabEvent.RecordStarted, LabEvent.TakeRetry).s.take).isEqualTo(Take.Retry)
        assertThat(say.on(LabEvent.MicDenied).s.take).isEqualTo(Take.MicDenied)
        // Progress only updates a running take.
        assertThat(say.on(LabEvent.RecordProgress(500)).s.take).isEqualTo(Take.None)
    }

    @Test
    fun recordingIsOnlyPossibleInTheSayStep() {
        val listen = loaded.on(LabEvent.Open("hi-rra-ra"))
        assertThat(listen.on(LabEvent.RecordStarted).s.take).isEqualTo(Take.None)
        assertThat(listen.on(LabEvent.TakeReady("/x.aac", 900)).s.take).isEqualTo(Take.None)
    }

    @Test
    fun doneMarksPractisedButBackDoesNot() {
        val say = toSay(rr).on(LabEvent.TakeReady("/x.aac", 900))
        val done = say.on(LabEvent.Done)
        assertThat(done.session).isNull()
        assertThat(done.practised).containsExactly("hi-rra-ra")

        val back = loaded.on(LabEvent.Open("en-v-w"), LabEvent.TestMyEar, LabEvent.Back)
        assertThat(back.session).isNull()
        assertThat(back.practised).isEmpty()
        // Back and Done with no open pair change nothing.
        assertThat(loaded.on(LabEvent.Back, LabEvent.Done)).isEqualTo(loaded)
    }

    @Test
    fun hindiPairSaysMorThenMod() {
        val say = toSay(rr)
        assertThat(say.s.sayTarget?.text).isEqualTo("मोड़")
        assertThat(say.s.sayThen?.text).isEqualTo("मोर")
    }

    private fun toSay(pair: MinimalPair): SoundLabState {
        var st = loaded.on(LabEvent.Open(pair.id), LabEvent.TestMyEar)
        while (st.s.step == LabStep.PICK) st = st.on(LabEvent.Pick(st.s.current!!.word.id), LabEvent.NextClip)
        return st
    }
}
