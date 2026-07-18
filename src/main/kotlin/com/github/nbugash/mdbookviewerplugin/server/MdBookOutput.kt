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

/**
 * Pulls the actionable lines out of mdBook's captured output so a serve failure surfaces *why*
 * it failed (a missing preprocessor/backend, a broken `SUMMARY.md`, etc.) instead of a bare exit
 * code. Prefers the `ERROR` / `Caused by:` chain mdBook prints; falls back to the last few
 * non-blank lines. Returns "" when there is nothing useful, so callers can omit the detail.
 */
fun extractMdBookError(output: String): String {
    val lines = output.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val errorLines = lines.filter {
        it.startsWith("ERROR", ignoreCase = true) || it.startsWith("Caused by", ignoreCase = true)
    }
    val chosen = errorLines.ifEmpty { lines.takeLast(4) }
    return chosen.joinToString("\n").take(600)
}
