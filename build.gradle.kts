//ext.kotlin.version = "1.6.10"

plugins {
    kotlin("multiplatform") version "1.8.10"
}

group = "me.rfirmin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val szTarget = when {
        hostOs == "Mac OS X" -> macosX64("sz")
        hostOs == "Linux" -> linuxX64("sz")
        isMingwX64 -> mingwX64("sz")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    szTarget.apply {
        compilations["main"].enableEndorsedLibs = false

        binaries {
            executable(listOf(DEBUG, RELEASE)) {
                entryPoint = "main"
            }
        }
    }
    sourceSets {
        val szMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
            }
        }
        val szTest by getting
    }

    // this is here to fool intellij into being able to run tests
    jvm {}
}
