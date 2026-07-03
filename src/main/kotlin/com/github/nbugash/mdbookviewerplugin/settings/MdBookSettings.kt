package com.github.nbugash.mdbookviewerplugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Project-level persistence for the mdBook Viewer. Currently only remembers the last
 * URL the user loaded, so the tool window can prefill it on the next open.
 */
@Service(Service.Level.PROJECT)
@State(name = "MdBookViewerSettings", storages = [Storage("mdBookViewer.xml")])
class MdBookSettings : PersistentStateComponent<MdBookSettings.State> {

    class State {
        var lastUrl: String = "http://localhost:3000"
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var lastUrl: String
        get() = state.lastUrl
        set(value) {
            state.lastUrl = value
        }

    companion object {
        fun getInstance(project: Project): MdBookSettings = project.service()
    }
}
