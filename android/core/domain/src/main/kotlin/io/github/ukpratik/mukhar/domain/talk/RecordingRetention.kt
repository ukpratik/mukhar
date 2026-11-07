package io.github.ukpratik.mukhar.domain.talk

/** One recording on the phone. [protected] ones (being recorded or still waiting for their analysis) are never auto-deleted. */
data class StoredRecording(val id: String, val bytes: Long, val createdAt: Long, val protected: Boolean)

/**
 * US-080: recordings never fill the phone. When their total goes over the user's limit (default 500 MB), the oldest
 * ones are deleted until the total fits again.
 */
object RecordingRetention {
    const val DEFAULT_CAP_MB = 500
    val CAP_OPTIONS_MB = listOf(100, 250, 500, 1_000, 2_000)
    const val MB = 1024L * 1024L

    fun capBytes(capMb: Int): Long = capMb.toLong() * MB

    /** Ids to delete, oldest first, so the total is at most [capBytes]. Protected recordings count but stay. */
    fun toDelete(recordings: List<StoredRecording>, capBytes: Long): List<String> {
        var total = recordings.sumOf { it.bytes }
        if (total <= capBytes) return emptyList()
        val out = ArrayList<String>()
        for (r in recordings.filter { !it.protected }.sortedWith(compareBy<StoredRecording> { it.createdAt }.thenBy { it.id })) {
            if (total <= capBytes) break
            out += r.id
            total -= r.bytes
        }
        return out
    }
}
