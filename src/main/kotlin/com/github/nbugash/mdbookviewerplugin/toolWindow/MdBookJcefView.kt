package com.github.nbugash.mdbookviewerplugin.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import javax.swing.JComponent

/**
 * Wraps the embedded JCEF browser. Every reference to a `com.intellij.ui.jcef` type lives
 * here, so this class is loaded only when [JcefSupport.isAvailable] is true — keeping the
 * missing-JCEF case (headless Remote Development backends) from ever touching these symbols.
 */
internal class MdBookJcefView : Disposable {

    private val browser = JBCefBrowser()

    val component: JComponent
        get() = browser.component

    init {
        Disposer.register(this, browser)
    }

    fun loadUrl(url: String) = browser.loadURL(url)

    fun reload() = browser.cefBrowser.reload()

    override fun dispose() {
        // browser is disposed through the Disposer registration above
    }
}
