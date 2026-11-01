package com.vaak.domain.model

/** Practice languages in R1. Hinglish talks are [HI]. */
enum class Lang(val code: String) {
    EN("en"),
    HI("hi");

    companion object {
        fun fromCode(code: String): Lang = entries.firstOrNull { it.code == code }
            ?: throw IllegalArgumentException("Unknown language code: $code")
    }
}
