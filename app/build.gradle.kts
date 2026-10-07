plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android { namespace = "com.samfoy.batomon"; compileSdk = 36
    defaultConfig { applicationId = "com.samfoy.batomon"; minSdk = 26; targetSdk = 35; versionCode = 4; versionName = "0.4.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    signingConfigs {
        create("stableRelease") {
            val keystorePath = System.getenv("BATOMON_RELEASE_KEYSTORE")
            if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("BATOMON_RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("BATOMON_RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("BATOMON_RELEASE_KEY_PASSWORD")
            }
        }
    }
    buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("stableRelease"); proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
}
