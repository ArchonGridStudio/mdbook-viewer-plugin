package com.github.nbugash.mdbookviewerplugin

import com.github.nbugash.mdbookviewerplugin.runtime.BundledPreprocessors
import com.github.nbugash.mdbookviewerplugin.runtime.TargetPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Exercises the real extract path for the bundled preprocessors (mermaid, toc, katex). Guarded so it
 * only runs on a supported host that actually has the bundled binaries on the test classpath (i.e.
 * after `bundlePreprocessors` has run — CI); it self-skips otherwise.
 *
 * Every tool named in the bundled manifest must be extracted into the returned directory, named so
 * mdBook can invoke it by `mdbook-<name>` from PATH, and a second resolve must be a cache hit.
 */
class BundledPreprocessorsTest {

    @Test
    fun `extracts every bundled preprocessor into one reusable directory`() {
        val platform = TargetPlatform.current()
        assumeNotNull(platform)
        val manifest = javaClass.classLoader.getResource("mdbook-tools/manifest.txt")
        assumeTrue("bundled preprocessors not on classpath (run ./gradlew bundlePreprocessors)", manifest != null)

        val binDir = BundledPreprocessors().resolveBinDir()
        assertTrue("a supported host with bundled binaries should resolve a directory", binDir != null)

        val tools = manifest!!.readText().trim().lines()
            .map { it.trim().substringBefore(' ') }
            .filter { it.isNotEmpty() }
        for (tool in tools) {
            val exe = binDir!!.resolve(if (platform!!.isWindows) "$tool.exe" else tool)
            assertTrue("$tool should be extracted", exe.exists() && exe.length() > 0L)
            assertTrue("$tool should be executable", exe.canExecute())
        }

        val second = BundledPreprocessors().resolveBinDir()
        assertEquals("second resolve should be a cache hit at the same directory", binDir, second)
    }
}
