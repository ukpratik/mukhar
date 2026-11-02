package com.vaak.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-074 (M6): VaakTheme follows the system's dark setting by default, with the dark version of Direction A (D-018). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaakThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private data class Seen(val background: Color, val primary: Color, val accentInk: Color, val accent: Color, val hero: Color)

    private fun seen(): Seen {
        var s: Seen? = null
        compose.setContent {
            VaakTheme {
                val c = MaterialTheme.colorScheme
                s = Seen(c.background, c.primary, VaakTheme.colors.accentInk, VaakTheme.colors.accent, VaakTheme.colors.hero)
            }
        }
        compose.waitForIdle()
        return s!!
    }

    @Test
    @Config(qualifiers = "notnight")
    fun lightWhenTheSystemIsLight() {
        val s = seen()
        assertThat(s.background).isEqualTo(Color(0xFFFAF6EF)) // paper
        assertThat(s.primary).isEqualTo(Color(0xFF2B2766)) // brand
        assertThat(s.accentInk).isEqualTo(Color(0xFF8A5100))
    }

    @Test
    @Config(qualifiers = "night")
    fun darkWhenTheSystemIsDark() {
        val s = seen()
        assertThat(s.background).isEqualTo(Color(0xFF15131F)) // deep ink paper
        assertThat(s.primary).isEqualTo(Color(0xFFC9C5F5)) // lavender brand
        assertThat(s.accentInk).isEqualTo(Color(0xFFF5C276))
        // Saffron and the hero card stay the same in both themes (design-system §1).
        assertThat(s.accent).isEqualTo(Color(0xFFF2A531))
        assertThat(s.hero).isEqualTo(Color(0xFF2B2766))
    }
}
