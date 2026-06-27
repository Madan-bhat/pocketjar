plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

import java.net.URL
import java.util.zip.ZipInputStream

android {
    namespace = "com.mchost"
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "com.mchost"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        aidl = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += setOf(
                "**/libjvm.so",
                "**/libjli.so",
                "**/libjsig.so",
                "**/libmchost_jvm.so",
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
}

tasks.register("checkJreAsset") {
    doLast {
        val asset = file("src/main/assets/runtime/jre21-aarch64.zip")
        if (!asset.exists() || asset.length() < 10L * 1024 * 1024) {
            logger.warn(
                "JRE asset missing or too small. Run: bash scripts/setup-android-runtime.sh"
            )
        }
    }
}

tasks.register("ensureLoclxBinary") {
    doLast {
        val dest = file("src/main/jniLibs/arm64-v8a/libloclx.so")
        if (dest.exists() && dest.length() > 1_000_000L) {
            logger.lifecycle("LocalXpose CLI present (${dest.length()} bytes)")
            return@doLast
        }
        dest.parentFile.mkdirs()
        val zip = layout.buildDirectory.file("loclx-download.zip").get().asFile
        logger.lifecycle("Downloading LocalXpose CLI into jniLibs (one-time, ~23 MB)…")
        URL("https://api.localxpose.io/api/v2/downloads/loclx-linux-arm64.zip")
            .openStream().use { input ->
                zip.outputStream().use { output -> input.copyTo(output) }
            }
        var found = false
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith("loclx")) {
                    dest.outputStream().use { zis.copyTo(it) }
                    found = true
                    break
                }
                entry = zis.nextEntry
            }
        }
        zip.delete()
        if (!found || !dest.exists() || dest.length() < 1_000_000L) {
            throw GradleException("Failed to download LocalXpose CLI — run: bash scripts/setup-android-runtime.sh")
        }
        logger.lifecycle("Installed ${dest.absolutePath} (${dest.length()} bytes)")
    }
}

tasks.named("preBuild") {
    dependsOn("checkJreAsset", "ensureLoclxBinary")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.gson)
    implementation(libs.weupnp)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
