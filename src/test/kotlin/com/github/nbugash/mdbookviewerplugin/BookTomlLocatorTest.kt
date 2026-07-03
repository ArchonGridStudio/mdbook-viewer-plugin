package com.github.nbugash.mdbookviewerplugin

import com.github.nbugash.mdbookviewerplugin.util.BookTomlLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BookTomlLocatorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `finds book toml at the search root`() {
        val root = tmp.newFolder().toPath()
        root.resolve("book.toml").toFile().writeText("[book]")

        assertEquals(root, BookTomlLocator.findBookRoot(root))
    }

    @Test
    fun `finds book toml one level down in a common subdir`() {
        val root = tmp.newFolder().toPath()
        val docs = root.resolve("docs")
        docs.toFile().mkdirs()
        docs.resolve("book.toml").toFile().writeText("[book]")

        assertEquals(docs, BookTomlLocator.findBookRoot(root))
    }

    @Test
    fun `prefers the search root over a subdir when both contain book toml`() {
        val root = tmp.newFolder().toPath()
        root.resolve("book.toml").toFile().writeText("[book]")
        val docs = root.resolve("docs")
        docs.toFile().mkdirs()
        docs.resolve("book.toml").toFile().writeText("[book]")

        assertEquals(root, BookTomlLocator.findBookRoot(root))
    }

    @Test
    fun `returns null when no book toml exists`() {
        val root = tmp.newFolder().toPath()
        root.resolve("src").toFile().mkdirs()

        assertNull(BookTomlLocator.findBookRoot(root))
    }
}
