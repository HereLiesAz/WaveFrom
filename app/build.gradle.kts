import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// Versioning (source of truth: version.properties, owned by HereLiesAz/workflows).
//   The central android-play-release / android-github-release executors rewrite
//   versionMajor/Minor/Patch/Build before building, and Play additionally passes
//   -PversionCodeOverride / -PversionName (Play's next free versionCode), which win.
//   Gradle never bumps or writes the file itself.
// version.properties is read as a *tracked* configuration input (via providers),
// so the configuration cache stays enabled.
// ---------------------------------------------------------------------------
val versionProps = Properties().apply {
    val text = providers.fileContents(
        rootProject.layout.projectDirectory.file("version.properties"),
    ).asText.get()
    load(text.reader())
}
fun versionInt(key: String, default: Int = 0) =
    (versionProps.getProperty(key) ?: "$default").trim().toInt()

val verMajor = versionInt("versionMajor")
val verMinor = versionInt("versionMinor")
val verPatch = versionInt("versionPatch")
val verBuild = versionInt("versionBuild")

val computedVersionName = providers.gradleProperty("versionName").orNull
    ?: "$verMajor.$verMinor.$verPatch"
val computedVersionCode = (providers.gradleProperty("versionCodeOverride").orNull?.trim()?.toInt()
    ?: verBuild).coerceAtLeast(1) // Play requires versionCode >= 1

// CI reads the version straight from Gradle: `./gradlew -q printVersionName printVersionCode`.
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
    compileSdk = 37

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
        targetSdk = 37
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
            // R8 on so the Play executor can upload mapping.txt for deobfuscation.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
