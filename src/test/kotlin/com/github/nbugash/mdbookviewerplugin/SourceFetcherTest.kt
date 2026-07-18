package com.github.nbugash.mdbookviewerplugin

import com.github.nbugash.mdbookviewerplugin.server.SourceFetcher
import com.intellij.openapi.progress.DumbProgressIndicator
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Network-gated smoke test for the pure-JVM fetch (FR-002). De-risks JGit HTTPS + shallow-clone
 * parity end-to-end. Opt-in (needs network + a real remote), so it self-skips unless run with
 * `-DmdbookIntegration=true`; that keeps the default/offline build green.
 */
class SourceFetcherTest {

    @Test
    fun `shallow-clones a public repository over https into the target directory`() {
        assumeTrue("set MDBOOK_INTEGRATION=true to run", System.getenv("MDBOOK_INTEGRATION") == "true")

        val dir = Files.createTempDirectory("sourcefetcher-it-")
        try {
            SourceFetcher().fetch(
                cloneUrl = "https://github.com/rust-lang/mdBook.git",
                token = null,
                targetDir = dir,
                indicator = DumbProgressIndicator.INSTANCE,
            )
            assertTrue("clone should populate the target dir", dir.resolve(".git").toFile().exists())
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
