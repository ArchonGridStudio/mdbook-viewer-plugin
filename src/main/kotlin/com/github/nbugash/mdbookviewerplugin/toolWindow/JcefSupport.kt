package com.github.nbugash.mdbookviewerplugin.toolWindow

/**
 * Safe probe for JCEF availability.
 *
 * A headless Remote Development backend ships without JCEF, so `com.intellij.ui.jcef.JBCefApp`
 * may not even be on the classpath. Referencing it directly throws [ClassNotFoundException] /
 * [NoClassDefFoundError] and takes the whole tool window down. This probe resolves the class
 * reflectively first, so the direct `isSupported()` call is only reached when the class is
 * genuinely loadable; any failure degrades to `false`.
 */
internal object JcefSupport {

    val isAvailable: Boolean by lazy {
        try {
            Class.forName("com.intellij.ui.jcef.JBCefApp")
            com.intellij.ui.jcef.JBCefApp.isSupported()
        } catch (t: Throwable) {
            false
        }
    }
}
