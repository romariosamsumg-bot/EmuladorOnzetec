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
        // LibretroDroid é publicado via JitPack.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.Swordfish90") }
        }
    }
}

rootProject.name = "RetroPlay"

include(":app")
include(":core:common")
include(":core:ui")
include(":domain")
include(":data")
include(":baselineprofile")
// LibretroDroid com o grampo de áudio dos highlights (fonte em third_party/libretrodroid).
include(":libretrodroid")
project(":libretrodroid").projectDir = file("third_party/libretrodroid")
