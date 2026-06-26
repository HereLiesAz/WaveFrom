import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// Programmatic versioning (source of truth: version.properties).
//   major/minor — edited by hand; bumping minor resets patch to 0.
//   patch — +1 on every artifact build; resets when minor changes.
//   build — +1 on every artifact build; NEVER resets → the Play versionCode.
//   patchMinor — bookkeeping: the minor the current patch is counted within.
//
// version.properties is read as a *tracked* configuration input (via providers),
// so the configuration cache stays enabled: it is reused for tests/checks and is
// correctly invalidated for the next artifact build (which changes the file). The
// file is written from a task at execution time — never during configuration — so
// there are no config-cache-hostile side effects in the configuration phase.
// "Bump-then-use": an artifact build advances the counters for THIS build, so a
// minor change shows its patch reset immediately.
// ---------------------------------------------------------------------------
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    val text = providers.fileContents(
        rootProject.layout.projectDirectory.file("version.properties"),
    ).asText.get()
    load(text.reader())
}
fun versionInt(key: String, default: Int = 0) =
    (versionProps.getProperty(key) ?: "$default").trim().toInt()

val verMajor = versionInt("major")
val verMinor = versionInt("minor")
val curPatch = versionInt("patch")
val curBuild = versionInt("build")
val curPatchMinor = versionInt("patchMinor", -1)

val isArtifactBuild = gradle.startParameter.taskNames.any { name ->
    val task = name.substringAfterLast(':').lowercase()
    task.startsWith("assemble") || task.startsWith("bundle")
}
val verPatch = when {
    !isArtifactBuild -> curPatch
    curPatchMinor != verMinor -> 0 // minor changed → reset patch
    else -> curPatch + 1
}
val verBuild = if (isArtifactBuild) curBuild + 1 else curBuild

val computedVersionName = "$verMajor.$verMinor.$verPatch"
val computedVersionCode = verBuild.coerceAtLeast(1) // Play requires versionCode >= 1

// Persist the advanced counters at execution time (so configuration stays pure).
// Wired into artifact builds only; CI commits the file back so the counter persists.
val bumpVersion = tasks.register("bumpVersion") {
    val out = versionPropsFile
    val text = "major=$verMajor\nminor=$verMinor\npatch=$verPatch\nbuild=$verBuild\npatchMinor=$verMinor\n"
    doLast { out.writeText(text) }
}
if (isArtifactBuild) {
    tasks.named("preBuild").configure { dependsOn(bumpVersion) }
}

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
            isMinifyEnabled = false
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
