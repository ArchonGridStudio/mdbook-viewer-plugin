package com.github.nbugash.mdbookviewerplugin.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Application-wide storage for the private-repo access token, kept in the IDE's encrypted
 * PasswordSafe rather than in plain settings. A personal access token is user-scoped, so it
 * is not tied to a single project.
 */
object MdBookSecrets {

    private val ATTRIBUTES = CredentialAttributes(generateServiceName("mdBook Viewer", "git-access-token"))

    /** Returns the stored token (trimmed), or null if none is set. May block; call off the EDT. */
    fun getToken(): String? = PasswordSafe.instance.getPassword(ATTRIBUTES)?.trim()?.takeIf { it.isNotEmpty() }

    /** Stores [token] trimmed, or clears it when null/blank. Trimming guards against a pasted
     *  trailing newline corrupting the credential passed to git. */
    fun setToken(token: String?) {
        PasswordSafe.instance.setPassword(ATTRIBUTES, token?.trim()?.takeIf { it.isNotEmpty() })
    }
}
