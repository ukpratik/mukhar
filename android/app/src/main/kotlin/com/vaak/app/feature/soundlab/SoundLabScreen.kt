package com.vaak.app.feature.soundlab

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaak.app.R
import com.vaak.app.feature.talk.common.InfoChip
import com.vaak.app.feature.talk.common.InfoNote
import com.vaak.app.feature.talk.common.SectionHead
import com.vaak.app.feature.talk.common.VaakAppBar
import com.vaak.app.feature.talk.common.clock
import com.vaak.app.feature.talk.common.langLabel
import com.vaak.domain.model.Lang
import com.vaak.ui.text.mixedScript
import com.vaak.ui.theme.FrauncesText
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme

const val TAG_SOUNDLAB_LIST = "soundlab_list"
const val TAG_SOUNDLAB_REVEAL = "soundlab_reveal"
const val TAG_SOUNDLAB_PREVIEW = "soundlab_preview"
const val TAG_SOUNDLAB_STATUS = "soundlab_status"

/** Above this font scale the Pick step's two buttons stack. */
private const val LARGE_FONT_SCALE = 1.3f

/** S22 Sound Lab lite (US-070), opened from the Practice tab. */
@Composable
fun SoundLabScreen(onBack: () -> Unit, vm: SoundLabViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onAction(if (granted) SoundLabAction.StartRecord else SoundLabAction.Lab(LabEvent.MicDenied))
    }
    // System back inside a pair returns to the pair list (and deletes the take).
    BackHandler(enabled = state.lab.session != null) { vm.onAction(SoundLabAction.Lab(LabEvent.Back)) }
    SoundLabContent(
        state = state,
        onAction = { a ->
            if (a == SoundLabAction.StartRecord &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            ) {
                // Asked here, in context, if it was denied earlier (S22 notes).
                mic.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                vm.onAction(a)
            }
        },
        onBack = onBack,
    )
}

@Composable
fun SoundLabContent(state: SoundLabUiState, onAction: (SoundLabAction) -> Unit, onBack: () -> Unit) {
    val session = state.lab.session
    val title = session?.pair?.label ?: stringResource(R.string.soundlab_title)
    // The pane title changes between the pair list and a pair, so TalkBack announces where the user now is.
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).semantics { paneTitle = title }) {
        VaakAppBar(
            title = title,
            backDescription = stringResource(if (session != null) R.string.soundlab_back_pairs_cd else R.string.soundlab_back_cd),
            onBack = { if (session != null) onAction(SoundLabAction.Lab(LabEvent.Back)) else onBack() },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = VaakSpace.gutter).padding(bottom = 24.dp),
        ) {
            when {
                session != null -> {
                    StepsRow(session.step)
                    when (session.step) {
                        LabStep.LISTEN -> ListenStep(session, state, onAction)
                        LabStep.PICK -> PickStep(session, state, onAction)
                        LabStep.SAY -> SayStep(session, state, onAction)
                    }
                }
                state.loadFailed -> InfoNote(Icons.Rounded.Info, stringResource(R.string.soundlab_load_failed), Modifier.padding(top = 16.dp))
                !state.lab.loading -> PairList(state, onAction)
            }
        }
    }
}

// ---- Pair list ----

