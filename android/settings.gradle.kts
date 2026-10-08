pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "ascon"

include(":app")
include(":core")
include(":feature:library")
include(":feature:series")
include(":feature:browser")
include(":feature:reader")
include(":feature:settings")
include(":engine:adblock")
include(":engine:detection")
