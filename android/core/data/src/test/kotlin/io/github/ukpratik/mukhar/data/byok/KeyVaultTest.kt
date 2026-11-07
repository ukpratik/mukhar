package io.github.ukpratik.mukhar.data.byok

import androidx.datastore.core.DataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** ADR-008 key storage: Tink AEAD per provider (provider id as associated data), last-4 only, wiped with the data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyVaultTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("byok").toFile()
    private val file = File(dir, ByokFileSerializer.FILE)
    private val store = DataStoreFactory.create(ByokFileSerializer, scope = scope) { file }
    private val cipher = TestCipher()
    private var now = 1_000L
    private val vault = KeyVault(store, cipher) { now }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun savesEncryptedAndReadsBack() = runBlocking<Unit> {
        val secret = "sk-ant-api03-SECRET-abcd1234"
        vault.save(ByokProvider.ANTHROPIC, "  $secret \n")
        assertThat(vault.key(ByokProvider.ANTHROPIC)).isEqualTo(secret)
        val info = vault.keys.first().getValue(ByokProvider.ANTHROPIC)
        assertThat(info.last4).isEqualTo("1234")
        assertThat(info.savedAtMs).isEqualTo(1_000L)
        assertThat(info.check).isNull()
        // The file never holds the key or a recognisable part of it.
        val onDisk = file.readText()
        assertThat(onDisk).doesNotContain("SECRET")
        assertThat(onDisk).doesNotContain("sk-ant")
        assertThat(vault.key(ByokProvider.DEEPGRAM)).isNull()
    }

    @Test
    fun aCiphertextMovedToAnotherProviderDoesNotDecryptAndIsDropped() = runBlocking<Unit> {
        vault.save(ByokProvider.DEEPGRAM, "dg-key-0001")
        store.updateData { f -> f.copy(keys = f.keys + (ByokProvider.SARVAM.id to f.keys.getValue(ByokProvider.DEEPGRAM.id))) }
        assertThat(vault.key(ByokProvider.SARVAM)).isNull()
        assertThat(vault.keys.first()).doesNotContainKey(ByokProvider.SARVAM)
        assertThat(vault.key(ByokProvider.DEEPGRAM)).isEqualTo("dg-key-0001")
    }

    @Test
    fun checksAreRememberedAndRemoveForgetsTheKey() = runBlocking<Unit> {
        vault.save(ByokProvider.DEEPGRAM, "dg-key-0001")
        now = 5_000L
        vault.recordCheck(ByokProvider.DEEPGRAM, KeyCheck.LIMITED)
        val info = vault.keys.first().getValue(ByokProvider.DEEPGRAM)
        assertThat(info.check).isEqualTo(KeyCheck.LIMITED)
        assertThat(info.checkedAtMs).isEqualTo(5_000L)
        vault.remove(ByokProvider.DEEPGRAM)
        assertThat(vault.key(ByokProvider.DEEPGRAM)).isNull()
    }

    @Test
    fun replacingAKeyKeepsOneEntry() = runBlocking<Unit> {
        vault.save(ByokProvider.SARVAM, "first-1111")
        vault.save(ByokProvider.SARVAM, "second-2222")
        assertThat(vault.key(ByokProvider.SARVAM)).isEqualTo("second-2222")
        assertThat(vault.keys.first().getValue(ByokProvider.SARVAM).last4).isEqualTo("2222")
    }

    @Test
    fun wipeDeletesEveryKeyAndTheKeyset() = runBlocking<Unit> {
        vault.save(ByokProvider.DEEPGRAM, "dg-key-0001")
        vault.save(ByokProvider.ANTHROPIC, "an-key-0002")
        vault.wipe()
        assertThat(vault.keys.first()).isEmpty()
        assertThat(cipher.destroyed).isTrue()
    }

    @Test
    fun blankKeysAreRefused() = runBlocking<Unit> {
        val r = runCatching { vault.save(ByokProvider.DEEPGRAM, "   ") }
        assertThat(r.isFailure).isTrue()
    }
}
