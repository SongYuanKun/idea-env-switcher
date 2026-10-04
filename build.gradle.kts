import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "io.github.ideaenvswitcher"
version = "0.5.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    intellijPlatform {
        // Community 便于本地验证；插件同样可在 Ultimate 中运行
        intellijIdeaCommunity("2024.3.6")
        bundledPlugin("com.intellij.java")
        plugin("PythonCore:243.24978.46")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
        zipSigner()
    }
    // Platform 测试运行时仍依赖 JUnit4 的 TestRule
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginVerification {
        freeArgs = listOf("-offline")
        ides {
            create(IntelliJPlatformType.IntellijIdea, "263.5701.42")
        }
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES,
        )
    }
    pluginConfiguration {
        name = "Env Switcher"
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
    wrapper {
        gradleVersion = "9.0.0"
    }
}
