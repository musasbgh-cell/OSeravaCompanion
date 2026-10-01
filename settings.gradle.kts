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
rootProject.name = "SeravaCompanion"
include(":app")
include(":llamaLib")
project(":llamaLib").projectDir = file(".llama/examples/llama.android/lib")
