package com.example.terminalicon

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Shared command CRUD, implemented by both the application-level (global) and
 * project-level storages so callers can operate on either through one API.
 */
interface CommandStore {
    var savedCommands: MutableList<SavedCommand>

    fun addCommand(name: String, commands: List<String>, workingDir: String = "", icon: String = "default", shortcut: String = "", isFavorite: Boolean = false, envVars: Map<String, String> = emptyMap(), customIconPath: String = "") {
        savedCommands.add(SavedCommand(name, commands.toMutableList(), workingDir, icon, shortcut, isFavorite, envVars.toMutableMap(), customIconPath))
    }

    fun updateCommand(oldCommand: SavedCommand, name: String, commands: List<String>, workingDir: String = "", icon: String = "default", shortcut: String = "", isFavorite: Boolean = false, envVars: Map<String, String> = emptyMap(), customIconPath: String = "") {
        val index = savedCommands.indexOf(oldCommand)
        if (index != -1) {
            savedCommands[index] = SavedCommand(name, commands.toMutableList(), workingDir, icon, shortcut, isFavorite, envVars.toMutableMap(), customIconPath)
        }
    }

    fun toggleFavorite(command: SavedCommand) {
        command.isFavorite = !command.isFavorite
    }

    fun getFavoriteCommands(): List<SavedCommand> = savedCommands.filter { it.isFavorite }

    fun removeCommand(command: SavedCommand) {
        savedCommands.remove(command)
    }

    fun importCommands(commands: List<SavedCommand>) {
        savedCommands.addAll(commands)
    }

    fun exportCommands(): List<SavedCommand> = savedCommands.toList()
}

@Service
@State(
    name = "TerminalCommandStorage",
    storages = [Storage("terminalCommands.xml")]
)
class CommandStorageService : PersistentStateComponent<CommandStorageService>, CommandStore {
    override var savedCommands: MutableList<SavedCommand> = mutableListOf()
    var executionHistory: MutableList<ExecutionHistoryEntry> = mutableListOf()

    override fun getState(): CommandStorageService = this

    override fun loadState(state: CommandStorageService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun addToHistory(commandName: String, timestamp: Long = System.currentTimeMillis()) {
        executionHistory.add(0, ExecutionHistoryEntry(commandName, timestamp))
        // Keep only last 100 entries
        if (executionHistory.size > 100) {
            executionHistory.removeAt(executionHistory.size - 1)
        }
    }

    companion object {
        fun getInstance(): CommandStorageService {
            return service()
        }
    }
}

data class ExecutionHistoryEntry(
    var commandName: String = "",
    var timestamp: Long = 0
)

data class SavedCommand(
    var name: String = "",
    var commands: MutableList<String> = mutableListOf(),
    var workingDirectory: String = "",
    var icon: String = "default",
    var keyboardShortcut: String = "",
    var isFavorite: Boolean = false,
    var environmentVariables: MutableMap<String, String> = mutableMapOf(),
    // Absolute path to a custom image file. When set, it is used as-is as the command icon
    // (no color tint, terminal.svg is ignored); when empty, the `icon` color is used instead.
    var customIconPath: String = ""
) {
    // For display purposes - show all commands separated by " && "
    fun getCommandsAsString(): String {
        return commands.joinToString(" && ")
    }

    // Process variables in commands
    fun processVariables(projectName: String, projectPath: String, fileName: String = ""): List<String> {
        return commands.map { command ->
            command
                .replace("{project_name}", projectName)
                .replace("{project_path}", projectPath)
                .replace("{file_name}", fileName)
                .replace("{date}", java.time.LocalDate.now().toString())
                .replace("{time}", java.time.LocalTime.now().toString())
        }
    }

    fun getEnvVarsAsString(): String {
        if (environmentVariables.isEmpty()) return "None"
        return environmentVariables.entries.joinToString(", ") { "${it.key}=${it.value}" }
    }
}