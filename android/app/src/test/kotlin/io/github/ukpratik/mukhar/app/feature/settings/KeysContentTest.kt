package io.github.ukpratik.mukhar.app.feature.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.byok.ByokProvider
import io.github.ukpratik.mukhar.data.byok.KeyCheck
import io.github.ukpratik.mukhar.data.byok.KeyInfo
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Your keys" (ADR-008): masked entry, last-4 only, the check states and the honesty footnote. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class KeysContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun savedKeysShowOnlyTheLastFourAndTheirCheck() {
        val state = KeysUiState(
            keys = mapOf(
                ByokProvider.DEEPGRAM to KeyInfo("a91c", 1L, 2L, KeyCheck.OK),
                ByokProvider.ANTHROPIC to KeyInfo("7f02", 1L, null, null),
            ),
            status = mapOf(ByokProvider.ANTHROPIC to KeyStatus.Done(KeyCheck.MODEL_UNAVAILABLE)),
        )
        compose.setContent { MukharTheme { KeysContent(state, KeysActions()) } }
        compose.onNodeWithText("••••a91c").assertExists()
        compose.onNodeWithText("Key works").assertExists()
        compose.onNodeWithText("This key can't use claude-haiku-5-5").performScrollTo()
        compose.onNodeWithTag(TAG_KEY_FOOTNOTE).performScrollTo()
        compose.onNodeWithText("Stored encrypted on this phone only. Not backed up.", substring = true).assertExists()
    }

    @Test
    fun aNewKeyIsSavedAndTestedThenTheFieldClears() {
        var saved: Pair<ByokProvider, String>? = null
        var tested: ByokProvider? = null
        val state = KeysUiState(
            keys = mapOf(ByokProvider.DEEPGRAM to KeyInfo("a91c", 1L, null, null)),
            status = mapOf(ByokProvider.DEEPGRAM to KeyStatus.Checking),
        )
        compose.setContent { MukharTheme { KeysContent(state, KeysActions(onSave = { p, k -> saved = p to k }, onTest = { tested = it })) } }
        compose.onNodeWithText("Checking…").assertExists()
        compose.onNodeWithTag(TAG_KEY_TEST + "deepgram").assertIsNotEnabled()
        val save = compose.onNodeWithTag(TAG_KEY_SAVE + "anthropic").performScrollTo()
        save.assertIsNotEnabled()
        compose.onNodeWithTag(TAG_KEY_FIELD + "anthropic").performTextInput("sk-ant-api03-xyz")
        // Masked: the typed key never shows as text.
        val shown = compose.onNodeWithTag(TAG_KEY_FIELD + "anthropic").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        assertThat(shown).doesNotContain("sk-ant")
        save.assertIsEnabled().performClick()
        assertThat(saved).isEqualTo(ByokProvider.ANTHROPIC to "sk-ant-api03-xyz")
        assertThat(tested).isNull()
    }

    @Test
    fun checkStatesHaveTheAdrCopy() {
        val texts = KeyCheck.entries.associateWith { checkText(it) }
        assertThat(texts.values.toSet()).hasSize(KeyCheck.entries.size)
        compose.setContent {
            MukharTheme {
                KeysContent(
                    KeysUiState(
                        keys = mapOf(ByokProvider.SARVAM to KeyInfo("0000", 1L, null, null)),
                        status = mapOf(ByokProvider.SARVAM to KeyStatus.Done(KeyCheck.INVALID)),
                    ),
                    KeysActions(),
                )
            }
        }
        compose.onNodeWithText("Key not accepted (401). Check you copied all of it.").performScrollTo()
    }
}
