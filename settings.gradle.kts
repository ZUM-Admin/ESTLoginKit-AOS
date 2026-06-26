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
        // 카카오 SDK
        maven("https://devrepo.kakao.com/nexus/content/groups/public/")
        maven("https://jitpack.io")
    }
}

rootProject.name = "ESTLoginKit-Android"

include(":estloginkit")
include(":example")
