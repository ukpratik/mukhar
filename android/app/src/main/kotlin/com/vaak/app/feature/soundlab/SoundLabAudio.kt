package com.vaak.app.feature.soundlab

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.vaak.data.tts.Speaker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Loads the pair pack and the list of recorded clips. */
interface PairSource {
    suspend fun load(): LoadedPairs
}

data class LoadedPairs(val pack: PairPack, val registry: ClipRegistry)

class AssetPairSource @Inject constructor(@param:ApplicationContext private val context: Context) : PairSource {
    override suspend fun load(): LoadedPairs = withContext(Dispatchers.IO) {
        val pack = context.assets.open(PAIRS_JSON).bufferedReader().use { PairParser.parse(it.readText()) }
        val files = context.assets.list(pack.clipsDir)?.toList().orEmpty()
        LoadedPairs(pack, ClipRegistry(pack.clipsDir, files))
    }

    companion object {
        const val PAIRS_JSON = "pairs.json"
    }
}

/** One clip at a time (S22 notes): starting one stops the last. [playing] is the key of the clip now playing. */
interface ClipPlayer {
    val playing: StateFlow<String?>
    fun play(key: String, source: ClipSource)
    fun playFile(key: String, path: String)
    fun stop()
    fun release()
}

/** MediaPlayer for bundled clips and the user's take; the platform TTS ([Speaker]) for preview clips. */
class AndroidClipPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val speaker: Speaker,
) : ClipPlayer {
    private val _playing = MutableStateFlow<String?>(null)
    override val playing: StateFlow<String?> = _playing.asStateFlow()
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    override fun play(key: String, source: ClipSource) {
        stop()
        when (source) {
            is ClipSource.Recorded -> start(key) { mp ->
                context.assets.openFd(source.assetPath).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            }
            is ClipSource.Preview -> {
                speaker.speak(source.text, source.lang)
                // TextToSpeech has no per-utterance callback through Speaker; show "playing" for about the word's length.
                _playing.value = key
                main.postDelayed({ if (_playing.value == key) _playing.value = null }, TTS_BASE_MS + TTS_PER_CHAR_MS * source.text.length)
            }
        }
    }

    override fun playFile(key: String, path: String) {
        stop()
        start(key) { it.setDataSource(path) }
    }

    private fun start(key: String, source: (MediaPlayer) -> Unit) {
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            source(mp)
            mp.setOnCompletionListener { done(it, key) }
            mp.setOnErrorListener { p, _, _ -> done(p, key); true }
            mp.prepare()
            mp.start()
            player = mp
            _playing.value = key
        } catch (e: Exception) {
            // No PII: only the failure type (clip ids are content, not user data, but keep logs minimal anyway).
            Log.w(TAG, "clip playback failed: ${e.javaClass.simpleName}")
            mp.release()
            _playing.value = null
        }
    }

    private fun done(mp: MediaPlayer, key: String) {
        if (player === mp) player = null
        mp.release()
        if (_playing.value == key) _playing.value = null
    }

    override fun stop() {
        main.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        _playing.value = null
    }

    override fun release() = stop()

    private companion object {
        const val TAG = "SoundLab"
        const val TTS_BASE_MS = 700L
        const val TTS_PER_CHAR_MS = 90L
    }
}

/** Where takes go: the cache, outside `recordings/` (crash recovery scans that folder), cleared on leaving a pair. */
class SoundLabFiles(val dir: File) {
    fun newTake(id: String): File = File(dir.apply { mkdirs() }, "take-$id.aac")

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}

/** M6: Sound Lab lite wiring. */
@Module
@InstallIn(ViewModelComponent::class)
abstract class SoundLabModule {
    @Binds
    abstract fun pairSource(impl: AssetPairSource): PairSource

    @Binds
    abstract fun clipPlayer(impl: AndroidClipPlayer): ClipPlayer
}

@Module
@InstallIn(SingletonComponent::class)
object SoundLabFilesModule {
    @Provides
    @Singleton
    fun soundLabFiles(@ApplicationContext context: Context): SoundLabFiles = SoundLabFiles(File(context.cacheDir, "soundlab"))
}
