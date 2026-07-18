package com.github.nbugash.mdbookviewerplugin

import com.github.nbugash.mdbookviewerplugin.runtime.Arch
import com.github.nbugash.mdbookviewerplugin.runtime.Os
import com.github.nbugash.mdbookviewerplugin.runtime.TargetPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the host → bundled-binary mapping. These are the deterministic pieces of platform
 * selection; `current()` itself is host-dependent, so it is only smoke-checked here.
 */
class TargetPlatformTest {

    @Test
    fun `resource path encodes os-arch and always points at the gzip binary`() {
        assertEquals("mdbook/linux-x64/mdbook.gz", TargetPlatform(Os.LINUX, Arch.X64).resourcePath)
        assertEquals("mdbook/macos-x64/mdbook.gz", TargetPlatform(Os.MACOS, Arch.X64).resourcePath)
        assertEquals("mdbook/macos-arm64/mdbook.gz", TargetPlatform(Os.MACOS, Arch.ARM64).resourcePath)
        assertEquals("mdbook/windows-x64/mdbook.gz", TargetPlatform(Os.WINDOWS, Arch.X64).resourcePath)
    }

    @Test
    fun `executable name is exe only on windows`() {
        assertEquals("mdbook.exe", TargetPlatform(Os.WINDOWS, Arch.X64).executableName)
        assertEquals("mdbook", TargetPlatform(Os.LINUX, Arch.X64).executableName)
        assertEquals("mdbook", TargetPlatform(Os.MACOS, Arch.ARM64).executableName)
    }

    @Test
    fun `current host resolves to a supported target or null, never a bogus one`() {
        val current = TargetPlatform.current()
        // On CI (linux-x64 / macOS) this is non-null; on an unsupported arch it is null (FR-007).
        // Either way it must never point at a resource path outside the bundled layout.
        if (current != null) {
            assertTrue(current.resourcePath.startsWith("mdbook/"))
            assertTrue(current.resourcePath.endsWith("/mdbook.gz"))
        }
    }

    @Test
    fun `host descriptor is non-blank for the unsupported-platform message`() {
        assertTrue(TargetPlatform.describeHost().isNotBlank())
    }
}
