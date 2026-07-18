package com.github.nbugash.mdbookviewerplugin.runtime

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import java.io.File

/**
 * Removes the extracted-mdBook cache when the plugin is uninstalled or disabled, so no bundled
 * binary is left orphaned on the host (FR-008). A plugin *update* is left alone — the new build
 * extracts into its own version-keyed directory and re-extraction is cheap, so wiping on update
 * would only cost time.
 */
class MdBookCacheCleaner : DynamicPluginListener {

    override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (isUpdate || pluginDescriptor.pluginId?.idString != PLUGIN_ID) return
        runCatching { FileUtil.delete(File(PathManager.getSystemPath(), CACHE_DIR_NAME)) }
    }

    private companion object {
        const val PLUGIN_ID = "com.github.nbugash.mdbookviewerplugin"
        const val CACHE_DIR_NAME = "mdbook-viewer"
    }
}
