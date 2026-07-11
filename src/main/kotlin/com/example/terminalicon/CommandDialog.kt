package com.example.terminalicon

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.table.JBTable
import java.awt.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel

class CommandDialog(private val project: Project) : DialogWrapper(project) {
    // UI Components
    private val commandListModel = DefaultListModel<SavedCommand>()
    private val commandList = JBList(commandListModel)
    private val storage = CommandStorageService.getInstance()
    private val projectStorage = ProjectCommandStorageService.getInstance(project)

    // Editor Fields
    private val nameField = JTextField()
    private val commandArea = JTextArea(8, 30)
    private val workingDirField = TextFieldWithBrowseButton()
    private val iconComboBox = JComboBox(arrayOf("default", "green", "blue", "red", "yellow", "purple"))
    private val shortcutField = JTextField()
    private val favoriteCheckBox = JCheckBox("Favorite")
    private val projectOnlyCheckBox = JCheckBox("This project only")

    // Environment Variables Table
    private val envVarsTableModel = DefaultTableModel(arrayOf("Variable", "Value"), 0)
    private val envVarsTable = JBTable(envVarsTableModel)

    // Search
    private val searchField = JTextField()
    private val showFavoritesOnly = JCheckBox("Favorites Only")

    // Editor buttons (kept as fields so we can reference them)
    private val saveButton = JButton("Save")
    private val cancelEditButton = JButton("Cancel Edit")

    // State
    private var editingCommand: SavedCommand? = null
    private val allCommands = mutableListOf<SavedCommand>()
    var selectedCommand: SavedCommand? = null

    init {
        title = "Fast Run - Terminal Command Manager"
        init()
        loadCommands()
        setupWorkingDirField()
        setupEnvVarsTable()
        setupListSelectionListener()
    }

