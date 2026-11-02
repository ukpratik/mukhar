package com.vaak.ui.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.intl.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.vaak.ui.R
import com.vaak.ui.theme.Manrope
import com.vaak.ui.theme.NotoDeva
import com.vaak.ui.theme.VaakTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** NFR-012: bundled fonts and mixed English + Hindi text (M6). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MixedScriptTest {
    @get:Rule
    val compose = createComposeRule()

    private val mixed = "Say मोड़ (turn), then मोर (peacock): basically मेरा office."

    @Test
    fun runsCoverDevanagariWordsAndBridgeSpacesOnly() {
        assertThat(MixedScript.devanagariRuns("hello")).isEmpty()
        assertThat(MixedScript.devanagariRuns("मेरा office बहुत stressful था")).containsExactly(0 until 4, 12 until 16, 27 until 29).inOrder()
        // Two Devanagari words separated by a space are one run; a trailing space is not included.
        assertThat(MixedScript.devanagariRuns("सास और साँस ok")).containsExactly(0 until 11)
    }

    @Test
    fun clustersAreNeverSplit() {
        // Nukta (ड + ़), chandrabindu, a ZWJ and a virama all stay inside the run.
        val nukta = "पड़ी"
        assertThat(MixedScript.devanagariRuns("x $nukta y")).containsExactly(2 until 2 + nukta.length)
        val zwj = "क्‍ष"
        assertThat(MixedScript.devanagariRuns("a $zwj b")).containsExactly(2 until 2 + zwj.length)
        val run = MixedScript.devanagariRuns("पूँछ").single()
        assertThat(run).isEqualTo(0 until 4)
    }

    @Test
    fun annotatedStringStylesOnlyTheDevanagariRunsWithTheHindiLocale() {
        val s = mixedScript("Say मोड़ then मोर")
        assertThat(s.text).isEqualTo("Say मोड़ then मोर")
        val styles = s.spanStyles
        assertThat(styles).hasSize(2)
        assertThat(styles.map { s.text.substring(it.start, it.end) }).containsExactly("मोड़", "मोर").inOrder()
        assertThat(styles.all { it.item.fontFamily == NotoDeva && it.item.localeList == LocaleList("hi") }).isTrue()
    }

    @Test
    fun rendersMixedEnglishAndHindiInVaakTheme() {
        compose.setContent {
            VaakTheme {
                Text(mixedScript(mixed), style = MaterialTheme.typography.bodyLarge)
                Text("मेरा office बहुत stressful था", style = VaakTheme.type.hiBodyLarge)
            }
        }
        compose.onNodeWithText(mixed).assertIsDisplayed()
        val node = compose.onNodeWithText("मेरा office बहुत stressful था").assertIsDisplayed().fetchSemanticsNode()
        assertThat(node.config.getOrNull(SemanticsProperties.Text)?.single()?.text).isEqualTo("मेरा office बहुत stressful था")
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        assertThat(res.getResourceTypeName(R.font.noto_sans_devanagari_variable)).isEqualTo("font")
        assertThat(res.getResourceTypeName(R.font.manrope_variable)).isEqualTo("font")
    }

    @Test
    fun typeScaleUsesTheBundledFamilies() {
        compose.setContent {
            VaakTheme {
                assertThat(MaterialTheme.typography.bodyLarge.fontFamily).isEqualTo(Manrope)
                assertThat(MaterialTheme.typography.labelLarge.fontFamily).isEqualTo(Manrope)
                assertThat(VaakTheme.type.hiBodyLarge.fontFamily).isEqualTo(NotoDeva)
                assertThat(VaakTheme.type.hiHeadline.localeList).isEqualTo(LocaleList("hi"))
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun bundledVariableFontsResolveAtEveryWeightUsed() {
        val resolver = createFontFamilyResolver(ApplicationProvider.getApplicationContext())
        for (w in listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)) {
            assertThat(resolver.resolve(NotoDeva, w).value).isNotNull()
            assertThat(resolver.resolve(Manrope, w).value).isNotNull()
        }
        assertThat(resolver.resolve(Manrope, FontWeight.ExtraBold).value).isNotNull()
    }
}