@Composable
private fun PairList(state: SoundLabUiState, onAction: (SoundLabAction) -> Unit) {
    Column(Modifier.testTag(TAG_SOUNDLAB_LIST)) {
        Text(stringResource(R.string.soundlab_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        val registry = state.registry
        if (registry != null && state.lab.pairs.any { !registry.fullyRecorded(it) }) {
            InfoNote(Icons.Rounded.Info, stringResource(R.string.soundlab_preview_list_note), Modifier.padding(top = 12.dp))
        }
        for (lang in Lang.entries) {
            val pairs = state.lab.pairs.filter { it.lang == lang }
            if (pairs.isEmpty()) continue
            SectionHead(stringResource(if (lang == Lang.HI) R.string.soundlab_hindi else R.string.soundlab_english), trailing = null)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pairs.forEach { PairRow(it, it.id in state.lab.practised) { onAction(SoundLabAction.Lab(LabEvent.Open(it.id))) } }
            }
        }
    }
}

@Composable
private fun PairRow(pair: MinimalPair, practised: Boolean, onOpen: () -> Unit) {
    val cd = if (practised) {
        stringResource(R.string.soundlab_pair_practised_cd, pair.label, pair.example, pair.hint)
    } else {
        stringResource(R.string.soundlab_pair_cd, pair.label, pair.example, pair.hint)
    }
    VaakCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = cd }) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.widthIn(min = 56.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(pair.label, style = wordStyle(pair.lang, 17f), color = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(mixedScript(pair.example), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(pair.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (practised) {
                InfoChip(stringResource(R.string.soundlab_practised), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary, icon = Icons.Rounded.Check)
            } else {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- Steps header ----

@Composable
private fun StepsRow(step: LabStep) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Triple(LabStep.LISTEN, R.string.soundlab_step_listen, Icons.Rounded.Hearing),
            Triple(LabStep.PICK, R.string.soundlab_step_pick, Icons.Rounded.TouchApp),
            Triple(LabStep.SAY, R.string.soundlab_step_say, Icons.Rounded.Mic),
        ).forEach { (s, label, icon) ->
            val done = s.ordinal < step.ordinal
            val now = s == step
            val text = stringResource(label)
            val cd = when {
                done -> stringResource(R.string.soundlab_step_done_cd, text)
                now -> stringResource(R.string.soundlab_step_now_cd, text)
                else -> text
            }
            // The current step is a polite live region, so moving Listen → Pick → Say is announced once.
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (now) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 6.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = cd
                        if (now) liveRegion = LiveRegionMode.Polite
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = when {
                    now -> MaterialTheme.colorScheme.primary
                    done -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(if (done) Icons.Rounded.Check else icon, null, tint = tint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(text, style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}

// ---- Listen ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListenStep(s: PairSession, state: SoundLabUiState, onAction: (SoundLabAction) -> Unit) {
    val pair = s.pair
    Text(stringResource(R.string.soundlab_listen_title), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 23.sp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
    Text(mixedScript(pair.listenHint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
    PreviewNotes(pair, state)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(pair.a, pair.b).forEach { side ->
            val word = side.words.first()
            VaakCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PlayButton(word, state, pair.lang, size = 56, onAction = onAction)
                    Text(word.text, style = wordStyle(pair.lang, if (pair.lang == Lang.HI) 30f else 28f), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
                    WordMeta(word)
                    Text(
                        tipText(side),
                        style = if (pair.lang == Lang.HI) VaakTheme.type.hiBodyMedium.copy(fontSize = 13.sp) else MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                    )
                }
            }
        }
    }
    // The other word pairs as small play chips (vine / wine).
    val more = pair.wordPairs.drop(1).flatMap { listOf(it.first, it.second) }
    if (more.isNotEmpty()) {
        FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            more.forEach { w -> SmallPlay(w, state, pair.lang, onAction) }
        }
    }
    Button(onClick = { onAction(SoundLabAction.Lab(LabEvent.TestMyEar)) }, modifier = Modifier.fillMaxWidth().padding(top = 18.dp).heightIn(min = 54.dp)) {
        Text(stringResource(R.string.soundlab_test_ear))
    }
}

private fun tipText(side: PairSide) = mixedScript("${side.sound}: ${side.tip}")

@Composable
private fun WordMeta(word: PairWord) {
    val meta = listOfNotNull(word.translit, word.gloss).joinToString(" · ")
    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

/** "Preview voice" label (design review: no recorded clip yet) and the no-voice note. */
@Composable
private fun PreviewNotes(pair: MinimalPair, state: SoundLabUiState) {
    if (pair.words.none(state::isPreview)) return
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag(TAG_SOUNDLAB_PREVIEW), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoChip(stringResource(R.string.soundlab_preview_voice), VaakTheme.colors.accentTint, VaakTheme.colors.accentInk, icon = Icons.Rounded.Info)
        val noVoice = state.voices[pair.lang] == false
        InfoNote(
            Icons.Rounded.Info,
            if (noVoice) stringResource(R.string.soundlab_no_voice, stringResource(langLabel(pair.lang))) else stringResource(R.string.soundlab_preview_note),
        )
    }
}

@Composable
private fun PlayButton(word: PairWord, state: SoundLabUiState, lang: Lang, size: Int, onAction: (SoundLabAction) -> Unit) {
    val playing = state.playing == word.id
    val enabled = state.canPlay(word, lang)
    val cd = stringResource(if (playing) R.string.soundlab_stop_cd else R.string.soundlab_play_cd, word.text)
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
            .clickable(enabled = enabled, role = Role.Button) { onAction(SoundLabAction.Play(word)) }
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            null,
            tint = if (playing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size((size * 0.54f).dp),
        )
    }
}

@Composable
private fun SmallPlay(word: PairWord, state: SoundLabUiState, lang: Lang, onAction: (SoundLabAction) -> Unit) {
    val playing = state.playing == word.id
    val cd = stringResource(if (playing) R.string.soundlab_stop_cd else R.string.soundlab_play_cd, word.text)
    Surface(
        onClick = { onAction(SoundLabAction.Play(word)) },
        enabled = state.canPlay(word, lang),
        shape = CircleShape,
        color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = if (playing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = cd },
    ) {
        Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text(word.text, style = wordStyle(lang, 17f))
        }
    }
}

// ---- Pick ----

@Composable
private fun PickStep(s: PairSession, state: SoundLabUiState, onAction: (SoundLabAction) -> Unit) {
    val round = s.current ?: return
    val pair = s.pair
    PreviewNotes(pair, state)
    val playing = state.playing == round.word.id
    val n = s.round + 1
    val total = s.rounds.size
    val roundCd = stringResource(R.string.soundlab_play_round_cd, n, total)
    val playingState = stringResource(R.string.soundlab_playing)
    VaakCard(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    .clickable(enabled = state.canPlay(round.word, pair.lang), role = Role.Button) { onAction(SoundLabAction.PlayRound) }
                    .semantics {
                        contentDescription = roundCd
                        if (playing) stateDescription = playingState
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.Replay, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
            }
            Text(stringResource(R.string.soundlab_clip_of, n, total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        }
    }
    Text(stringResource(R.string.soundlab_which), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 10.dp).semantics { heading() })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        round.choices.toList().forEach { w -> ChoiceButton(w, s, pair.lang, Modifier.weight(1f)) { onAction(SoundLabAction.Lab(LabEvent.Pick(w.id))) } }
    }
    val picked = s.picked
    if (picked != null) {
        val right = s.pickedRight == true
        Column(
            Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 14.dp, vertical = 12.dp).testTag(TAG_SOUNDLAB_REVEAL)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Hearing, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    mixedScript(stringResource(if (right) R.string.soundlab_reveal_right else R.string.soundlab_reveal_other, round.word.text)),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                mixedScript(if (right) stringResource(R.string.soundlab_reveal_right_body) else pair.revealHint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp),
            )
        }
        val hearBoth = @Composable { m: Modifier ->
            OutlinedButton(onClick = { onAction(SoundLabAction.HearBoth) }, modifier = m.heightIn(min = 54.dp)) {
                Text(stringResource(R.string.soundlab_hear_both), textAlign = TextAlign.Center)
            }
        }
        val next = @Composable { m: Modifier ->
            Button(onClick = { onAction(SoundLabAction.Lab(LabEvent.NextClip)) }, modifier = m.heightIn(min = 54.dp)) {
                Text(stringResource(if (s.isLastRound) R.string.soundlab_to_say else R.string.soundlab_next_clip), textAlign = TextAlign.Center)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
            }
        }
        // Side by side; stacked (next first) at large text so neither label is squeezed.
        if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) {
            Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                next(Modifier.fillMaxWidth())
                hearBoth(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                hearBoth(Modifier.weight(1f))
                next(Modifier.weight(1f))
            }
        }
    }
}

