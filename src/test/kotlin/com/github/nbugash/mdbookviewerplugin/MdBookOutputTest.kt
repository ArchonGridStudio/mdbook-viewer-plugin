package com.github.nbugash.mdbookviewerplugin

import com.github.nbugash.mdbookviewerplugin.server.extractMdBookError
import com.github.nbugash.mdbookviewerplugin.server.parseServingUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `extractMdBookError surfaces the missing-backend error, not the info noise`() {
        val output = """
             INFO Book building has started
             INFO Running the katex backend
            ERROR The command `mdbook-katex` wasn't found, is the `katex` backend installed?
            ERROR Rendering failed
            	Caused by: Unable to run the backend `katex`
        """.trimIndent()
        val detail = extractMdBookError(output)
        assertTrue("keeps the actionable ERROR", detail.contains("mdbook-katex"))
        assertTrue("keeps the cause chain", detail.contains("Caused by"))
        assertTrue("drops INFO noise", !detail.contains("Book building has started"))
    }

    @Test
    fun `extractMdBookError falls back to the last non-blank lines when there is no ERROR marker`() {
        assertEquals("first\nsomething odd happened", extractMdBookError("first\n\nsomething odd happened\n"))
    }

    @Test
    fun `extractMdBookError returns empty for empty output`() {
        assertEquals("", extractMdBookError(""))
    }
}
