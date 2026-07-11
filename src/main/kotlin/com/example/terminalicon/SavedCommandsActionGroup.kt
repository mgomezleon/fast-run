package com.example.terminalicon

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

class SavedCommandsActionGroup : ActionGroup(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val actions = mutableListOf<AnAction>()

        // Global commands (available in every project)
        CommandStorageService.getInstance().savedCommands.forEach { command ->
            actions.add(RunSavedCommandAction(command))
        }

        // Project-scoped commands (only for the current project)
        e?.project?.let { project ->
            ProjectCommandStorageService.getInstance(project).savedCommands.forEach { command ->
                actions.add(RunSavedCommandAction(command))
            }
        }

        // If no commands, show a message action
        if (actions.isEmpty()) {
            actions.add(object : AnAction("No saved commands (Click toolbar icon to add)") {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
                override fun actionPerformed(e: AnActionEvent) {}
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = false
                }
            })
        }

        return actions.toTypedArray()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}

class RunSavedCommandAction(private val savedCommand: SavedCommand) : AnAction(savedCommand.name, savedCommand.getCommandsAsString(), CommandIcons.forCommand(savedCommand)), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        TerminalIconAction.executeCommandsInTerminal(project, savedCommand)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
