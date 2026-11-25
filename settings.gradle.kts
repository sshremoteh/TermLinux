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

rootProject.name = "TermLinux"
include(":app")
include(":terminal-emulator")
include(":terminal-view")

project(":terminal-emulator").projectDir = file("native/terminal/terminal-emulator")
project(":terminal-view").projectDir = file("native/terminal/terminal-view")
