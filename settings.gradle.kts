pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven { setUrl("https://maven.aliyun.com/repository/public")  }
        maven { setUrl("https://maven.aliyun.com/repository/google")   }
        maven { setUrl("https://maven.aliyun.com/repository/central")   }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { setUrl("https://maven.aliyun.com/repository/public")  }
        maven { setUrl("https://maven.aliyun.com/repository/google")   }
        maven { setUrl("https://maven.aliyun.com/repository/central")   }
        mavenCentral()
    }
}

rootProject.name = "KoraIM"
include(":app")
