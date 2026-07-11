package com.example.terminalicon

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Project-scoped command storage. Persists to <project>/.idea/fastRunCommands.xml so
 * commands marked "This project only" live with the project (and can be shared via VCS)
 * instead of the application-wide [CommandStorageService].
 */
@Service(Service.Level.PROJECT)
@State(
    name = "FastRunProjectCommandStorage",
    storages = [Storage("fastRunCommands.xml")]
)
class ProjectCommandStorageService : PersistentStateComponent<ProjectCommandStorageService>, CommandStore {
    override var savedCommands: MutableList<SavedCommand> = mutableListOf()

    override fun getState(): ProjectCommandStorageService = this

    override fun loadState(state: ProjectCommandStorageService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: Project): ProjectCommandStorageService = project.service()
    }
}
