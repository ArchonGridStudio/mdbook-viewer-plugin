package com.github.nbugash.mdbookviewerplugin

import com.github.nbugash.mdbookviewerplugin.runtime.BundledMdBook
import com.github.nbugash.mdbookviewerplugin.runtime.TargetPlatform
import com.intellij.openapi.progress.DumbProgressIndicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Exercises the real extract → chmod → verify path (FR-001, FR-010). Guarded so it only runs on
 * a supported host that actually has the bundled binary on the test classpath (i.e. after
 * `bundleMdbook` has run — CI). It self-skips on an unsupported arch or a binary-less local run.
 *
 * The negative paths — unsupported platform (FR-007) and a corrupt/blocked binary (FR-010 error)
 * — are validated at the service/quickstart level (Scenarios F, G) because forcing them in a unit
 * test requires overriding the static host lookup; they are intentionally not faked here.
 */
class BundledMdBookTest {

    @Test
    fun `resolves an executable that runs, and reuses it on the second call`() {
        val platform = TargetPlatform.current()
        assumeNotNull(platform)
        assumeTrue(
            "bundled binary not on classpath (run ./gradlew bundleMdbook)",
            javaClass.classLoader.getResource(platform!!.resourcePath) != null,
        )

        val indicator = DumbProgressIndicator.INSTANCE
        val first = BundledMdBook().resolveExecutable(indicator)
        assertTrue("extracted file should exist", first.exists())
        assertTrue("extracted file should be executable", first.canExecute())

        val second = BundledMdBook().resolveExecutable(indicator)
        assertEquals("second resolve should be a cache hit at the same path", first.absolutePath, second.absolutePath)
    }
}
