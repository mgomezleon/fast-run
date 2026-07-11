# Fast Run - Terminal Command Manager

**Save and execute your favorite terminal commands with a single click!**

A powerful productivity plugin for JetBrains IDEs that lets you save, organize, and quickly run terminal commands.

## Project Structure

```
jetbrains_extention/
├── build.gradle.kts          # Gradle build configuration
├── settings.gradle.kts       # Gradle settings
├── gradle.properties         # Plugin properties
└── src/
    └── main/
        ├── kotlin/
        │   └── com/example/terminalicon/
        │       └── TerminalIconAction.kt    # Main action handler
        └── resources/
            ├── META-INF/
            │   └── plugin.xml               # Plugin configuration
            └── icons/
                └── terminal-custom.svg      # Custom icon
```

## Build and Run

### Prerequisites
- JDK 17 or higher
- Gradle (or use the included Gradle wrapper)

### Commands

1. **Build the plugin:**
   ```bash
   ./gradlew buildPlugin
   ```

2. **Run IDE with plugin:**
   ```bash
   ./gradlew runIde
   ```

3. **Verify plugin:**
   ```bash
   ./gradlew verifyPlugin
   ```

## ✨ Features

- **💾 Save Multiple Commands:** Store unlimited terminal commands with custom names
- **🚀 Run Command Sequences:** Execute multiple commands in sequence with one click
- **📂 Global or Project-Scoped:** Choose whether a command is available in every project or only in the current one
- **📋 Fast Run Menu:** New dedicated menu in the main menu bar for quick access
- **🎨 Color Icons:** Assign a color to each command; it shows up in the menu and command list
- **🎨 Toolbar Icon:** Beautiful green icon for instant access to command manager
- **💪 Persistent Storage:** Your commands are saved automatically and persist across IDE restarts
- **📝 Easy Management:** Simple dialog to add, edit, and delete saved commands

## 🎯 Perfect For

- npm/yarn scripts (build, test, deploy)
- Docker commands
- Git workflows
- Build and deployment scripts
- Database migrations
- Any repetitive terminal tasks!

## 📦 Installation

### From JetBrains Marketplace (Coming Soon)
1. Open IntelliJ IDEA / WebStorm / PyCharm
2. Go to `Settings` → `Plugins` → `Marketplace`
3. Search for "Fast Run"
4. Click `Install`

### Manual Installation
1. Download the latest `.zip` from [Releases](https://github.com/ofirs1988/fast-run/releases)
2. Go to `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk`
3. Select the downloaded ZIP file
4. Restart IDE

## 🚀 Usage

### Adding Commands
1. Click the green toolbar icon 🟢 OR go to **Fast Run → Manage Commands...**
2. Enter a name for your command (e.g., "Build Project")
3. Add your commands (one per line for sequences):
   ```
   npm install
   npm run build
   npm test
   ```
4. Click **Save Command**

### Running Commands
- **Option 1:** Go to **Fast Run** menu and click your saved command
- **Option 2:** Click the toolbar icon and select from saved commands

### Command Sequences
Add multiple commands (one per line) and they'll run in order:
```
git pull
npm install
npm run build
```

### Global vs. Project-Scoped Commands
By default, saved commands are **global** and appear in every project. To keep a command tied to a single project:

1. Open the command editor and tick **This project only**.
2. Save — the command is now stored in `<project>/.idea/fastRunCommands.xml` and only shows up in that project.

Project-scoped commands are marked with a **PROJ** badge in the list. Since they live under `.idea/`, they can be committed and shared with your team (add the file to `.gitignore` if you'd rather keep them private). Editing a command and toggling the checkbox moves it between global and project storage.

### Color Icons
Pick a color in the **Icon** field of the editor (default, green, blue, red, yellow, purple). The matching colored dot is shown next to the command in the Fast Run menu, the terminal context menu, and the command list.

## 🛠️ Development

Built with Kotlin on the IntelliJ Platform SDK. See [CLAUDE.md](CLAUDE.md) for build commands and architecture notes.

```bash
./gradlew buildPlugin   # build the installable .zip in build/distributions/
./gradlew runIde        # launch a sandbox IDE with the plugin installed
```
