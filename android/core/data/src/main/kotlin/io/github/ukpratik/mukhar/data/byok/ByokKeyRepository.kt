package io.github.ukpratik.mukhar.data.byok

import kotlinx.coroutines.flow.Flow

/** "Your keys" (S19 → Analysis): save, check, replace and remove the user's own keys (ADR-008). */
class ByokKeyRepository(
    private val vault: KeyVault,
    private val deepgram: DeepgramClient,
    private val sarvam: SarvamClient,
    private val anthropic: AnthropicClient,
) {
    val keys: Flow<Map<ByokProvider, KeyInfo>> = vault.keys

    /** Saves (or replaces) the key, then checks it with the vendor (no or tiny spend). */
    suspend fun saveAndCheck(provider: ByokProvider, key: String): KeyCheck {
        vault.save(provider, key)
        return check(provider) ?: KeyCheck.ERROR
    }

    /** Checks the stored key; null when there is none. The answer is remembered for the "Checked today" line. */
    suspend fun check(provider: ByokProvider): KeyCheck? {
        val key = vault.key(provider) ?: return null
        val result = when (provider) {
            ByokProvider.DEEPGRAM -> deepgram.check(key)
            ByokProvider.SARVAM -> sarvam.check(key)
            ByokProvider.ANTHROPIC -> anthropic.check(key)
        }
        vault.recordCheck(provider, result)
        return result
    }

    suspend fun remove(provider: ByokProvider) = vault.remove(provider)
}
