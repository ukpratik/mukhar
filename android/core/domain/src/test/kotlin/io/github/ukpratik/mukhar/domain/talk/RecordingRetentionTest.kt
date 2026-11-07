package io.github.ukpratik.mukhar.domain.talk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecordingRetentionTest {
    private val mb = RecordingRetention.MB

    @Test
    fun underTheCapNothingIsDeleted() {
        val recs = listOf(StoredRecording("a", 100 * mb, 1, false), StoredRecording("b", 200 * mb, 2, false))
        assertThat(RecordingRetention.toDelete(recs, RecordingRetention.capBytes(500))).isEmpty()
    }

    @Test
    fun deletesOldestFirstUntilItFits() {
        val recs = listOf(
            StoredRecording("new", 200 * mb, 30, false),
            StoredRecording("old", 200 * mb, 10, false),
            StoredRecording("mid", 200 * mb, 20, false),
        )
        assertThat(RecordingRetention.toDelete(recs, RecordingRetention.capBytes(500))).containsExactly("old")
        assertThat(RecordingRetention.toDelete(recs, RecordingRetention.capBytes(250))).containsExactly("old", "mid").inOrder()
    }

    @Test
    fun protectedRecordingsAreKeptEvenIfOldest() {
        val recs = listOf(
            StoredRecording("pending", 300 * mb, 1, true),
            StoredRecording("a", 200 * mb, 2, false),
            StoredRecording("b", 200 * mb, 3, false),
        )
        assertThat(RecordingRetention.toDelete(recs, RecordingRetention.capBytes(500))).containsExactly("a")
        assertThat(RecordingRetention.toDelete(recs, RecordingRetention.capBytes(400))).containsExactly("a", "b").inOrder()
    }
}
