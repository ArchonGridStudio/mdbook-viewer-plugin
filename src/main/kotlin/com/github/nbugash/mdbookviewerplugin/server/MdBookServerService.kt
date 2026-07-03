package com.github.nbugash.mdbookviewerplugin.server

import com.github.nbugash.mdbookviewerplugin.MdBookBundle
import com.github.nbugash.mdbookviewerplugin.settings.MdBookSecrets
import com.github.nbugash.mdbookviewerplugin.util.BookTomlLocator
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.net.NetUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Thrown when the clone-and-serve pipeline fails; the message is user-facing. */
class MdBookServeException(message: String) : Exception(message)

/**
 * Owns the clone-and-serve lifecycle for the project: a fresh shallow clone in a temp dir
 * plus the `mdbook serve` process. As a project-level [Disposable], the platform disposes
 * it on project close, which stops the server and deletes the clone — no orphans, no leaks.
 */
@Service(Service.Level.PROJECT)
class MdBookServerService(private val project: Project) : Disposable {

    @Volatile
    private var processHandler: OSProcessHandler? = null

    @Volatile
    private var tempDir: Path? = null

    /**
     * Blocking pipeline (run inside a background task): tear down any previous serve, clone
     * [cloneUrl] into a fresh temp dir, locate the book, start `mdbook serve`, and return the
     * served URL. Throws [MdBookServeException] with a user-facing message on failure, or
     * [com.intellij.openapi.progress.ProcessCanceledException] if [indicator] is cancelled.
     */
    fun serveRepo(cloneUrl: String, indicator: ProgressIndicator): String {
        stop()
        try {
            val git = PathEnvironmentVariableUtil.findInPath("git")
                ?: throw MdBookServeException(MdBookBundle["notification.gitNotFound"])
            val mdbook = PathEnvironmentVariableUtil.findInPath("mdbook")
                ?: throw MdBookServeException(MdBookBundle["notification.mdbookNotFound"])

            indicator.text = MdBookBundle["status.cloning"]
            val dir = Files.createTempDirectory("mdbook-viewer-")
            tempDir = dir
            cloneRepo(git, cloneUrl, dir)
            indicator.checkCanceled()

            val bookRoot = BookTomlLocator.findBookRoot(dir)
                ?: throw MdBookServeException(MdBookBundle["notification.bookTomlNotFound"])

            indicator.text = MdBookBundle["status.starting"]
            return startServe(mdbook, bookRoot, indicator)
        } catch (t: Throwable) {
            stop() // clean the partial clone / process on failure or cancellation
            throw t
        }
    }

    private fun cloneRepo(git: File, cloneUrl: String, dir: Path) {
        val cmd = GeneralCommandLine(git.absolutePath, "clone", "--depth", "1")
        val token = MdBookSecrets.getToken()
        if (token != null && cloneUrl.startsWith("https://", ignoreCase = true)) {
            // Supply the token as the HTTPS password via an inline credential helper. The
            // helper reads the value from the environment ($MDBOOK_GIT_TOKEN), so the token
            // never appears in argv (ps) or in the stderr we surface on failure.
            cmd.addParameters(
                "-c", "credential.helper=",
                "-c", "credential.helper=!f() { echo username=x-access-token; echo \"password=\$MDBOOK_GIT_TOKEN\"; }; f",
            )
        }
        cmd.addParameters(cloneUrl, dir.toString())
        // Never block on an interactive credential/host-key prompt: fail fast instead.
        // Non-token auth falls back to the user's git credentials (SSH keys, credential helper).
        cmd.withEnvironment("GIT_TERMINAL_PROMPT", "0")
        cmd.withEnvironment("GIT_SSH_COMMAND", "ssh -o BatchMode=yes")
        if (token != null) cmd.withEnvironment("MDBOOK_GIT_TOKEN", token)

        val output = try {
            CapturingProcessHandler(cmd).runProcess(CLONE_TIMEOUT_MS)
        } catch (e: ExecutionException) {
            throw MdBookServeException(MdBookBundle["notification.cloneFailed", e.message ?: ""])
        }
        if (output.isTimeout) throw MdBookServeException(MdBookBundle["notification.cloneFailed", "timed out"])
        if (output.exitCode != 0) {
            val stderr = output.stderr.trim()
            // A private repo without a valid token lands here; give a token-specific hint so the
            // user knows to fix credentials rather than the URL. Either way we throw, so the book
            // is never served/rendered.
            val message = if (isAuthFailure(stderr)) {
                MdBookBundle["notification.cloneAuthFailed"]
            } else {
                MdBookBundle["notification.cloneFailed", stderr.take(300)]
            }
            throw MdBookServeException(message)
        }
    }

    private fun startServe(mdbook: File, bookRoot: Path, indicator: ProgressIndicator): String {
        val port = NetUtils.findAvailableSocketPort()
        val cmd = GeneralCommandLine(mdbook.absolutePath, "serve", "--port", port.toString())
            .withWorkDirectory(bookRoot.toFile())
        val handler = try {
            OSProcessHandler(cmd)
        } catch (e: ExecutionException) {
            throw MdBookServeException(e.message ?: MdBookBundle["notification.mdbookNotFound"])
        }

        val urlRef = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (urlRef.get() != null) return
                val text = event.text
                val url = parseServingUrl(text)
                    ?: if (text.contains("Serving on", ignoreCase = true)) "http://localhost:$port" else null
                if (url != null) {
                    urlRef.set(url)
                    latch.countDown()
                }
            }

            override fun processTerminated(event: ProcessEvent) = latch.countDown()
        })
        processHandler = handler
        handler.startNotify()

        val deadline = System.currentTimeMillis() + SERVE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (latch.await(200, TimeUnit.MILLISECONDS)) break
            indicator.checkCanceled()
            if (handler.isProcessTerminated) break
        }
        if (urlRef.get() == null && handler.isProcessTerminated) {
            throw MdBookServeException(MdBookBundle["notification.serveFailed", handler.exitCode ?: -1])
        }
        return urlRef.get() ?: "http://localhost:$port"
    }

    @Synchronized
    fun stop() {
        processHandler?.destroyProcess()
        processHandler = null
        tempDir?.let { dir -> runCatching { FileUtil.delete(dir.toFile()) } }
        tempDir = null
    }

    override fun dispose() = stop()

    companion object {
        private const val CLONE_TIMEOUT_MS = 120_000
        private const val SERVE_TIMEOUT_MS = 120_000

        fun getInstance(project: Project): MdBookServerService = project.service()
    }
}
