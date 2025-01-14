import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("maven-publish")
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

            groupId = "com.supermegazinc.libraries"
            artifactId = artifactId
            version = "2.6"
            artifact("${layout.buildDirectory.get().asFile}/outputs/aar/$artifactId-release.aar")

            pom {
                withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")
                    configurations.implementation.get().dependencies.forEach { dependency ->
                        if (dependency.group != null && dependency.version != null) {
                            dependenciesNode.appendNode("dependency").apply {
                                appendNode("groupId", dependency.group)
                                appendNode("artifactId", dependency.name)
                                appendNode("version", dependency.version)
                                appendNode("scope", "compile")
                            }
                        }
                    }
                }
            }
        }
    }

    val githubProperties = Properties().apply {
        load(File("${layout.projectDirectory.asFile}/github.properties").inputStream())
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/supermegazinc/Android-Libraries")
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
    implementation(libs.supermegazinc.logger)
    implementation(libs.supermegazinc.escentials)
}