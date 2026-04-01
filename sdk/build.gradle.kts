plugins {
    id("com.android.library")
    kotlin("android")
    id("com.vanniktech.maven.publish")
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
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}

mavenPublishing {
    // Sonatype Central Portal 사용 (신 포털, 2024+)
    // OSSRH_USERNAME, OSSRH_PASSWORD 환경변수로 인증
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)

    // SIGNING_KEY, SIGNING_PASSWORD 환경변수로 GPG 서명
    signAllPublications()

    coordinates("io.github.nsk-bci", "mindwave-sdk", "2.0.0")

    pom {
        name.set("NeuroSky MindWave SDK")
        description.set("Modern Kotlin SDK for NeuroSky MindWave EEG headsets — BLE + BT Classic, no TGC dependency")
        url.set("https://github.com/nsk-bci/mindwave-sdk-android")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }

        developers {
            developer {
                id.set("nsk-bci")
                name.set("nsk-bci")
                url.set("https://github.com/nsk-bci")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/nsk-bci/mindwave-sdk-android.git")
            developerConnection.set("scm:git:ssh://github.com/nsk-bci/mindwave-sdk-android.git")
            url.set("https://github.com/nsk-bci/mindwave-sdk-android")
        }
    }
}
