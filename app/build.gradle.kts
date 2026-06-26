import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// Programmatic versioning (source of truth: version.properties).
//   major     — edited by hand.
//   minor     — edited by hand; bumping it resets patch to 0.
//   patch     — +1 on every artifact build; resets when minor changes.
//   build     — +1 on every artifact build; NEVER resets → the Play versionCode.
//   patchMinor— bookkeeping: the minor value the current patch is counted within.
// The file is only mutated when an assemble/bundle is actually requested, so
// `tasks`, IDE sync, and plain unit-test runs don't churn it. CI commits the
// mutated file back so the build counter persists across (ephemeral) runners.
// ---------------------------------------------------------------------------
val versionPropsFile = rootProject.file("version.properties")
val (computedVersionName, computedVersionCode) = run {
    val props = Properties().apply { versionPropsFile.inputStream().use { load(it) } }
    fun read(key: String, default: Int = 0) = (props.getProperty(key) ?: "$default").trim().toInt()

    val major = read("major")
    val minor = read("minor")
    var patch = read("patch")
    var build = read("build")
    val patchMinor = read("patchMinor", -1)

    val isArtifactBuild = gradle.startParameter.taskNames.any { name ->
        val task = name.substringAfterLast(':').lowercase()
        task.startsWith("assemble") || task.startsWith("bundle")
    }
    if (isArtifactBuild) {
        patch = if (patchMinor != minor) 0 else patch + 1
        build += 1
        versionPropsFile.writeText(
            buildString {
                appendLine("major=$major")
                appendLine("minor=$minor")
                appendLine("patch=$patch")
                appendLine("build=$build")
                appendLine("patchMinor=$minor")
            },
        )
    }
    // major.minor.patch name, build as the (>= 1) Play versionCode.
    Pair("$major.$minor.$patch", build.coerceAtLeast(1))
}

// Capture the values into task-local vals (not script references) so CI can read
// the version straight from Gradle: `./gradlew -q printVersionName printVersionCode`.
tasks.register("printVersionName") {
    val name = computedVersionName
    doLast { println(name) }
}
tasks.register("printVersionCode") {
    val code = computedVersionCode
    doLast { println(code) }
}

android {
    namespace = "com.hereliesaz.wavefrom"
    compileSdk = 35

    // OpenCellID key for cell-tower geolocation, from local.properties
    // (opencellid.api.key) or the OPENCELLID_API_KEY env var. Blank when unset, in
    // which case cellular detections stay RssiOnly — the feature is opt-in.
    val openCellIdKey: String = run {
        val props = Properties()
        rootProject.file("local.properties").takeIf { it.exists() }
            ?.inputStream()?.use { props.load(it) }
        props.getProperty("opencellid.api.key") ?: System.getenv("OPENCELLID_API_KEY") ?: ""
    }

    defaultConfig {
        applicationId = "com.hereliesaz.wavefrom"
        minSdk = 26
        targetSdk = 35
        versionCode = computedVersionCode
        versionName = computedVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "OPENCELLID_API_KEY", "\"$openCellIdKey\"")
    }

    // Release signing from CI secrets (keystore path via KEYSTORE_FILE, else repo-root
    // release.jks). Applied only when the keystore is present, so local debug builds and
    // PR/fork CI (no secrets) still build unsigned and never fail for missing keystore.
    val releaseKeystore = System.getenv("KEYSTORE_FILE")?.let { rootProject.file(it) }
        ?: rootProject.file("release.jks")
    val hasReleaseKeystore = releaseKeystore.exists()
    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric + Compose-under-Robolectric need merged Android resources.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle + Compose
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM-managed versions)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ARCore (Phase 2): world tracking + camera pose
    implementation(libs.google.arcore)

    testImplementation(libs.junit)
    // Real org.json so JSONObject works in JVM unit tests (android.jar only stubs it).
    testImplementation("org.json:json:20240303")
    // Robolectric + Compose-under-Robolectric: run Android-framework + Compose UI tests
    // on the JVM (no emulator). The BOM pins the ui-test artifact versions.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
