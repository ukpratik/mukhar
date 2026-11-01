package com.vaak.domain.text

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.vaak.domain.TestVectors
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class NormalizerTest(private val name: String, private val input: String, private val expected: String) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = TestVectors.load("normalize.json").getValue("cases").jsonArray.map {
            val o = it.jsonObject
            arrayOf(o.getValue("name").jsonPrimitive.content, o.getValue("in").jsonPrimitive.content, o.getValue("out").jsonPrimitive.content)
        }
    }

    @Test
    fun matchesSharedVector() {
        assertWithMessage(name).that(Normalizer.norm(input)).isEqualTo(expected)
    }

    @Test
    fun isIdempotent() {
        assertThat(Normalizer.norm(Normalizer.norm(input))).isEqualTo(Normalizer.norm(input))
    }
}

class TokenizerTest {
    @Test
    fun splitsNormalisesAndDropsPunctuationOnlyTokens() {
        assertThat(Tokenizer.tokenize("So, um — I think   it's ठीक। !"))
            .containsExactly("so", "um", "i", "think", "its", "ठीक").inOrder()
    }

    @Test
    fun emptyAndBlankGiveNoTokens() {
        assertThat(Tokenizer.tokenize("")).isEmpty()
        assertThat(Tokenizer.tokenize("   \n ")).isEmpty()
    }
}
