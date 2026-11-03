package io.github.ukpratik.mukhar.app.feature.words

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.data.repo.WordItem
import io.github.ukpratik.mukhar.data.repo.WordStatus
import io.github.ukpratik.mukhar.data.tts.TtsSupport
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.text.Normalizer
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "6 Nov" (design copy). */
fun shortDay(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK))

fun posRes(pos: String?, full: Boolean = false, hindi: Boolean = false): Int? = when (pos) {
    "noun" -> if (hindi) R.string.pos_noun_hi else if (full) R.string.pos_noun_full else R.string.pos_noun
    "verb" -> if (hindi) R.string.pos_verb_hi else if (full) R.string.pos_verb_full else R.string.pos_verb
    "adjective" -> if (hindi) R.string.pos_adjective_hi else if (full) R.string.pos_adjective_full else R.string.pos_adjective
    "adverb" -> if (hindi) R.string.pos_adverb_hi else if (full) R.string.pos_adverb_full else R.string.pos_adverb
    "phrase" -> if (hindi) R.string.pos_phrase_hi else if (full) R.string.pos_phrase_full else R.string.pos_phrase
    else -> null
}

fun registerRes(register: String): Int = when (register) {
    "formal" -> R.string.register_formal
    "power" -> R.string.register_power
    else -> R.string.register_casual
}

fun goalTrackRes(key: String): Int? = when (key) {
    "professional" -> R.string.track_professional
    "interviews" -> R.string.track_interviews
    "public_speaking" -> R.string.track_public_speaking
    "content_creation" -> R.string.track_content_creation
    "word_power" -> R.string.track_word_power
    "everyday" -> R.string.track_everyday
    else -> null
}

/** "verb · power" (register always as text, never colour only). */
@Composable
fun posAndRegister(word: WordItem): String {
    val pos = posRes(word.pos)?.let { stringResource(it) }
    val reg = stringResource(registerRes(word.register))
    return listOfNotNull(pos, reg).joinToString(" · ")
}

@Composable
fun statusLabel(word: WordItem): String = when (word.status) {
    WordStatus.NEW -> stringResource(R.string.word_status_new)
    WordStatus.LEARNING -> stringResource(R.string.word_status_learning)
    WordStatus.KNOWN -> stringResource(R.string.word_status_known)
    WordStatus.SAVED_FOR_LATER -> stringResource(R.string.word_status_saved, word.releaseDay?.let(::shortDay).orEmpty())
}

/** The S14 status chip: New (saffron), Learning (brand), Known (glow, with a check), Saved for … (muted). */
@Composable
fun StatusChip(word: WordItem, modifier: Modifier = Modifier) {
    val (bg, fg) = when (word.status) {
        WordStatus.NEW -> MukharTheme.colors.accentTint to MukharTheme.colors.accentInk
        WordStatus.KNOWN -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        WordStatus.LEARNING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        WordStatus.SAVED_FOR_LATER -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        statusLabel(word),
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * The 48 dp speaker slot is reserved from first layout so async TTS init never shifts the row; with no voice it
 * stays empty (hidden, not greyed, S14 notes).
 */
@Composable
fun SpeakerSlot(visible: Boolean, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(MukharSpace.touch)) {
        if (visible) {
            FilledTonalIconButton(onClick = onClick, modifier = Modifier.testTag(TAG_SPEAKER)) {
                Icon(Icons.Rounded.VolumeUp, contentDescription = description)
            }
        }
    }
}

@Composable
fun hearDescription(text: String, lang: Lang): String =
    stringResource(if (lang == Lang.HI) R.string.word_hear_hi_cd else R.string.word_hear_en_cd, text)

/** S14 state 4: shown once per language when no voice is installed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceTip(lang: Lang, onOpenSettings: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    val name = stringResource(if (lang == Lang.HI) R.string.lang_hi else R.string.lang_en)
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite }
            .testTag(TAG_VOICE_TIP),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column {
            Text(stringResource(R.string.voice_tip_title, name), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.voice_tip_body, name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            // The two buttons wrap onto two lines at large text instead of clipping.
            FlowRow {
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.voice_tip_open)) }
                TextButton(onClick = onNotNow) { Text(stringResource(R.string.voice_tip_not_now), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** "Open voice settings": install TTS data, falling back to the system TTS settings when the OEM engine ignores it. */
fun Context.openVoiceSettings() {
    try {
        startActivity(TtsSupport.installVoiceIntent())
    } catch (_: ActivityNotFoundException) {
        runCatching { startActivity(Intent(TtsSupport.SYSTEM_TTS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** "< 1 min", "10 min", "5 h", "4 d" on the grade buttons, and a spoken form for TalkBack. */
@Composable
fun intervalLabel(d: Duration, spoken: Boolean = false): String {
    @Composable
    fun n(plural: Int, short: Int, value: Int) =
        if (spoken) pluralStringResource(plural, value, value) else stringResource(short, value)
    return when {
        d < Duration.ofMinutes(1) -> stringResource(if (spoken) R.string.interval_under_min_cd else R.string.interval_under_min)
        d < Duration.ofHours(1) -> n(R.plurals.interval_min_cd, R.string.interval_min, d.toMinutes().toInt())
        d < Duration.ofDays(1) -> n(R.plurals.interval_hours_cd, R.string.interval_hours, d.toHours().toInt())
        else -> n(R.plurals.interval_days_cd, R.string.interval_days, d.toDays().toInt())
    }
}

/**
 * Word-level diff for "You said → Say instead" and the corrected sentence: removed words struck through, added
 * words highlighted, so the change never relies on colour alone (S13 state 4, S15 state 2).
 */
object LineDiff {
    data class Part(val text: String, val kind: Kind)
    enum class Kind { SAME, REMOVED, ADDED }

    fun diff(before: String, after: String): Pair<List<Part>, List<Part>> {
        val a = before.split(' ').filter(String::isNotEmpty)
        val b = after.split(' ').filter(String::isNotEmpty)
        val na = a.map(Normalizer::norm)
        val nb = b.map(Normalizer::norm)
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            lcs[i][j] = if (na[i] == nb[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val left = mutableListOf<Part>()
        val right = mutableListOf<Part>()
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && na[i] == nb[j] -> { left += Part(a[i++], Kind.SAME); right += Part(b[j++], Kind.SAME) }
                j < b.size && (i == a.size || lcs[i][j + 1] >= lcs[i + 1][j]) -> right += Part(b[j++], Kind.ADDED)
                else -> left += Part(a[i++], Kind.REMOVED)
            }
        }
        return left to right
    }
}

@Composable
fun diffText(parts: List<LineDiff.Part>): AnnotatedString {
    val mark = MukharTheme.colors.glowMark
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return buildAnnotatedString {
        append("“")
        parts.forEachIndexed { i, p ->
            if (i > 0) append(' ')
            when (p.kind) {
                LineDiff.Kind.SAME -> append(p.text)
                LineDiff.Kind.REMOVED -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = muted, fontWeight = FontWeight.Normal)) { append(p.text) }
                LineDiff.Kind.ADDED -> withStyle(SpanStyle(background = mark, fontWeight = FontWeight.Bold)) { append(p.text) }
            }
        }
        append("”")
    }
}

const val TAG_SPEAKER = "speaker"
const val TAG_VOICE_TIP = "voice_tip"
