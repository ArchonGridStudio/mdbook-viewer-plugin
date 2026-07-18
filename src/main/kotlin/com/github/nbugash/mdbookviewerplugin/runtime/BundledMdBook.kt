package com.github.nbugash.mdbookviewerplugin.runtime

import com.github.nbugash.mdbookviewerplugin.MdBookBundle
import com.github.nbugash.mdbookviewerplugin.server.MdBookServeException
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProgressIndicator
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream

/**
 * Resolves a ready-to-run mdBook executable for the current host from the binary bundled in the
 * plugin — no `PATH` lookup and no external install (FR-001, FR-006). The gzip-compressed
 * resource is extracted once into a version-keyed cache under the IDE system directory, made
 * executable, and verified by running `mdbook --version` before it is handed out (FR-010). A
 * host with no bundled binary raises the unsupported-platform error (FR-007).
 */
class BundledMdBook {

    /**
     * @throws MdBookServeException with [MdBookBundle] "notification.platformUnsupported" when no
     *   bundled binary exists for this host, or "notification.mdbookVerifyFailed" when the
     *   extracted binary cannot run.
     */
    fun resolveExecutable(indicator: ProgressIndicator): File {
        val platform = TargetPlatform.current()
            ?: throw MdBookServeException(MdBookBundle["notification.platformUnsupported", TargetPlatform.describeHost()])

        val cacheDir = File(PathManager.getSystemPath(), "mdbook-viewer/${bundledVersion()}")
        val exe = File(cacheDir, platform.executableName)

        // Extraction is JVM-wide serialized: concurrent project opens must not race the same file.
        synchronized(EXTRACT_LOCK) {
            if (!exe.exists() || exe.length() == 0L) {
                indicator.text = MdBookBundle["status.preparing"]
                extract(platform, exe)
            }
            if (!exe.canExecute()) exe.setExecutable(true, false)
        }

        verify(exe)
        return exe
    }

    /** Decompress the bundled `mdbook.gz` resource to [exe] atomically (temp file + move). */
    private fun extract(platform: TargetPlatform, exe: File) {
        Files.createDirectories(exe.parentFile.toPath())
        val resource = javaClass.classLoader.getResourceAsStream(platform.resourcePath)
            ?: throw MdBookServeException(MdBookBundle["notification.platformUnsupported", TargetPlatform.describeHost()])
        val tmp = File(exe.parentFile, "${exe.name}.tmp")
        resource.use { input ->
            GZIPInputStream(input).use { gz ->
                tmp.outputStream().use { out -> gz.copyTo(out) }
            }
        }
        tmp.setExecutable(true, false)
        Files.move(tmp.toPath(), exe.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * Run `mdbook --version`. A present-but-non-functional binary (blocked by OS security,
     * missing exec bit, corrupt) MUST surface as an error here rather than fall through to a
     * serve attempt (FR-010).
     */
    private fun verify(exe: File) {
        val output = try {
            CapturingProcessHandler(GeneralCommandLine(exe.absolutePath, "--version")).runProcess(VERIFY_TIMEOUT_MS)
        } catch (e: ExecutionException) {
            throw MdBookServeException(MdBookBundle["notification.mdbookVerifyFailed"])
        }
        if (output.isTimeout || output.exitCode != 0) {
            throw MdBookServeException(MdBookBundle["notification.mdbookVerifyFailed"])
        }
    }

    companion object {
        private val EXTRACT_LOCK = Any()
        private const val VERIFY_TIMEOUT_MS = 10_000

        /** Pinned version written alongside the binaries at build time; keys the cache directory. */
        private fun bundledVersion(): String =
            BundledMdBook::class.java.classLoader.getResource("mdbook/version.txt")?.readText()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "unknown"
    }
}
