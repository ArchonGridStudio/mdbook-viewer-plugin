package com.github.nbugash.mdbookviewerplugin

import com.github.nbugash.mdbookviewerplugin.detect.looksLikeMdBook
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MdBookDetectionTest {

    @Test
    fun `recognizes mdBook by the generator comment`() {
        val html = """
            <!DOCTYPE HTML>
            <html lang="en" class="rust sidebar-visible" dir="ltr">
                <head>
                    <!-- Book generated using mdBook -->
                    <title>Preface</title>
        """.trimIndent()
        assertTrue(looksLikeMdBook(html))
    }

    @Test
    fun `recognizes mdBook by its theme structure even without the comment`() {
        val html = """<html><body><div class="sidebar-scrollbox"></div>built with mdBook</body></html>"""
        assertTrue(looksLikeMdBook(html))
    }

    @Test
    fun `rejects a plain html page`() {
        assertFalse(looksLikeMdBook("<html><body><h1>Hello world</h1></body></html>"))
    }

    @Test
    fun `does not false-positive when mdbook is only mentioned in body text`() {
        assertFalse(looksLikeMdBook("<html><body><p>I really like mdBook for docs.</p></body></html>"))
    }

    @Test
    fun `rejects an empty document`() {
        assertFalse(looksLikeMdBook(""))
    }
}
