package com.github.nbugash.mdbookviewerplugin.toolWindow

import com.github.nbugash.mdbookviewerplugin.MdBookBundle
import com.github.nbugash.mdbookviewerplugin.detect.looksLikeMdBook
import com.github.nbugash.mdbookviewerplugin.detect.normalizeUrl
import com.github.nbugash.mdbookviewerplugin.git.looksLikeGitRepo
import com.github.nbugash.mdbookviewerplugin.git.toCloneUrl
import com.github.nbugash.mdbookviewerplugin.server.MdBookServerService
import com.github.nbugash.mdbookviewerplugin.settings.MdBookSecrets
import com.github.nbugash.mdbookviewerplugin.settings.MdBookSettings
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.io.HttpRequests
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.SwingConstants

/**
 * The mdBook Viewer tool-window content. A single top row holds the controls —
 * [open-in-browser] [reload] [ URL field ] [connect] — and, when JCEF is available, an
 * embedded browser fills the rest. The embedded view degrades to a message when JCEF is
 * absent. Every load path funnels through [loadUrl].
 */
class MdBookBrowserPanel(private val project: Project) :
    JBPanel<MdBookBrowserPanel>(BorderLayout()), Disposable {

    private val settings = MdBookSettings.getInstance(project)
    private val urlField = JBTextField(settings.lastUrl)

    // Loaded/instantiated only when JCEF is genuinely available (see JcefSupport / MdBookJcefView).
    private val jcef: MdBookJcefView? = if (JcefSupport.isAvailable) MdBookJcefView() else null

    init {
        jcef?.let { Disposer.register(this, it) }
        add(buildToolbar(), BorderLayout.NORTH)
        add(centerComponent(), BorderLayout.CENTER)
        loadUrl(settings.lastUrl)
    }

    private fun centerComponent(): JComponent =
        jcef?.component ?: JBLabel(MdBookBundle["view.embeddedUnavailable"]).apply {
            horizontalAlignment = SwingConstants.CENTER
        }

    private fun buildToolbar(): JBPanel<*> {
        val openExternal = iconButton(AllIcons.General.Web, MdBookBundle["button.openExternal.tooltip"]) {
            val url = normalizeUrl(urlField.text)
            if (url.isNotEmpty()) BrowserUtil.browse(url)
        }
        val reload = iconButton(AllIcons.Actions.Refresh, MdBookBundle["button.reload.tooltip"]) { reloadBrowser() }
        val connect = iconButton(AllIcons.Actions.Execute, MdBookBundle["button.connect.tooltip"]) { onConnect() }
        urlField.addActionListener { onConnect() } // Enter loads + checks the URL

        val setToken = iconButton(AllIcons.General.Settings, MdBookBundle["button.token.tooltip"]) { promptForToken() }

        val leadingActions = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            add(openExternal)
            add(reload)
            add(setToken)
        }

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(leadingActions, BorderLayout.WEST)
            add(urlField, BorderLayout.CENTER)
            add(connect, BorderLayout.EAST)
        }
    }

    private fun iconButton(icon: Icon, tooltip: String, action: () -> Unit): JButton =
        JButton(icon).apply {
            toolTipText = tooltip
            addActionListener { action() }
        }

    /** Loads [raw] into the embedded browser (if present) and remembers it as the last URL. */
    fun loadUrl(raw: String) {
        val url = normalizeUrl(raw)
        if (url.isEmpty()) {
            notify(MdBookBundle["notification.urlEmpty"], NotificationType.WARNING)
            return
        }
        settings.lastUrl = url
        urlField.text = url
        jcef?.loadUrl(url)
    }

    /** Loads the entered URL, then verifies off-EDT whether it is actually an mdBook. */
    private fun onConnect() {
        val input = urlField.text.trim()
        if (input.isEmpty()) {
            notify(MdBookBundle["notification.urlEmpty"], NotificationType.WARNING)
            return
        }
        if (looksLikeGitRepo(input)) {
            serveFromRepo(toCloneUrl(input))
        } else {
            val url = normalizeUrl(input)
            loadUrl(url)
            verifyMdBook(url)
        }
    }

    /** Clones + serves a git repo in the background, then loads the served book. */
    private fun serveFromRepo(cloneUrl: String) {
        val service = MdBookServerService.getInstance(project)
        object : Task.Backgroundable(project, MdBookBundle["task.serving"], true) {
            private var servedUrl: String? = null

            override fun run(indicator: ProgressIndicator) {
                servedUrl = service.serveRepo(cloneUrl, indicator)
            }

            override fun onSuccess() {
                servedUrl?.let { loadUrl(it) }
            }

            override fun onThrowable(error: Throwable) {
                if (error is ProcessCanceledException) return
                notify(error.message ?: MdBookBundle["notification.serveError"], NotificationType.ERROR)
            }
        }.queue()
    }

    /** Fetches [url] off the EDT; if it is reachable but not an mdBook, shows an info balloon. */
    private fun verifyMdBook(url: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val html = try {
                HttpRequests.request(url)
                    .connectTimeout(PROBE_TIMEOUT_MS)
                    .readTimeout(PROBE_TIMEOUT_MS)
                    .readString()
            } catch (e: Exception) {
                null // unreachable or errored: the embedded browser shows its own state, stay quiet
            }
            if (html != null && !looksLikeMdBook(html)) {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        notify(MdBookBundle["notification.notMdBook"], NotificationType.INFORMATION)
                    }
                }
            }
        }
    }

    private fun promptForToken() {
        val token = Messages.showPasswordDialog(
            project,
            MdBookBundle["token.prompt"],
            MdBookBundle["token.title"],
            null,
        ) ?: return
        MdBookSecrets.setToken(token)
    }

    private fun reloadBrowser() {
        val view = jcef
        if (view != null) {
            view.reload()
        } else {
            notify(MdBookBundle["notification.embeddedUnavailable"], NotificationType.WARNING)
        }
    }

    private fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(message, type)
            .notify(project)
    }

    override fun dispose() {
        // The JBCefBrowser (if any) is disposed through the Disposer registration in init.
    }

    private companion object {
        const val NOTIFICATION_GROUP = "mdBook Viewer"
        const val PROBE_TIMEOUT_MS = 3000
    }
}
