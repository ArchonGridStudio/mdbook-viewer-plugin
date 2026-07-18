package com.github.nbugash.mdbookviewerplugin.server

import com.github.nbugash.mdbookviewerplugin.MdBookBundle
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder
import org.eclipse.jgit.util.FS
import java.io.File
import java.nio.file.Path

/**
 * Fetches book source with pure-JVM [JGit][Git] instead of a `git` subprocess, so no
 * version-control binary needs to be installed (FR-002). Behavioral parity with the previous
 * `git clone --depth 1`: shallow, non-interactive, HTTPS-token and SSH-key auth both work, and
 * the token never appears in a surfaced error.
 */
class SourceFetcher {

    /**
     * Shallow-clone [cloneUrl] into [targetDir]. [token] is applied as HTTPS credentials only;
     * scp-style `git@` URLs authenticate via the user's `~/.ssh` keys.
     *
     * @throws MdBookServeException "notification.cloneAuthFailed" on an auth failure, or
     *   "notification.cloneFailed" on any other transport/repository error.
     * @throws ProcessCanceledException when [indicator] is cancelled mid-fetch.
     */
    fun fetch(cloneUrl: String, token: String?, targetDir: Path, indicator: ProgressIndicator) {
        val clone = Git.cloneRepository()
            .setURI(cloneUrl)
            .setDirectory(targetDir.toFile())
            .setDepth(1)
            .setCloneAllBranches(false)
            .setProgressMonitor(IndicatorMonitor(indicator))

        // Token auth mirrors the old inline credential helper: username is the GitHub convention
        // "x-access-token", password is the PAT. HTTPS only — never attach a token to SSH.
        if (token != null && cloneUrl.startsWith("https", ignoreCase = true)) {
            clone.setCredentialsProvider(UsernamePasswordCredentialsProvider("x-access-token", token))
        }
        clone.setTransportConfigCallback { transport ->
            if (transport is SshTransport) transport.sshSessionFactory = SSH_FACTORY
        }

        try {
            clone.call().use { /* close the repo handle; the working tree stays on disk */ }
        } catch (e: GitAPIException) {
            // A cancelled fetch surfaces as a JGit exception; translate it back to the platform's
            // cancellation signal so the caller's `stop()`/cleanup runs the same as before.
            if (indicator.isCanceled) throw ProcessCanceledException()
            val reason = e.message.orEmpty()
            if (isAuthFailure(reason)) {
                throw MdBookServeException(MdBookBundle["notification.cloneAuthFailed"])
            }
            throw MdBookServeException(MdBookBundle["notification.cloneFailed", reason.take(300)])
        }
    }

    /** Bridges JGit's [ProgressMonitor] to the IDE indicator (progress text + cancellation). */
    private class IndicatorMonitor(private val indicator: ProgressIndicator) : ProgressMonitor {
        override fun start(totalTasks: Int) {}
        override fun beginTask(title: String?, totalWork: Int) {
            if (title != null) indicator.text2 = title
        }
        override fun update(completed: Int) {}
        override fun endTask() {}
        override fun isCancelled(): Boolean = indicator.isCanceled
        override fun showDuration(enabled: Boolean) {}
    }

    private companion object {
        /**
         * One sshd factory for the JVM, discovering keys from `~/.ssh` (id_rsa/id_ecdsa/id_ed25519)
         * and honoring `~/.ssh/config` and `known_hosts`. Pure Java (Apache MINA sshd) — no native
         * ssh binary. Built eagerly-lazy so a machine that never clones over SSH pays nothing.
         */
        private val SSH_FACTORY: SshdSessionFactory by lazy {
            val home = FS.DETECTED.userHome()
            SshdSessionFactoryBuilder()
                .setHomeDirectory(home)
                .setSshDirectory(File(home, ".ssh"))
                .build(null)
        }
    }
}
