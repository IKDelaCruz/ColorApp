import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Load version from version.properties
val versionPropsFile = file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { load(it) }
    }
}

val appVersionCode = versionProps.getProperty("VERSION_CODE", "1").toInt()
val versionMajor = versionProps.getProperty("VERSION_MAJOR", "1").toInt()
val versionMinor = versionProps.getProperty("VERSION_MINOR", "0").toInt()
val versionPatch = versionProps.getProperty("VERSION_PATCH", "0").toInt()
val appVersionName = "$versionMajor.$versionMinor.$versionPatch"

// Task to increment version code + patch on each build
tasks.register("incrementVersion") {
    doLast {
        val newCode = appVersionCode + 1
        val newPatch = versionPatch + 1
        versionProps.setProperty("VERSION_CODE", newCode.toString())
        versionProps.setProperty("VERSION_PATCH", newPatch.toString())
        versionPropsFile.outputStream().use { versionProps.store(it, null) }
        println("Version bumped to $versionMajor.$versionMinor.$newPatch (code: $newCode)")
    }
}

tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" }.configureEach {
    dependsOn("incrementVersion")
}

android {
    namespace = "com.itdcsystems.color"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.itdcsystems.color"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        compose = true
    }
    afterEvaluate {
        tasks.matching { it.name.startsWith("package") && it.name.endsWith("Debug") || it.name.endsWith("Release") }.configureEach {
            doLast {
                // Re-read version.properties to get the incremented values
                val currentProps = Properties().apply {
                    versionPropsFile.inputStream().use { load(it) }
                }
                val currentCode = currentProps.getProperty("VERSION_CODE", "1")
                val currentName = "${currentProps.getProperty("VERSION_MAJOR", "1")}.${currentProps.getProperty("VERSION_MINOR", "0")}.${currentProps.getProperty("VERSION_PATCH", "0")}"

                val outputDir = file("${project.layout.buildDirectory.get()}/outputs/apk")
                outputDir.walkTopDown().filter { it.extension == "apk" && it.name.startsWith("app-") }.forEach { apk ->
                    val buildType = if (apk.name.contains("release")) "release" else "debug"
                    val newName = "ColorSplash-v${currentName}-build${currentCode}-${buildType}.apk"
                    val dest = File(apk.parentFile, newName)
                    apk.copyTo(dest, overwrite = true)
                    println("APK: ${dest.name}")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
