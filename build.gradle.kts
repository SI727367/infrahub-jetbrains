import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.20"
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

intellijPlatform {
    instrumentCode = false
}

dependencies {
    // HTTP client - OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON parsing - kotlinx-serialization (matches @Serializable data classes)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // YAML parsing for schema and Infrahub config files
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation("junit:junit:4.13.2")

    // MockWebServer for unit tests
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        local("${System.getProperty("user.home")}/Applications/IntelliJ IDEA.app")
        testFramework(TestFrameworkType.Platform)
    }
}
