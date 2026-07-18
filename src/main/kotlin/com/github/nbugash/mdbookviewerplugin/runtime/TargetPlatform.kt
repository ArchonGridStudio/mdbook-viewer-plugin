package com.github.nbugash.mdbookviewerplugin.runtime

import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS

enum class Os { WINDOWS, MACOS, LINUX }

enum class Arch { X64, ARM64 }

/**
 * A supported operating-system + CPU-architecture pair and the resource path of its bundled
 * (gzip-compressed) mdBook binary. [current] returns null for any host without a bundled
 * binary, which is the single trigger for the unsupported-platform error (FR-007).
 *
 * The supported set matches the targets mdBook ships prebuilt binaries for; notably there is
 * no upstream `aarch64-unknown-linux` release, so Linux ARM64 (and Windows ARM64) resolve to
 * null rather than to a binary that does not exist.
 */
data class TargetPlatform(val os: Os, val arch: Arch) {

    private val osDir: String
        get() = when (os) {
            Os.WINDOWS -> "windows"
            Os.MACOS -> "macos"
            Os.LINUX -> "linux"
        }

    private val archDir: String
        get() = when (arch) {
            Arch.X64 -> "x64"
            Arch.ARM64 -> "arm64"
        }

    /** File name of the extracted executable on this platform. */
    val executableName: String
        get() = if (os == Os.WINDOWS) "mdbook.exe" else "mdbook"

    /** Classpath resource path of the gzip-compressed binary, e.g. `mdbook/macos-arm64/mdbook.gz`. */
    val resourcePath: String
        get() = "mdbook/$osDir-$archDir/mdbook.gz"

    /** Directory segment identifying this platform in bundled-resource paths, e.g. `macos-arm64`. */
    val platformDir: String
        get() = "$osDir-$archDir"

    /** True on Windows, where bundled executables carry the `.exe` suffix. */
    val isWindows: Boolean
        get() = os == Os.WINDOWS

    companion object {
        private val SUPPORTED: Set<TargetPlatform> = setOf(
            TargetPlatform(Os.WINDOWS, Arch.X64),
            TargetPlatform(Os.MACOS, Arch.X64),
            TargetPlatform(Os.MACOS, Arch.ARM64),
            TargetPlatform(Os.LINUX, Arch.X64),
        )

        /** The host platform, or null when no bundled binary exists for it (→ FR-007). Pure host inspection. */
        fun current(): TargetPlatform? {
            val os = when (OS.CURRENT) {
                OS.Windows -> Os.WINDOWS
                OS.macOS -> Os.MACOS
                OS.Linux -> Os.LINUX
                else -> return null
            }
            val arch = when (CpuArch.CURRENT) {
                CpuArch.X86_64 -> Arch.X64
                CpuArch.ARM64 -> Arch.ARM64
                else -> return null
            }
            return TargetPlatform(os, arch).takeIf { it in SUPPORTED }
        }

        /** Human-readable host descriptor for the unsupported-platform message's {0} slot. */
        fun describeHost(): String = "${OS.CURRENT} / ${CpuArch.CURRENT}"
    }
}
