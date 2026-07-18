package com.github.nbugash.mdbookviewerplugin.server

/**
 * True if [cloneOutput] indicates that a fetch failed because of authentication — a missing,
 * invalid, or unauthorized credential — as opposed to a network or other error. Used to give
 * a token-specific message and to keep the book from being served without valid access.
 *
 * The markers cover both the legacy `git` CLI phrasings and the messages JGit raises
 * (`TransportException`/auth errors), so the same classifier serves the pure-JVM fetch path.
 */
private val AUTH_FAILURE_MARKERS = listOf(
    // git CLI
    "authentication failed",
    "invalid username or token",
    "could not read username",
    "could not read password",
    "terminal prompts disabled",
    "permission denied (publickey)",
    "403 forbidden",
    // JGit (org.eclipse.jgit.errors / TransportException)
    "not authorized",
    "authentication is required",
    "auth fail",
    "authentication not supported",
    "credentialsprovider",
    "no credentialsprovider",
    "http 401",
    "http 403",
)

fun isAuthFailure(cloneOutput: String): Boolean {
    val output = cloneOutput.lowercase()
    return AUTH_FAILURE_MARKERS.any { it in output }
}
