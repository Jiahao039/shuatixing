package com.shutixing.app.parser

import com.shutixing.app.domain.model.Option
import java.security.MessageDigest

object StemHasher {
    fun hash(stem: String, options: List<Option>): String {
        val payload = buildString {
            append(stem.trim())
            options.sortedBy { it.key }.forEach { option ->
                append('|').append(option.key).append('.').append(option.text.trim())
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
