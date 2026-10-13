buildscript {
    dependencies {
        // Android Gradle plugin 9 has built-in Kotlin support and defaults to KGP 2.2.10.
        // Miuix 0.9.4 is built with Kotlin 2.4.x, so the bundled Kotlin Gradle plugin is raised here.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("com.android.library") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

tasks.register<Delete>("clean").configure {
    delete(layout.buildDirectory)
}
