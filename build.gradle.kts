plugins {
    id("com.android.application") version "9.4.1" apply false
    // Must match the Kotlin compiler AGP 9.4.1 ships with (built-in Kotlin 2.2.10).
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
