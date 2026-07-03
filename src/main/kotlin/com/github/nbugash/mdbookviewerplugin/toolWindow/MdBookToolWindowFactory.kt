package com.github.nbugash.mdbookviewerplugin.toolWindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Registers the mdBook Viewer tool window.
 *
 * Deliberately touches no JCEF symbol: the panel always renders the URL bar and decides
 * internally whether an embedded browser can be created, so a backend without JCEF gets a
 * working tool window instead of a crash.
 */
class MdBookToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = MdBookBrowserPanel(project)
        Disposer.register(toolWindow.disposable, panel)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }
}
