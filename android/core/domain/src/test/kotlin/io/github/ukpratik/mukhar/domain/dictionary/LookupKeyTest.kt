package io.github.ukpratik.mukhar.domain.dictionary

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LookupKeyTest {
    @Test
    fun romanisedHindiMeetsWhicheverSpellingIsTyped() {
        assertThat(LookupKey.loose("khush")).isEqualTo(LookupKey.loose("xuś"))
        assertThat(LookupKey.loose("anjaam")).isEqualTo(LookupKey.loose("añjām"))
        assertThat(LookupKey.loose("Zindagi")).isEqualTo(LookupKey.loose("jindagī"))
        assertThat(LookupKey.loose("vishwas")).isEqualTo(LookupKey.loose("viśvās"))
    }

    @Test
    fun devanagariIgnoresNukta() {
        assertThat(LookupKey.exact("ख़ुश")).isEqualTo(LookupKey.exact("खुश"))
        assertThat(LookupKey.isDevanagari("खुश")).isTrue()
        assertThat(LookupKey.isDevanagari("khush")).isFalse()
    }

    @Test
    fun englishStemsCoverRegularEndings() {
        assertThat(LookupKey.englishStems("running")).contains("run")
        assertThat(LookupKey.englishStems("studies")).contains("study")
        assertThat(LookupKey.englishStems("making")).contains("make")
        assertThat(LookupKey.englishStems("happier")).contains("happy")
        assertThat(LookupKey.englishStems("decisions")).contains("decision")
        assertThat(LookupKey.englishStems("stopped")).contains("stop")
        assertThat(LookupKey.englishStems("quickly")).contains("quick")
        assertThat(LookupKey.englishStems("go")).isEmpty()
    }

    @Test
    fun exactBeforeFormsBeforePrefixesAndOwnWordsFirst() {
        val c = { id: String, k: MatchKind, rank: Int, curated: Boolean -> LookupRanking.Candidate(id, k, rank, curated, id.length) }
        val order = LookupRanking.order(
            listOf(
                c("en:happiness", MatchKind.PREFIX, 900, false),
                c("en:happen", MatchKind.PREFIX, 300, false),
                c("en:happy", MatchKind.EXACT, 296, false),
                c("en:hap", MatchKind.PREFIX, 30000, true),
                c("en:happen", MatchKind.STEM, 300, false),
            ),
            limit = 10,
        )
        assertThat(order).containsExactly("en:happy", "en:happen", "en:hap", "en:happiness").inOrder()
    }
}
