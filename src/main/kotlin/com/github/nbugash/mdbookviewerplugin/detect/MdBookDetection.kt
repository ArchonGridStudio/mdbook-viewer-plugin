package com.github.nbugash.mdbookviewerplugin.detect

/**
 * Heuristic test for whether [html] was produced by mdBook.
 *
 * Detection scans the page markup rather than probing asset URLs: mdBook uses
 * content-hashed asset filenames (e.g. `general-0392ca55.css`), so there is no stable
 * `/book.js` to probe, whereas the generator comment and theme class names are emitted
 * verbatim on every page.
 */
fun looksLikeMdBook(html: String): Boolean {
    val h = html.lowercase()
    // The generator comment is emitted on every mdBook page; the theme class names are a
    // secondary signal for customized templates that might drop the comment. Requiring an
    // mdBook mention alongside a theme class avoids matching pages that merely say "mdBook".
    return "generated using mdbook" in h ||
        ("mdbook" in h && ("sidebar-scrollbox" in h || "sidebar-resize-handle" in h))
}
