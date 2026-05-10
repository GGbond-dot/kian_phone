package com.kian.khup.common.util

import java.security.MessageDigest

fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString(separator = "") { "%02x".format(it) }
}
