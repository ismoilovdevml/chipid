import java.net.URI
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ismoilovdev.chipid"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ismoilovdev.chipid"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        // Phones only; drops ML Kit's x86 OCR libraries (~23 MB).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            // BouncyCastle ships multi-release jars with duplicate metadata.
            excludes += listOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = false
    }
}

// The CSCA master list is published by the German BSI; its redistribution terms are not stated,
// so it is not committed. It is fetched once into assets and its CMS signature is verified at runtime.
val masterList = layout.projectDirectory.file("src/main/assets/masterlist.ml")
val fetchMasterList by tasks.registering {
    description = "Downloads the German BSI CSCA master list into assets/masterlist.ml if missing."
    val target = masterList.asFile
    outputs.file(target)
    onlyIf { !target.exists() }
    doLast {
        val url = URI(
            "https://www.bsi.bund.de/SharedDocs/Downloads/DE/BSI/ElekAusweise/CSCA/GermanMasterList.zip?__blob=publicationFile",
        ).toURL()
        ZipInputStream(url.openStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null && !entry.name.endsWith(".ml")) entry = zip.nextEntry
            requireNotNull(entry) { "GermanMasterList.zip contains no .ml file" }
            target.parentFile.mkdirs()
            target.outputStream().use { zip.copyTo(it) }
            logger.lifecycle("Fetched ${entry.name} -> ${target.name} (${target.length()} bytes)")
        }
    }
}
tasks.named("preBuild") { dependsOn(fetchMasterList) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")

    // MRZ scanning: CameraX preview + on-device (bundled) ML Kit text recognition.
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation("org.jmrtd:jmrtd:0.8.9")
    implementation("net.sf.scuba:scuba-sc-android:0.0.27")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.86")

    testImplementation("junit:junit:4.13.2")
}
