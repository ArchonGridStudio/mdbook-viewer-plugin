package com.github.nbugash.mdbookviewerplugin.server

private val URL_REGEX = Regex("""https?://\S+""")

/**
 * Extracts the HTTP(S) URL that `mdbook serve` reports it is serving on.
 *
 * mdBook logs a line such as `Serving on: http://localhost:3000`. The live-reload line
 * uses a `ws://` scheme, which is intentionally not matched. Returns null when the line
 * carries no HTTP(S) URL.
 */
fun parseServingUrl(line: String): String? {
    val match = URL_REGEX.find(line) ?: return null
    return match.value.trimEnd('.', ',', ';', ')', ']', '}', '"', '\'')
}
