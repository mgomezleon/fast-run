@file:Suppress("DEPRECATION")

package com.example.terminalicon

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.TerminalView

class TerminalIconAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Show the command dialog
        val dialog = CommandDialog(project)
        if (dialog.showAndGet()) {
            val selectedCommand = dialog.selectedCommand
            if (selectedCommand != null) {
                // Execute the commands in terminal
                executeCommandsInTerminal(project, selectedCommand)
            }
        }
    }

    companion object {
        fun executeCommandsInTerminal(project: Project, savedCommand: SavedCommand) {
            ApplicationManager.getApplication().invokeLater {
                // Determine working directory
                val workingDir = if (savedCommand.workingDirectory.isNotEmpty()) {
                    savedCommand.workingDirectory
                } else {
                    project.basePath
                }

                // Process variables in commands
                val processedCommands = savedCommand.processVariables(
                    projectName = project.name,
                    projectPath = project.basePath ?: "",
                    fileName = ""
                )

                // Try new API first (2025.2+), fallback to legacy
                if (!tryNewTerminalApi(project, savedCommand, processedCommands)) {
                    executeLegacyTerminal(project, savedCommand, processedCommands, workingDir)
                }
            }
        }

        /**
         * Try to use the new Terminal API available in IntelliJ 2025.2+
         * Uses TerminalWidget.sendCommandToExecute method via reflection
         */
        private fun tryNewTerminalApi(
            project: Project,
            savedCommand: SavedCommand,
            commands: List<String>
        ): Boolean {
            return try {
                val terminalToolWindow = ToolWindowManager.getInstance(project)
                    .getToolWindow("Terminal") ?: return false

                // Ensure terminal is visible
                terminalToolWindow.show()

                val content = terminalToolWindow.contentManager.selectedContent ?: return false
                val component = content.component

                // Try to find TerminalWidget using reflection (for compatibility)
                val terminalWidgetClass = try {
                    Class.forName("com.intellij.terminal.ui.TerminalWidget")
                } catch (e: ClassNotFoundException) {
                    return false
                }

                // Check if sendCommandToExecute method exists (2025.2+ API)
                val sendCommandMethod = try {
                    terminalWidgetClass.getMethod("sendCommandToExecute", String::class.java)
                } catch (e: NoSuchMethodException) {
                    return false
                }

                // Find the TerminalWidget in the component hierarchy
                val widget = findTerminalWidget(component, terminalWidgetClass) ?: return false

                // Set environment variables first (as commands)
                if (savedCommand.environmentVariables.isNotEmpty()) {
                    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
                    savedCommand.environmentVariables.forEach { (key, value) ->
                        val envCommand = if (isWindows) {
                            "set $key=$value"
                        } else {
                            "export $key=\"$value\""
                        }
                        sendCommandMethod.invoke(widget, envCommand)
                        // Small delay between commands
                        Thread.sleep(100)
                    }
                }

                // Execute the commands
                commands.forEach { command ->
                    sendCommandMethod.invoke(widget, command)
                    Thread.sleep(100)
                }

                true
            } catch (e: Exception) {
                // Log the error for debugging
                com.intellij.openapi.diagnostic.Logger.getInstance(TerminalIconAction::class.java)
                    .warn("Failed to use new Terminal API, falling back to legacy", e)
                false
            }
        }

        /**
         * Recursively find a TerminalWidget in the component hierarchy
         */
        private fun findTerminalWidget(component: java.awt.Component?, targetClass: Class<*>): Any? {
            if (component == null) return null
            if (targetClass.isInstance(component)) return component

            if (component is java.awt.Container) {
                for (child in component.components) {
                    val found = findTerminalWidget(child, targetClass)
                    if (found != null) return found
                }
            }
            return null
        }

        /**
         * Legacy Terminal API for IntelliJ versions before 2025.2
         * Uses TerminalView.createLocalShellWidget and ShellTerminalWidget.executeCommand
         */
        @Suppress("DEPRECATION")
        private fun executeLegacyTerminal(
            project: Project,
            savedCommand: SavedCommand,
            commands: List<String>,
            workingDir: String?
        ) {
            try {
                val terminalView = TerminalView.getInstance(project)
                val shellWidget = terminalView.createLocalShellWidget(workingDir, savedCommand.name)

                shellWidget.let { widget ->
                    // Set environment variables first
                    if (savedCommand.environmentVariables.isNotEmpty()) {
                        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
                        savedCommand.environmentVariables.forEach { (key, value) ->
                            val envCommand = if (isWindows) {
                                "set $key=$value"
                            } else {
                                "export $key=\"$value\""
                            }
                            widget.executeCommand(envCommand)
                        }
                    }

                    // Execute the commands
                    commands.forEach { command ->
                        widget.executeCommand(command)
                    }
                }
            } catch (e: Exception) {
                com.intellij.openapi.diagnostic.Logger.getInstance(TerminalIconAction::class.java)
                    .error("Failed to execute commands in terminal", e)
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        // Enable the action only when a project is open
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
}
