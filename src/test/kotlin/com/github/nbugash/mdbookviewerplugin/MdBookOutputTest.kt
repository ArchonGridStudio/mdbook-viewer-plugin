package com.github.nbugash.mdbookviewerplugin

import com.github.nbugash.mdbookviewerplugin.server.parseServingUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MdBookOutputTest {

    @Test
    fun `extracts url from a typical mdbook serve log line`() {
        val line = "2024-01-01 12:00:00 [INFO] (mdbook::cmd::serve): Serving on: http://localhost:3000"
        assertEquals("http://localhost:3000", parseServingUrl(line))
    }

    @Test
    fun `extracts url with a custom host and port`() {
        val line = "[INFO] (warp::server): listening on http://127.0.0.1:8080"
        assertEquals("http://127.0.0.1:8080", parseServingUrl(line))
    }

    @Test
    fun `returns null for a line without a url`() {
        assertNull(parseServingUrl("[INFO] (mdbook::book): Building book..."))
    }

    @Test
    fun `returns null for a blank line`() {
        assertNull(parseServingUrl("   "))
    }

    @Test
    fun `ignores a livereload websocket url`() {
        assertNull(parseServingUrl("[INFO] livereload on: ws://localhost:3001"))
    }
}
