pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "gradle-kotlin-accessors-generator"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

include("annotations", "processor", "example")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
