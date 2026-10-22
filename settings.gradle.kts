pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "CodePocket"
include(":editor-sora", ":app", ":workspace-core", ":editor-surface", ":editor-assist", ":terminal-emulator", ":terminal-view")
project(":terminal-emulator").projectDir = file("third-party/termux/terminal-emulator")
project(":terminal-view").projectDir = file("third-party/termux/terminal-view")

// Opt-in isolated prototype; the normal IDE and CI dependency graph do not include it.
if (providers.gradleProperty("codepocket.editorLab").orNull == "true") include(":editor-lab")
