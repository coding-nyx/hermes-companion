pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "hermes-companion"
include(":app")
include(":core:domain")
include(":core:common")
include(":transport:auth")
include(":transport:hermes")
include(":transport:broker")
include(":transport:discovery")
include(":data:db")
include(":data:repo")
include(":node")
