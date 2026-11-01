package com.vaak.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vaak.domain.model.Lang
import com.vaak.domain.prompt.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Talk-related user settings (LLD §2.1 DataStore `settings`). Languages and tracks come from onboarding in M3. */
data class TalkPrefs(
    val langs: List<Lang> = listOf(Lang.EN, Lang.HI),
    val tracks: Map<Lang, List<Track>> = DEFAULT_TRACKS,
    val lastLang: Lang = Lang.EN,
    val lastTrack: Map<Lang, Track> = emptyMap(),
    val stutterFriendly: Boolean = false,
    val transliteration: Boolean = false,
    val structureHint: Boolean = true,
    val thinkTime: Boolean = false,
) {
    fun tracksFor(lang: Lang): List<Track> = tracks[lang].orEmpty().ifEmpty { listOf(Track.EVERYDAY) }
    fun trackFor(lang: Lang): Track = lastTrack[lang]?.takeIf { it in tracksFor(lang) } ?: tracksFor(lang).first()

    companion object {
        /** Until onboarding (M3) asks, the tracks drawn in S08. */
        val DEFAULT_TRACKS = mapOf(
            Lang.EN to listOf(Track.PROFESSIONAL, Track.INTERVIEWS, Track.EVERYDAY),
            Lang.HI to listOf(Track.PROFESSIONAL, Track.EVERYDAY, Track.PUBLIC_SPEAKING),
        )
    }
}

interface UserSettings {
    val prefs: Flow<TalkPrefs>
    suspend fun current(): TalkPrefs = prefs.first()
    suspend fun setLang(lang: Lang)
    suspend fun setTrack(lang: Lang, track: Track)
    suspend fun setStutterFriendly(on: Boolean)
    suspend fun setTransliteration(on: Boolean)
    suspend fun setStructureHint(on: Boolean)
    suspend fun setThinkTime(on: Boolean)
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) : UserSettings {
    private val store = context.applicationContext.settingsStore

    override val prefs: Flow<TalkPrefs> = store.data.map { p ->
        TalkPrefs(
            lastLang = p[LAST_LANG]?.let { runCatching { Lang.fromCode(it) }.getOrNull() } ?: Lang.EN,
            lastTrack = Lang.entries.mapNotNull { l -> p[trackKey(l)]?.let { l to Track.fromKey(it) } }.toMap(),
            stutterFriendly = p[STUTTER] ?: false,
            transliteration = p[TRANSLIT] ?: false,
            structureHint = p[STRUCTURE] ?: true,
            thinkTime = p[THINK] ?: false,
        )
    }

    override suspend fun setLang(lang: Lang) = edit { it[LAST_LANG] = lang.code }
    override suspend fun setTrack(lang: Lang, track: Track) = edit { it[trackKey(lang)] = track.key }
    override suspend fun setStutterFriendly(on: Boolean) = edit { it[STUTTER] = on }
    override suspend fun setTransliteration(on: Boolean) = edit { it[TRANSLIT] = on }
    override suspend fun setStructureHint(on: Boolean) = edit { it[STRUCTURE] = on }
    override suspend fun setThinkTime(on: Boolean) = edit { it[THINK] = on }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    private companion object {
        val LAST_LANG = stringPreferencesKey("talk_lang")
        val STUTTER = booleanPreferencesKey("stutter_friendly")
        val TRANSLIT = booleanPreferencesKey("transliteration")
        val STRUCTURE = booleanPreferencesKey("structure_hint")
        val THINK = booleanPreferencesKey("think_time")
        fun trackKey(lang: Lang) = stringPreferencesKey("talk_track_${lang.code}")
    }
}
