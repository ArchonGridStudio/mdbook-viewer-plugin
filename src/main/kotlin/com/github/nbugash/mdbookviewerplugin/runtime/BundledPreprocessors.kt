package com.github.nbugash.mdbookviewerplugin.runtime

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream

/**
 * Extracts the bundled mdBook preprocessor binaries (mermaid, toc, katex, ...) for the current host
 * into a single directory, so that directory can be prepended to the `mdbook serve` process PATH and
 * mdBook can invoke each `mdbook-<name>` preprocessor by name.
 *
 * Unlike the renderer, preprocessors are best-effort: a book only needs one if its `book.toml` opts
 * in, so any failure to prepare a binary is logged and skipped rather than surfaced — a book that
 * does need a missing preprocessor still fails later with mdBook's own clear error.
 *
 * The set of tools and their versions is read from the bundled `mdbook-tools/manifest.txt`, not
 * hard-coded here, so adding a preprocessor to the build needs no change in this class.
 */
class BundledPreprocessors {

    /**
     * Returns the directory holding the extracted preprocessor binaries, or null when the host has no
     * bundled binaries (unsupported platform) or none could be prepared. Extraction is cached and
     * version-keyed by the bundled manifest, and serialized against concurrent project opens.
     */
    fun resolveBinDir(): File? {
        val platform = TargetPlatform.current() ?: return null
        val loader = javaClass.classLoader
        val manifest = loader.getResource(MANIFEST_RESOURCE)?.readText()?.trim().orEmpty()
        if (manifest.isEmpty()) return null

        val tools = manifest.lines()
            .mapNotNull { line -> line.trim().substringBefore(' ').takeIf { it.isNotEmpty() } }
        if (tools.isEmpty()) return null

        val binDir = File(PathManager.getSystemPath(), "mdbook-viewer/tools-${manifestKey(manifest)}")

        synchronized(EXTRACT_LOCK) {
            Files.createDirectories(binDir.toPath())
            var prepared = 0
            for (tool in tools) {
                val exe = File(binDir, if (platform.isWindows) "$tool.exe" else tool)
                if (exe.exists() && exe.length() > 0L) {
                    if (!exe.canExecute()) exe.setExecutable(true, false)
                    prepared++
                    continue
                }
                val resource = "mdbook-tools/$tool/${platform.platformDir}/$tool.gz"
                val input = loader.getResourceAsStream(resource)
                if (input == null) {
                    LOG.info("No bundled $tool for ${platform.platformDir}; skipping")
                    continue
                }
                try {
                    input.use { extract(it, exe) }
                    prepared++
                } catch (e: Exception) {
                    LOG.warn("Failed to prepare bundled preprocessor $tool; skipping", e)
                }
            }
            return if (prepared > 0) binDir else null
        }
    }

    /** Decompress a bundled `<tool>.gz` resource to [exe] atomically (temp file + move). */
    private fun extract(input: InputStream, exe: File) {
        val tmp = File(exe.parentFile, "${exe.name}.tmp")
        GZIPInputStream(input).use { gz -> tmp.outputStream().use { out -> gz.copyTo(out) } }
        tmp.setExecutable(true, false)
        Files.move(tmp.toPath(), exe.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        private val LOG = logger<BundledPreprocessors>()
        private val EXTRACT_LOCK = Any()
        private const val MANIFEST_RESOURCE = "mdbook-tools/manifest.txt"

        /** Stable per-content key so a version bump extracts into a fresh directory. */
        private fun manifestKey(manifest: String): String = Integer.toHexString(manifest.hashCode())
    }
}