/** A choice; after a pick both show their role in words and border style, not colour alone (S22 notes). */
@Composable
private fun ChoiceButton(word: PairWord, s: PairSession, lang: Lang, modifier: Modifier, onPick: () -> Unit) {
    val revealed = s.picked != null
    val isAnswer = revealed && s.current?.word?.id == word.id
    val isPicked = s.picked?.id == word.id
    val role = when {
        isAnswer && isPicked -> stringResource(R.string.soundlab_picked_this_one)
        isAnswer -> stringResource(R.string.soundlab_this_one)
        isPicked -> stringResource(R.string.soundlab_you_picked)
        else -> null
    }
    val border = when {
        isAnswer -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        isPicked -> BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
        else -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    }
    Column(
        modifier.heightIn(min = 72.dp).clip(RoundedCornerShape(18.dp))
            .background(if (isAnswer) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(border, RoundedCornerShape(18.dp))
            .clickable(enabled = !revealed, role = Role.Button, onClick = onPick)
            .semantics { if (role != null) stateDescription = role }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(word.text, style = wordStyle(lang, if (lang == Lang.HI) 26f else 24f), color = if (isAnswer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (role != null) Text(role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Say ----

@Composable
private fun SayStep(s: PairSession, state: SoundLabUiState, onAction: (SoundLabAction) -> Unit) {
    val pair = s.pair
    val target = s.sayTarget ?: return
    val then = s.sayThen
    Text(
        mixedScript(if (then != null) stringResource(R.string.soundlab_say_title, target.text, then.text) else stringResource(R.string.soundlab_say_title_one, target.text)),
        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 23.sp), color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
    Text(stringResource(R.string.soundlab_say_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
    PreviewNotes(pair, state)
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val preview = state.isPreview(target)
        CompareRow(
            title = stringResource(R.string.soundlab_model, target.text),
            meta = stringResource(if (preview) R.string.soundlab_preview_voice else if (pair.lang == Lang.HI) R.string.soundlab_recorded_voice_hi else R.string.soundlab_recorded_voice_en),
            playing = state.playing == target.id,
            enabled = state.canPlay(target, pair.lang),
            playCd = stringResource(if (state.playing == target.id) R.string.soundlab_stop_cd else R.string.soundlab_play_cd, stringResource(R.string.soundlab_model, target.text)),
        ) { onAction(SoundLabAction.Play(target)) }
        val take = s.take
        if (take is Take.Ready) {
            CompareRow(
                title = stringResource(R.string.soundlab_you, target.text),
                meta = stringResource(R.string.soundlab_take_meta, clock(take.durationMs)),
                playing = state.playing == SoundLabUiState.MY_TAKE,
                enabled = true,
                playCd = stringResource(
                    if (state.playing == SoundLabUiState.MY_TAKE) R.string.soundlab_stop_cd else R.string.soundlab_play_cd,
                    stringResource(R.string.soundlab_you, target.text),
                ),
            ) { onAction(SoundLabAction.PlayMine) }
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { RecordButton(s.take, onAction) }
    when (s.take) {
        Take.Retry -> InfoNote(Icons.Rounded.Info, stringResource(R.string.soundlab_retry), Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
        Take.MicDenied -> {
            InfoNote(Icons.Rounded.Mic, stringResource(R.string.soundlab_mic_denied), Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
            TextButton(onClick = { onAction(SoundLabAction.StartRecord) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.soundlab_allow_mic)) }
        }
        else -> Unit
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.SelfImprovement, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(mixedScript(pair.selfCheck), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
    Button(onClick = { onAction(SoundLabAction.Lab(LabEvent.Done)) }, modifier = Modifier.fillMaxWidth().padding(top = 18.dp).heightIn(min = 54.dp)) {
        Text(stringResource(R.string.soundlab_done_pair))
    }
}

@Composable
private fun CompareRow(title: String, meta: String, playing: Boolean, enabled: Boolean, playCd: String, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                .clickable(enabled = enabled, role = Role.Button, onClick = onPlay)
                .semantics { contentDescription = playCd },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                tint = if (playing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(mixedScript(title), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecordButton(take: Take, onAction: (SoundLabAction) -> Unit) {
    val recording = take as? Take.Recording
    val label = when {
        recording != null -> stringResource(R.string.soundlab_recording, clock(recording.elapsedMs))
        take is Take.Ready -> stringResource(R.string.soundlab_record_again)
        else -> stringResource(R.string.soundlab_record)
    }
    val cd = if (recording != null) stringResource(R.string.soundlab_stop_recording_cd) else label
    // Announced once each (polite): "Recording started", then "Your take is ready". The ticking clock isn't.
    val status = when (take) {
        is Take.Recording -> stringResource(R.string.recording_started)
        is Take.Ready -> stringResource(R.string.soundlab_take_ready)
        else -> null
    }
    if (status != null) Text("", Modifier.testTag(TAG_SOUNDLAB_STATUS).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = status })
    Column(
        Modifier.padding(top = 4.dp, bottom = 6.dp).clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button) { onAction(if (recording != null) SoundLabAction.StopRecord else SoundLabAction.StartRecord) }
            .clearAndSetSemantics { contentDescription = cd; role = Role.Button; onClick { onAction(if (recording != null) SoundLabAction.StopRecord else SoundLabAction.StartRecord); true } }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .background(if (recording != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.5.dp, VaakTheme.colors.lineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (recording != null) Icons.Rounded.Stop else Icons.Rounded.Mic, null,
                tint = if (recording != null) VaakTheme.colors.accent else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Pair words: the serif for English, Noto Sans Devanagari (line height ≥ 1.6) for Hindi. */
@Composable
private fun wordStyle(lang: Lang, size: Float): TextStyle = if (lang == Lang.HI) {
    VaakTheme.type.hiHeadline.copy(fontSize = size.sp, lineHeight = (size * 1.6f).sp, fontWeight = FontWeight.SemiBold)
} else {
    TextStyle(fontFamily = FrauncesText, fontWeight = FontWeight.SemiBold, fontSize = size.sp, lineHeight = (size * 1.2f).sp)
}
