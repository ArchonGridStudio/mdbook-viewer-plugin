package com.github.nbugash.mdbookviewerplugin.detect

private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

/**
 * Normalizes a user-entered URL so it always carries a scheme. `localhost:3000` becomes
 * `http://localhost:3000`; a URL that already has a scheme is returned trimmed but unchanged.
 * Blank input returns an empty string for the caller to handle.
 */
fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return if (SCHEME_REGEX.containsMatchIn(trimmed)) trimmed else "http://$trimmed"
}
