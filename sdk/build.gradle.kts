plugins {
    id("com.android.library")
    kotlin("android")
    id("maven-publish")
}

android {
    namespace = "com.neurosky.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}

// The Git tag is the single source of truth for the SDK version.
// JitPack builds a tag with `-Pversion=<tag>`; otherwise fall back to `git describe`
// (exactly "7.0.0" on a tag, "7.0.0-3-gabc1234" between tags).
val sdkVersion: String = run {
    val requested = project.version.toString()
    if (requested != Project.DEFAULT_VERSION) {
        return@run requested.removePrefix("v")
    }
    val describe = runCatching {
        providers.exec {
            commandLine("git", "describe", "--tags", "--match", "v[0-9]*")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
    }.getOrDefault("")
    describe.removePrefix("v").ifEmpty { "0.0.0-SNAPSHOT" }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.nsk-bci"
                artifactId = "mindwave-sdk-android"
                version = sdkVersion
            }
        }
    }
}
