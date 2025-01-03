import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("maven-publish")
}

val githubProperties = Properties().apply {
    load(File("${layout.projectDirectory.asFile}/github.properties").inputStream())
}

android {

    namespace = "com.supermegazinc.${project.name}"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

}

publishing {
    publications {
        create<MavenPublication>("gpr") {
            run {
                groupId = "com.supermegazinc.libraries"
                artifactId = artifactId
                version = "2.0"
                artifact("${layout.buildDirectory.get().asFile}/outputs/aar/$artifactId-release.aar")
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/supermegazinc/Android-BLE-Library")
            credentials {
                username = githubProperties["gpr.usr"] as String?
                password = githubProperties["gpr.key"] as String?
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.logger)
    implementation(libs.libraries.escentials)
}