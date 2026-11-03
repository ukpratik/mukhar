package io.github.ukpratik.mukhar.domain

import io.github.ukpratik.mukhar.domain.model.Word
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File

/** Loads `shared/test-vectors/<name>` (path passed by Gradle as the `mukhar.testVectors` system property). */
object TestVectors {
    private val dir: File by lazy {
        val path = System.getProperty("mukhar.testVectors")
            ?: error("mukhar.testVectors system property not set (run through Gradle)")
        File(path).also { check(it.isDirectory) { "Missing test vectors at $it" } }
    }

    fun load(name: String): JsonObject = Json.parseToJsonElement(dir.resolve(name).readText()) as JsonObject

    fun words(array: JsonElement): List<Word> = array.jsonArray.map {
        val t = it as JsonArray
        Word(t[0].jsonPrimitive.content, t[1].jsonPrimitive.long, t[2].jsonPrimitive.long, t[3].jsonPrimitive.double)
    }

    fun strings(array: JsonElement): List<String> = array.jsonArray.map { it.jsonPrimitive.content }
}
