package com.github.nbugash.mdbookviewerplugin

import com.github.nbugash.mdbookviewerplugin.server.isAuthFailure
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the "a private repo is not served/rendered without a valid token" behavior.
 *
 * When `git clone` fails for an authentication reason, [isAuthFailure] flags it; the serve
 * service then throws instead of returning a URL, so the panel never calls loadUrl and the
 * book is not rendered. These tests pin the detection that drives that non-render path.
 */
class CloneDiagnosticsTest {

    @Test
    fun `flags GitHub invalid-token rejection as an auth failure`() {
        val output = "remote: Invalid username or token. Password authentication is not supported for Git operations.\n" +
            "fatal: Authentication failed for 'https://github.com/ArchonGridStudio/InfraWars.git/'"
        assertTrue(isAuthFailure(output))
    }

    @Test
    fun `flags a missing credential (prompts disabled) as an auth failure`() {
        // No token at all: with terminal prompts disabled, git cannot obtain a username.
        val output = "fatal: could not read Username for 'https://github.com': terminal prompts disabled"
        assertTrue(isAuthFailure(output))
    }

    @Test
    fun `flags an SSH permission-denied as an auth failure`() {
        val output = "git@github.com: Permission denied (publickey).\nfatal: Could not read from remote repository."
        assertTrue(isAuthFailure(output))
    }

    @Test
    fun `does not flag a network error as an auth failure`() {
        assertFalse(isAuthFailure("fatal: unable to access 'https://github.com/x/y.git/': Could not resolve host: github.com"))
    }

    @Test
    fun `does not flag empty output`() {
        assertFalse(isAuthFailure(""))
    }
}
