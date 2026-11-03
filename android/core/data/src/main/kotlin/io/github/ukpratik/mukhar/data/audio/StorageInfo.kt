package io.github.ukpratik.mukhar.data.audio

import android.content.Context
import android.os.StatFs
import java.io.File

/** Free bytes where recordings are written (US-018, D-028). Debug builds can simulate low storage. */
fun interface StorageInfo {
    fun availableBytes(): Long
}

class StatFsStorageInfo(private val context: Context) : StorageInfo {
    override fun availableBytes(): Long = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
}

/** `files/recordings/<id>.aac`. Excluded from backup by omission (backup rules include only Room and settings). */
class RecordingFiles(private val root: File) {
    val dir: File get() = File(root, "recordings").apply { mkdirs() }

    fun fileFor(sessionId: String): File = File(dir, "$sessionId.aac")
}
