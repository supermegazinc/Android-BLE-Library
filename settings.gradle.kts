import java.io.FileInputStream
import java.util.Properties

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

val githubProperties = Properties()
githubProperties.load(FileInputStream("./github.properties"))

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/supermegazinc/Android-Escentials-Library")
            credentials {
                username = githubProperties["gpr.usr"] as String?
                password = githubProperties["gpr.key"] as String?
            }
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://github.com/supermegazinc/Android-Logger-Library")
            credentials {
                username = githubProperties["gpr.usr"] as String?
                password = githubProperties["gpr.key"] as String?
            }
        }
    }
}

rootProject.name = "ble"