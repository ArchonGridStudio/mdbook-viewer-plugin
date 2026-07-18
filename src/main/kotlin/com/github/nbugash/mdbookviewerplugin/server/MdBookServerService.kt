package com.github.nbugash.mdbookviewerplugin.server

import com.github.nbugash.mdbookviewerplugin.MdBookBundle
import com.github.nbugash.mdbookviewerplugin.runtime.BundledMdBook
import com.github.nbugash.mdbookviewerplugin.runtime.BundledPreprocessors
import com.github.nbugash.mdbookviewerplugin.settings.MdBookSecrets
import com.github.nbugash.mdbookviewerplugin.util.BookTomlLocator
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
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
            // Bundled renderer + verify FIRST: fail fast with a clear message on an unsupported
            // platform (FR-007) or a non-functional binary (FR-010) before doing any network work.
            val mdbook = BundledMdBook().resolveExecutable(indicator)

            indicator.text = MdBookBundle["status.cloning"]
            val dir = Files.createTempDirectory("mdbook-viewer-")
            tempDir = dir
            SourceFetcher().fetch(cloneUrl, MdBookSecrets.getToken(), dir, indicator)
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

    private fun startServe(mdbook: File, bookRoot: Path, indicator: ProgressIndicator): String {
        val port = NetUtils.findAvailableSocketPort()
        val cmd = GeneralCommandLine(mdbook.absolutePath, "serve", "--port", port.toString())
            .withWorkDirectory(bookRoot.toFile())
        prependPreprocessorsToPath(cmd)
        val handler = try {
            OSProcessHandler(cmd)
        } catch (e: ExecutionException) {
            throw MdBookServeException(MdBookBundle["notification.mdbookVerifyFailed"])
        }

        val urlRef = AtomicReference<String?>(null)
        // Capture mdBook's own output so a failure can report the real cause (a missing
        // preprocessor/backend, a broken SUMMARY.md, ...) instead of a bare exit code.
        val output = StringBuilder()
        val latch = CountDownLatch(1)
        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                synchronized(output) { if (output.length < MAX_OUTPUT_CAPTURE) output.append(event.text) }
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
            val detail = extractMdBookError(synchronized(output) { output.toString() })
            throw MdBookServeException(MdBookBundle["notification.serveFailed", handler.exitCode ?: -1, detail])
        }
        return urlRef.get() ?: "http://localhost:$port"
    }

    /**
     * Makes the bundled preprocessors (mermaid, toc, katex) discoverable by prepending their
     * directory to the serve process's PATH — mdBook invokes each `mdbook-<name>` from PATH when a
     * book opts in via `[preprocessor.<name>]`. Best-effort: a book that needs none is unaffected,
     * and one that needs a preprocessor we could not prepare still fails with mdBook's own message.
     *
     * Note: some preprocessors also expect their JS/CSS assets (e.g. `mermaid.min.js`) in the book,
     * which authors commit via `mdbook-<tool> install`; the bundled binary makes the build succeed,
     * and books set up that way render fully.
     */
    private fun prependPreprocessorsToPath(cmd: GeneralCommandLine) {
        val binDir = runCatching { BundledPreprocessors().resolveBinDir() }.getOrNull() ?: return
        val current = System.getenv("PATH").orEmpty()
        val combined = if (current.isEmpty()) binDir.absolutePath
        else binDir.absolutePath + File.pathSeparator + current
        cmd.withEnvironment("PATH", combined)
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
        private const val SERVE_TIMEOUT_MS = 120_000
        private const val MAX_OUTPUT_CAPTURE = 16_384

        fun getInstance(project: Project): MdBookServerService = project.service()
    }
}
