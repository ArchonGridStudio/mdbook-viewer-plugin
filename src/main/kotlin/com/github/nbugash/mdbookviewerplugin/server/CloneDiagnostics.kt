package com.github.nbugash.mdbookviewerplugin.server

/**
 * True if [gitOutput] indicates that a clone failed because of authentication — a missing,
 * invalid, or unauthorized credential — as opposed to a network or other error. Used to give
 * a token-specific message and to keep the book from being served without valid access.
 */
private val AUTH_FAILURE_MARKERS = listOf(
    "authentication failed",
    "invalid username or token",
    "could not read username",
    "could not read password",
    "terminal prompts disabled",
    "permission denied (publickey)",
    "403 forbidden",
)

fun isAuthFailure(gitOutput: String): Boolean {
    val output = gitOutput.lowercase()
    return AUTH_FAILURE_MARKERS.any { it in output }
}
