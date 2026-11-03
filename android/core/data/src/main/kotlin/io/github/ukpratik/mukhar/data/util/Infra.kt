package io.github.ukpratik.mukhar.data.util

import android.util.Log
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Qualifier

/** Injected so tests get deterministic ids (LLD §5). */
fun interface IdGenerator {
    fun newId(): String

    companion object {
        val Random = IdGenerator { UUID.randomUUID().toString() }
    }
}

/** App-lifetime coroutine scope for the foreground analysis path (LLD §4.9). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Whether any activity is started; the worker only notifies when the app is in the background (US-072). */
object AppVisibility {
    private val started = AtomicInteger(0)
    val isForeground: Boolean get() = started.get() > 0
    fun onStart() = started.incrementAndGet()
    fun onStop() = started.updateAndGet { maxOf(0, it - 1) }
}

/** Log wrapper (LLD §6): codes and ids only. Never transcript text, words, names, audio paths or tokens. */
object MukharLog {
    private const val TAG = "Mukhar"
    fun i(message: String) {
        if (Log.isLoggable(TAG, Log.INFO)) Log.i(TAG, message)
    }
    fun w(message: String) {
        if (Log.isLoggable(TAG, Log.WARN)) Log.w(TAG, message)
    }
}
