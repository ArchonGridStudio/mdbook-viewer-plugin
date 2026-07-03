package com.github.nbugash.mdbookviewerplugin.util

import java.nio.file.Files
import java.nio.file.Path

/**
 * Locates the directory that holds an mdBook's `book.toml` within a checked-out repo.
 * Pure so it can be unit-tested without a real clone.
 */
object BookTomlLocator {

    private const val BOOK_TOML = "book.toml"

    /** Common locations to check one level below the repo root. */
    private val COMMON_SUBDIRS = listOf("docs", "book", "doc", "guide")

    /**
     * Returns the directory containing a [BOOK_TOML], preferring [searchRoot] itself over a
     * common subdirectory. Returns null when none is found.
     */
    fun findBookRoot(searchRoot: Path): Path? {
        if (Files.isRegularFile(searchRoot.resolve(BOOK_TOML))) return searchRoot
        return COMMON_SUBDIRS
            .map { searchRoot.resolve(it) }
            .firstOrNull { Files.isRegularFile(it.resolve(BOOK_TOML)) }
    }
}
