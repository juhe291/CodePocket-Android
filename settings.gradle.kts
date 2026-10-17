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
include(":app")

// Opt-in isolated prototype; the normal IDE and CI dependency graph do not include it.
if (providers.gradleProperty("codepocket.editorLab").orNull == "true") include(":editor-lab")
