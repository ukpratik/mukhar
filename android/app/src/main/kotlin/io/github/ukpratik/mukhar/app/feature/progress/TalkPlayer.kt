package io.github.ukpratik.mukhar.app.feature.progress

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.io.File

/**
 * Plays the kept baseline and latest recordings on the phone (US-051). One at a time: starting one stops the other
 * (S16 notes). Files never leave the device.
 */
@Stable
class TalkPlayer {
    var playing by mutableStateOf<File?>(null)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    private var player: MediaPlayer? = null

    fun toggle(file: File) {
        if (playing == file) {
            stop()
            return
        }
        stop()
        val mp = runCatching {
            MediaPlayer().apply {
                setDataSource(file.path)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
        }.getOrNull() ?: return
        player = mp
        playing = file
    }

    fun tick() {
        positionMs = runCatching { player?.currentPosition?.toLong() }.getOrNull() ?: 0L
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
        playing = null
        positionMs = 0
    }
}

@Composable
fun rememberTalkPlayer(): TalkPlayer {
    val player = remember { TalkPlayer() }
    DisposableEffect(player) { onDispose { player.stop() } }
    LaunchedEffect(player.playing) {
        while (player.playing != null) {
            player.tick()
            delay(TICK_MS)
        }
    }
    return player
}

private const val TICK_MS = 250L
