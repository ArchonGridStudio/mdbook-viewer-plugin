package com.github.nbugash.mdbookviewerplugin

import com.github.nbugash.mdbookviewerplugin.detect.normalizeUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlNormalizationTest {

    @Test
    fun `prepends http when no scheme is present`() {
        assertEquals("http://localhost:3000", normalizeUrl("localhost:3000"))
    }

    @Test
    fun `keeps an existing http scheme`() {
        assertEquals("http://localhost:3000", normalizeUrl("http://localhost:3000"))
    }

    @Test
    fun `keeps an existing https scheme`() {
        assertEquals("https://example.com/book", normalizeUrl("https://example.com/book"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("http://127.0.0.1:8080", normalizeUrl("  127.0.0.1:8080  "))
    }

    @Test
    fun `leaves an empty string empty`() {
        assertEquals("", normalizeUrl("   "))
    }
}
