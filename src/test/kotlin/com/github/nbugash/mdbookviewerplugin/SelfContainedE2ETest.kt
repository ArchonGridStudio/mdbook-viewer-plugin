package com.github.nbugash.mdbookviewerplugin

import com.github.nbugash.mdbookviewerplugin.runtime.BundledMdBook
import com.github.nbugash.mdbookviewerplugin.runtime.TargetPlatform
import com.github.nbugash.mdbookviewerplugin.server.SourceFetcher
import com.github.nbugash.mdbookviewerplugin.util.BookTomlLocator
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.DumbProgressIndicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * End-to-end proof that the plugin is self-contained: it fetches a repository with **no `git`
 * installed** and renders an mdBook with **no `mdbook` installed** — every external binary comes
 * from the plugin, executed by absolute path, never via `PATH`.
 *
 * Opt-in (network + bundled binaries required): run with `-DmdbookIntegration=true`. Meaningful
 * only when launched with `git`/`mdbook` shadowed or absent from `PATH`.
 */
class SelfContainedE2ETest {

    @Test
    fun `fetches a real repo without git, then renders an mdBook with the bundled binary`() {
        assumeTrue("set MDBOOK_INTEGRATION=true to run", System.getenv("MDBOOK_INTEGRATION") == "true")
        assumeNotNull(TargetPlatform.current())

        // --- FETCH: pure-JVM JGit. If any code shelled out to a `git` binary this would use PATH;
        //     it does not. Proves the fetch half needs no installed git. ---
        val cloneDir = Files.createTempDirectory("e2e-fetch-")
        try {
            SourceFetcher().fetch(
                cloneUrl = "https://github.com/rust-lang/mdBook.git",
                token = null,
                targetDir = cloneDir,
                indicator = DumbProgressIndicator.INSTANCE,
            )
            assertTrue("JGit clone produced a .git dir", cloneDir.resolve(".git").toFile().isDirectory)
            assertNotNull("cloned repo contains an mdBook (book.toml found)", BookTomlLocator.findBookRoot(cloneDir))
        } finally {
            cloneDir.toFile().deleteRecursively()
        }

        // --- RENDER: the bundled mdbook, resolved to an absolute path under the IDE-private system
        //     dir, renders a book. The v0.5.3 mdbook on this box's PATH is never consulted; the
        //     bundled v0.5.4 is used. Proves the render half needs no installed mdbook. ---
        val mdbook = BundledMdBook().resolveExecutable(DumbProgressIndicator.INSTANCE)
        val privateRoot = Path.of(PathManager.getSystemPath(), "mdbook-viewer").toAbsolutePath()
        println("[e2e] bundled mdbook: ${mdbook.absolutePath}")
        println("[e2e] IDE-private cache root: $privateRoot")
        assertTrue(
            "extracted binary must live under the IDE-private system dir, not a shared/PATH location",
            mdbook.toPath().toAbsolutePath().startsWith(privateRoot),
        )

        val book = writeMinimalBook()
        try {
            val build = CapturingProcessHandler(
                GeneralCommandLine(mdbook.absolutePath, "build").withWorkDirectory(book.toFile()),
            ).runProcess(120_000)
            assertEquals("bundled mdbook build failed:\n${build.stderr}", 0, build.exitCode)

            val indexHtml = book.resolve("book/index.html").toFile()
            assertTrue("bundled mdbook rendered index.html", indexHtml.exists())
            assertTrue("rendered HTML contains the chapter body", indexHtml.readText().contains("Self contained hello"))
        } finally {
            book.toFile().deleteRecursively()
        }
    }

    /** A minimal, preprocessor-free mdBook so the render assertion is deterministic. */
    private fun writeMinimalBook(): Path {
        val root = Files.createTempDirectory("e2e-book-")
        root.resolve("book.toml").toFile().writeText("[book]\ntitle = \"E2E\"\n")
        val src = Files.createDirectories(root.resolve("src"))
        src.resolve("SUMMARY.md").toFile().writeText("# Summary\n\n- [Intro](intro.md)\n")
        src.resolve("intro.md").toFile().writeText("# Intro\n\nSelf contained hello.\n")
        return root
    }
}
