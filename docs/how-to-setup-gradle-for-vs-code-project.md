# How to Set Up Gradle for a VS Code Project

This guide covers configuring Gradle for a Java/Gradle project in Visual Studio Code, including installation, VS Code extension setup, and the Gradle wrapper.

## Table of Contents

- [Prerequisites](#prerequisites)
- [1. Install Gradle](#1-install-gradle-if-not-already-installed)
- [2. Install the Gradle for Java extension](#2-install-the-gradle-for-java-extension-in-vs-code)
- [3. Configure VS Code workspace settings](#3-configure-vs-code-workspace-settings)
- [4. Generate a Gradle wrapper](#4-generate-a-gradle-wrapper-recommended)
- [5. Reload VS Code](#5-reload-vs-code)
- [6. Troubleshooting](#6-troubleshooting)

## Prerequisites

- A JDK installed locally (JDK 17+ recommended; JDK 25 used in examples below)
- Visual Studio Code
- Administrator access, if setting system-wide environment variables

---

## 1. Install Gradle (if not already installed)

1. Download Gradle from [gradle.org/releases](https://gradle.org/releases/).
2. Extract the zip to a location on your machine, e.g. `C:\gradle\gradle-9.6.1`.
3. Verify it works:

   ```powershell
   & "C:\gradle\gradle-9.6.1\bin\gradle.bat" --version
   ```

### Optional: Set system environment variables

Setting `GRADLE_HOME` and adding Gradle to your `PATH` lets you run `gradle` directly from any terminal:

```powershell
# Run as Administrator
[Environment]::SetEnvironmentVariable("GRADLE_HOME", "C:\gradle\gradle-9.6.1", "User")

# Add to PATH
$currentPath = [Environment]::GetEnvironmentVariable("PATH", "User")
[Environment]::SetEnvironmentVariable("PATH", "$currentPath;C:\gradle\gradle-9.6.1\bin", "User")
```

> Restart your terminal after setting these for the changes to take effect.

---

## 2. Install the Gradle for Java extension in VS Code

Search for and install the **Gradle for Java** extension by Microsoft:

```
Extension ID: vscjava.vscode-gradle
```

Or install it from the command line:

```powershell
code --install-extension vscjava.vscode-gradle
```

---

## 3. Configure VS Code workspace settings

Create or edit `.vscode/settings.json` in your project root:

```json
{
  "gradle.java.home": "C:\\Java\\jdk-25",
  "gradle.gradleHome": "C:\\gradle\\gradle-9.6.1"
}
```

| Setting              | Description                                 |
| -------------------- | -------------------------------------------- |
| `gradle.java.home`   | Path to the JDK used by Gradle               |
| `gradle.gradleHome`  | Path to your Gradle installation directory   |

> **Note:** Use double backslashes (`\\`) in JSON paths on Windows.

---

## 4. Generate a Gradle Wrapper (recommended)

The **Gradle Wrapper** (`gradlew` / `gradlew.bat`) pins the Gradle version per project, so everyone on the team uses the same version without installing it manually.

> **Important:** Run this from the **root project directory**, not from a submodule.

```powershell
cd C:\path\to\your\project-root
gradle wrapper --gradle-version 9.6.1
```

This creates:

| File                                        | Purpose                                          |
| -------------------------------------------- | ------------------------------------------------- |
| `gradlew` (Unix) / `gradlew.bat` (Windows)   | Cross-platform scripts to run Gradle              |
| `gradle/wrapper/gradle-wrapper.jar`          | Small JAR that bootstraps the Gradle version      |
| `gradle/wrapper/gradle-wrapper.properties`   | Configuration file that pins the Gradle version   |

After the wrapper is generated, **commit these files to version control** (including the JAR — it's small and required for the wrapper to work without a local Gradle install).

### Using the wrapper

Instead of `gradle build`, use:

```powershell
# Windows
gradlew build
```

```bash
# Unix / macOS
./gradlew build
```

---

## 5. Reload VS Code

After making configuration changes:

1. **Reload the window:** `Ctrl+Shift+P` → `Developer: Reload Window`
2. The **Gradle for Java** extension should now detect your Gradle installation and the project's build file.
3. You'll see Gradle tasks in the **Gradle sidebar** (look for the Gradle icon in the activity bar).

---

## 6. Troubleshooting

### "Task 'wrapper' not found in project"

The `wrapper` task must be run from the **root project**, not from a submodule in a multi-module project.

**❌ Wrong:**

```powershell
cd oauth2\oauth2-authorization-server
gradle wrapper --gradle-version 9.6.1
```

**✅ Correct:**

```powershell
cd C:\path\to\project-root
gradle wrapper --gradle-version 9.6.1
```

### Gradle not detected in VS Code

- Verify `gradle.gradleHome` in `.vscode/settings.json` points to the correct path.
- Check that the JDK path in `gradle.java.home` is valid.
- Reload the VS Code window after changing settings.

### POM relocation warnings

Warnings like:

```
POM relocation to an other version number is not fully supported in Gradle
```

These are informational and can usually be ignored. Consider updating the affected dependency to the direct version recommended in the warning.

---

## License

This guide is provided as-is for internal reference. Adapt freely for your team's documentation.