    private fun setupWorkingDirField() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        workingDirField.addBrowseFolderListener(
            "Select Working Directory",
            "Choose the directory where commands will be executed",
            project,
            descriptor
        )
    }

    private fun setupEnvVarsTable() {
        envVarsTable.setShowGrid(true)
        envVarsTable.rowHeight = 24
    }

    private fun setupListSelectionListener() {
        commandList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val selected = commandList.selectedValue
                if (selected != null && editingCommand == null) {
                    populateEditorFromCommand(selected)
                }
            }
        }
    }

    private fun populateEditorFromCommand(command: SavedCommand) {
        nameField.text = command.name
        commandArea.text = command.commands.joinToString("\n")
        workingDirField.text = command.workingDirectory
        iconComboBox.selectedItem = command.icon
        shortcutField.text = command.keyboardShortcut
        favoriteCheckBox.isSelected = command.isFavorite
        projectOnlyCheckBox.isSelected = isProjectCommand(command)
        setEnvironmentVariablesInTable(command.environmentVariables)
    }

    /** Whether [command] currently lives in the project-scoped storage (by identity). */
    private fun isProjectCommand(command: SavedCommand): Boolean = projectStorage.savedCommands.any { it === command }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.preferredSize = Dimension(960, 640)

        // Split pane: left sidebar | right editor
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        splitPane.dividerSize = 1
        splitPane.dividerLocation = 300
        splitPane.isOneTouchExpandable = false

        splitPane.leftComponent = createLeftSidebar()
        splitPane.rightComponent = createRightPanel()

        mainPanel.add(splitPane, BorderLayout.CENTER)

        return mainPanel
    }

    // ==================== LEFT SIDEBAR ====================

    private fun createLeftSidebar(): JPanel {
        val sidebar = JPanel(BorderLayout(0, 0))
        sidebar.minimumSize = Dimension(260, 0)
        sidebar.border = BorderFactory.createMatteBorder(0, 0, 0, 1, UIManager.getColor("Separator.separatorColor") ?: Color.GRAY)

        // Top: Search bar
        val searchPanel = JPanel(BorderLayout(6, 0))
        searchPanel.border = BorderFactory.createEmptyBorder(8, 10, 6, 10)

        searchField.putClientProperty("JTextField.placeholderText", "Search commands...")
        searchPanel.add(searchField, BorderLayout.CENTER)
        showFavoritesOnly.toolTipText = "Show favorites only"
        searchPanel.add(showFavoritesOnly, BorderLayout.EAST)

        showFavoritesOnly.addActionListener { filterCommands() }
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = filterCommands()
            override fun removeUpdate(e: DocumentEvent?) = filterCommands()
            override fun changedUpdate(e: DocumentEvent?) = filterCommands()
        })

        sidebar.add(searchPanel, BorderLayout.NORTH)

        // Center: Command list
        commandList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        commandList.fixedCellHeight = 36
        commandList.cellRenderer = CommandListCellRenderer()

        val listScroll = JBScrollPane(commandList)
        listScroll.border = BorderFactory.createEmptyBorder()
        sidebar.add(listScroll, BorderLayout.CENTER)

        // Bottom: Action buttons + History
        val bottomPanel = JPanel(BorderLayout(0, 0))

        // Action buttons
        val actionPanel = JPanel(GridLayout(2, 1, 0, 2))
        actionPanel.border = BorderFactory.createEmptyBorder(6, 10, 4, 10)

        val topActions = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        val runButton = JButton("Run")
        val newButton = JButton("New")
        val deleteButton = JButton("Delete")

        runButton.addActionListener { runSelectedCommand() }
        newButton.addActionListener { clearFields() }
        deleteButton.addActionListener { deleteSelectedCommand() }

        topActions.add(runButton)
        topActions.add(newButton)
        topActions.add(deleteButton)

        val bottomActions = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        val importButton = JButton("Import")
        val exportButton = JButton("Export")
        val favButton = JButton("Toggle Fav")

        importButton.addActionListener { importCommands() }
        exportButton.addActionListener { exportCommands() }
        favButton.addActionListener { toggleFavoriteCommand() }

        bottomActions.add(favButton)
        bottomActions.add(importButton)
        bottomActions.add(exportButton)

        actionPanel.add(topActions)
        actionPanel.add(bottomActions)

        bottomPanel.add(actionPanel, BorderLayout.NORTH)

        // History section
        val historyPanel = createHistoryPanel()
        bottomPanel.add(historyPanel, BorderLayout.CENTER)

        sidebar.add(bottomPanel, BorderLayout.SOUTH)

        return sidebar
    }

    private fun createHistoryPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.separatorColor") ?: Color.GRAY),
            BorderFactory.createEmptyBorder(4, 0, 0, 0)
        )
        panel.preferredSize = Dimension(0, 110)

        val headerLabel = JLabel("  Recent Executions")
        headerLabel.font = headerLabel.font.deriveFont(Font.BOLD, 11f)
        headerLabel.border = BorderFactory.createEmptyBorder(4, 6, 4, 0)
        panel.add(headerLabel, BorderLayout.NORTH)

        val historyList = JBList<String>()
        val historyModel = DefaultListModel<String>()
        historyList.model = historyModel
        historyList.fixedCellHeight = 20
        historyList.font = historyList.font.deriveFont(11f)

        storage.executionHistory.take(5).forEach { entry ->
            val date = java.text.SimpleDateFormat("HH:mm:ss")
                .format(java.util.Date(entry.timestamp))
            historyModel.addElement("  $date - ${entry.commandName}")
        }

        if (historyModel.isEmpty) {
            historyModel.addElement("  No recent executions")
        }

        val scroll = JBScrollPane(historyList)
        scroll.border = BorderFactory.createEmptyBorder()
        panel.add(scroll, BorderLayout.CENTER)

        return panel
    }

    // ==================== RIGHT PANEL ====================

    private fun createRightPanel(): JPanel {
        val panel = JPanel(BorderLayout(0, 0))
        panel.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)

        // Editor content with tabs
        val tabbedPane = JTabbedPane(JTabbedPane.TOP)
        tabbedPane.border = BorderFactory.createEmptyBorder(4, 4, 0, 4)

        tabbedPane.addTab("Command", createCommandEditorTab())
        tabbedPane.addTab("Environment", createEnvironmentTab())
        tabbedPane.addTab("Templates", createTemplatesTab())

        panel.add(tabbedPane, BorderLayout.CENTER)

        // Bottom: Save/Cancel buttons
        val buttonBar = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 6))
        buttonBar.border = BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.separatorColor") ?: Color.GRAY)

        cancelEditButton.isVisible = false
        saveButton.addActionListener { saveCommand() }
        cancelEditButton.addActionListener { cancelEdit() }

        buttonBar.add(cancelEditButton)
        buttonBar.add(saveButton)

        panel.add(buttonBar, BorderLayout.SOUTH)

        return panel
    }

    private fun createCommandEditorTab(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createEmptyBorder(12, 16, 12, 16)

        val gbc = GridBagConstraints()
        gbc.insets = Insets(4, 4, 4, 4)
        gbc.anchor = GridBagConstraints.WEST

        var row = 0

        // Name + Favorite
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1; gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        panel.add(createFieldLabel("Name"), gbc)
        gbc.gridx = 1; gbc.gridwidth = 1; gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(nameField, gbc)
        gbc.gridx = 2; gbc.gridwidth = 1; gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        panel.add(favoriteCheckBox, gbc)
        row++

        // Project-only scope
        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 2; gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.anchor = GridBagConstraints.WEST
        projectOnlyCheckBox.toolTipText = "Save this command only for the current project (stored in .idea/fastRunCommands.xml)"
        panel.add(projectOnlyCheckBox, gbc)
        row++

        // Commands
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1; gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.NORTHWEST
        panel.add(createFieldLabel("Commands"), gbc)
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 1.0
        commandArea.lineWrap = true
        commandArea.wrapStyleWord = true
        commandArea.font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        panel.add(JBScrollPane(commandArea), gbc)
        row++

        // Help text
        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weighty = 0.0
        gbc.anchor = GridBagConstraints.WEST
        val helpLabel = JLabel("<html><i>One command per line. Variables: {project_name}, {project_path}, {date}, {time}</i></html>")
        helpLabel.font = helpLabel.font.deriveFont(11f)
        helpLabel.foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        panel.add(helpLabel, gbc)
        row++

        // Working Directory
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1; gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.WEST
        panel.add(createFieldLabel("Working Dir"), gbc)
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(workingDirField, gbc)
        row++

        // Icon + Shortcut on same row
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1; gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        panel.add(createFieldLabel("Icon"), gbc)

        val iconShortcutPanel = JPanel(GridBagLayout())
        val igbc = GridBagConstraints()
        igbc.insets = Insets(0, 0, 0, 12)
        igbc.gridx = 0; igbc.weightx = 0.0; igbc.fill = GridBagConstraints.NONE
        iconComboBox.preferredSize = Dimension(100, iconComboBox.preferredSize.height)
        iconShortcutPanel.add(iconComboBox, igbc)

        igbc.gridx = 1; igbc.weightx = 0.0; igbc.insets = Insets(0, 0, 0, 6)
        iconShortcutPanel.add(JLabel("Shortcut:"), igbc)

        igbc.gridx = 2; igbc.weightx = 1.0; igbc.fill = GridBagConstraints.HORIZONTAL; igbc.insets = Insets(0, 0, 0, 0)
        iconShortcutPanel.add(shortcutField, igbc)

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(iconShortcutPanel, gbc)

        return panel
    }

    private fun createEnvironmentTab(): JPanel {
        val panel = JPanel(BorderLayout(0, 8))
        panel.border = BorderFactory.createEmptyBorder(12, 16, 12, 16)

        val headerLabel = JLabel("Environment variables are set before command execution")
        headerLabel.font = headerLabel.font.deriveFont(11f)
        headerLabel.foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        panel.add(headerLabel, BorderLayout.NORTH)

        envVarsTable.preferredScrollableViewportSize = Dimension(400, 200)
        panel.add(JBScrollPane(envVarsTable), BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        val addButton = JButton("Add Variable")
        val removeButton = JButton("Remove")

        addButton.addActionListener { addEnvironmentVariable() }
        removeButton.addActionListener { removeEnvironmentVariable() }

        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun createTemplatesTab(): JPanel {
        val panel = JPanel(BorderLayout(0, 8))
        panel.border = BorderFactory.createEmptyBorder(12, 16, 12, 16)

        val headerLabel = JLabel("Select a template to populate the editor with pre-configured commands")
        headerLabel.font = headerLabel.font.deriveFont(11f)
        headerLabel.foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        panel.add(headerLabel, BorderLayout.NORTH)

        // Templates list grouped by category
        val templateListModel = DefaultListModel<String>()
        val templateList = JBList(templateListModel)
        templateList.fixedCellHeight = 28
        templateList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val templatesByCategory = CommandTemplates.getTemplatesByCategory()
        val flatTemplates = mutableListOf<CommandTemplates.Template>()

        templatesByCategory.forEach { (category, templates) ->
            templateListModel.addElement("--- $category ---")
            flatTemplates.add(CommandTemplates.Template("", "", emptyList())) // placeholder for header
            templates.forEach { template ->
                templateListModel.addElement("  ${template.name} - ${template.description}")
                flatTemplates.add(template)
            }
        }

        templateList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                val text = value as? String ?: ""
                if (text.startsWith("---")) {
                    font = font.deriveFont(Font.BOLD)
                    if (!isSelected) {
                        foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
                    }
                }
                return comp
            }
        }

        val scrollPane = JBScrollPane(templateList)
        panel.add(scrollPane, BorderLayout.CENTER)

        val applyButton = JButton("Apply Template")
        applyButton.addActionListener {
            val idx = templateList.selectedIndex
            if (idx >= 0 && idx < flatTemplates.size) {
                val template = flatTemplates[idx]
                if (template.name.isNotEmpty()) {
                    nameField.text = template.name
                    commandArea.text = template.commands.joinToString("\n")
                    iconComboBox.selectedItem = template.icon
                    setEnvironmentVariablesInTable(template.envVars)
                    // Switch back to Command tab
                    val tabbedPane = SwingUtilities.getAncestorOfClass(JTabbedPane::class.java, panel) as? JTabbedPane
                    tabbedPane?.selectedIndex = 0
                }
            }
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        buttonPanel.add(applyButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    // ==================== HELPERS ====================

    private fun createFieldLabel(text: String): JLabel {
        val label = JLabel("$text:")
        label.font = label.font.deriveFont(Font.PLAIN)
        label.preferredSize = Dimension(85, label.preferredSize.height)
        return label
    }

    // ==================== CELL RENDERER ====================

    private inner class CommandListCellRenderer : ListCellRenderer<SavedCommand> {
        override fun getListCellRendererComponent(
            list: JList<out SavedCommand>?,
            value: SavedCommand?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val panel = JPanel(BorderLayout(8, 0))
            panel.border = BorderFactory.createEmptyBorder(4, 10, 4, 10)

            if (isSelected) {
                panel.background = list?.selectionBackground ?: UIManager.getColor("List.selectionBackground")
                panel.foreground = list?.selectionForeground ?: UIManager.getColor("List.selectionForeground")
            } else {
                panel.background = list?.background ?: UIManager.getColor("List.background")
                panel.foreground = list?.foreground ?: UIManager.getColor("List.foreground")
            }
            panel.isOpaque = true

            if (value == null) return panel

            // Left: icon
            val iconLabel = JLabel(CommandIcons.forColor(value.icon))
            iconLabel.preferredSize = Dimension(20, 20)
            panel.add(iconLabel, BorderLayout.WEST)

            // Center: name + command preview
            val centerPanel = JPanel(BorderLayout(0, 1))
            centerPanel.isOpaque = false

            val nameLabel = JLabel(value.name)
            nameLabel.font = nameLabel.font.deriveFont(Font.BOLD, 12f)
            nameLabel.foreground = if (isSelected) panel.foreground else list?.foreground
            centerPanel.add(nameLabel, BorderLayout.NORTH)

            val cmdPreview = value.getCommandsAsString()
            val previewLabel = JLabel(if (cmdPreview.length > 40) cmdPreview.substring(0, 40) + "..." else cmdPreview)
            previewLabel.font = previewLabel.font.deriveFont(Font.PLAIN, 10f)
            previewLabel.foreground = if (isSelected) panel.foreground else (UIManager.getColor("Label.disabledForeground") ?: Color.GRAY)
            centerPanel.add(previewLabel, BorderLayout.SOUTH)

            panel.add(centerPanel, BorderLayout.CENTER)

            // Right: project badge + favorite star
            val eastPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
            eastPanel.isOpaque = false

            if (isProjectCommand(value)) {
                val projLabel = JLabel("PROJ")
                projLabel.font = projLabel.font.deriveFont(Font.BOLD, 9f)
                projLabel.foreground = if (isSelected) panel.foreground else Color(100, 149, 237)
                projLabel.toolTipText = "Only available in this project"
                eastPanel.add(projLabel)
            }

            if (value.isFavorite) {
                val favLabel = JLabel("*")
                favLabel.font = favLabel.font.deriveFont(Font.BOLD, 14f)
                favLabel.foreground = Color(255, 193, 7)
                eastPanel.add(favLabel)
            }

            panel.add(eastPanel, BorderLayout.EAST)

            return panel
        }
    }

    // ==================== ACTIONS ====================

    private fun addEnvironmentVariable() {
        val varName = JOptionPane.showInputDialog(this.rootPane, "Variable Name:", "Add Environment Variable", JOptionPane.PLAIN_MESSAGE)
        if (varName != null && varName.isNotBlank()) {
            val varValue = JOptionPane.showInputDialog(this.rootPane, "Variable Value:", "Add Environment Variable", JOptionPane.PLAIN_MESSAGE)
            if (varValue != null) {
                envVarsTableModel.addRow(arrayOf(varName.trim(), varValue.trim()))
            }
        }
    }

    private fun removeEnvironmentVariable() {
        val selectedRow = envVarsTable.selectedRow
        if (selectedRow >= 0) {
            envVarsTableModel.removeRow(selectedRow)
        } else {
            Messages.showWarningDialog("Please select a variable to remove", "Warning")
        }
    }

    private fun getEnvironmentVariablesFromTable(): Map<String, String> {
        val envVars = mutableMapOf<String, String>()
        for (i in 0 until envVarsTableModel.rowCount) {
            val key = envVarsTableModel.getValueAt(i, 0) as String
            val value = envVarsTableModel.getValueAt(i, 1) as String
            if (key.isNotBlank()) {
                envVars[key] = value
            }
        }
        return envVars
    }

    private fun setEnvironmentVariablesInTable(envVars: Map<String, String>) {
        envVarsTableModel.rowCount = 0
        envVars.forEach { (key, value) ->
            envVarsTableModel.addRow(arrayOf(key, value))
        }
    }

    private fun saveCommand() {
        val name = nameField.text.trim()
        val commandsText = commandArea.text.trim()
        val workingDir = workingDirField.text.trim()
        val icon = iconComboBox.selectedItem as String
        val shortcut = shortcutField.text.trim()
        val isFavorite = favoriteCheckBox.isSelected
        val envVars = getEnvironmentVariablesFromTable()

        if (name.isEmpty() || commandsText.isEmpty()) {
            Messages.showWarningDialog("Please fill both name and commands fields", "Warning")
            return
        }

        val commands = commandsText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (commands.isEmpty()) {
            Messages.showWarningDialog("Please enter at least one command", "Warning")
            return
        }

        val projectOnly = projectOnlyCheckBox.isSelected
        val targetStore: CommandStore = if (projectOnly) projectStorage else storage

        if (editingCommand != null) {
            val wasProject = isProjectCommand(editingCommand!!)
            if (wasProject == projectOnly) {
                targetStore.updateCommand(editingCommand!!, name, commands, workingDir, icon, shortcut, isFavorite, envVars)
            } else {
                // Scope changed: move the command to the other storage
                val oldStore: CommandStore = if (wasProject) projectStorage else storage
                oldStore.removeCommand(editingCommand!!)
                targetStore.addCommand(name, commands, workingDir, icon, shortcut, isFavorite, envVars)
            }
            editingCommand = null
            saveButton.text = "Save"
            cancelEditButton.isVisible = false
        } else {
            targetStore.addCommand(name, commands, workingDir, icon, shortcut, isFavorite, envVars)
        }

        loadCommands()
        clearFields()
    }

    private fun cancelEdit() {
        editingCommand = null
        saveButton.text = "Save"
        cancelEditButton.isVisible = false
        clearFields()
    }

    private fun clearFields() {
        nameField.text = ""
        commandArea.text = ""
        workingDirField.text = ""
        iconComboBox.selectedIndex = 0
        shortcutField.text = ""
        favoriteCheckBox.isSelected = false
        projectOnlyCheckBox.isSelected = false
        envVarsTableModel.rowCount = 0
        editingCommand = null
        commandList.clearSelection()
    }

    private fun editSelectedCommand() {
        val selected = commandList.selectedValue ?: run {
            Messages.showWarningDialog("Please select a command to edit", "Warning")
            return
        }

        editingCommand = selected
        populateEditorFromCommand(selected)
        saveButton.text = "Update"
        cancelEditButton.isVisible = true
    }

    private fun toggleFavoriteCommand() {
        val selected = commandList.selectedValue ?: run {
            Messages.showWarningDialog("Please select a command", "Warning")
            return
        }

        storageFor(selected).toggleFavorite(selected)
        loadCommands()
    }

    /** The storage that currently owns [command]. */
    private fun storageFor(command: SavedCommand): CommandStore = if (isProjectCommand(command)) projectStorage else storage

    private fun runSelectedCommand() {
        val selected = commandList.selectedValue ?: run {
            Messages.showWarningDialog("Please select a command to run", "Warning")
            return
        }

        selectedCommand = selected
        storage.addToHistory(selected.name)
        close(OK_EXIT_CODE)
    }

    private fun deleteSelectedCommand() {
        val selected = commandList.selectedValue ?: run {
            Messages.showWarningDialog("Please select a command to delete", "Warning")
            return
        }

        val confirm = Messages.showYesNoDialog(
            "Are you sure you want to delete '${selected.name}'?",
            "Confirm Delete",
            Messages.getQuestionIcon()
        )

        if (confirm == Messages.YES) {
            storageFor(selected).removeCommand(selected)
            loadCommands()
            clearFields()
        }
    }

    private fun importCommands() {
        Messages.showInfoMessage("Import functionality coming soon! Will support JSON format.", "Info")
    }

    private fun exportCommands() {
        Messages.showInfoMessage("Export functionality coming soon! Will support JSON format.", "Info")
    }

    private fun filterCommands() {
        val searchText = searchField.text.lowercase()
        commandListModel.clear()

        allCommands
            .filter { cmd ->
                val matchesSearch = cmd.name.lowercase().contains(searchText) ||
                        cmd.commands.any { it.lowercase().contains(searchText) }
                val matchesFavorite = !showFavoritesOnly.isSelected || cmd.isFavorite
                matchesSearch && matchesFavorite
            }
            .sortedByDescending { it.isFavorite }
            .forEach { commandListModel.addElement(it) }
    }

    private fun loadCommands() {
        allCommands.clear()
        allCommands.addAll(storage.savedCommands)
        allCommands.addAll(projectStorage.savedCommands)
        filterCommands()
    }

    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction)
    }
}
