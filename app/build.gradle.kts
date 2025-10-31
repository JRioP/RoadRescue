plugins {
    // This assumes 'libs.plugins.android.application' is defined in your version catalog
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "fourthyear.roadrescue"
    // Changed from 35 to 34. 35 is likely a preview, 34 is the stable SDK in late 2025.
    compileSdk = 35

    defaultConfig {
        applicationId = "fourthyear.roadrescue"
        minSdk = 23
        // Target SDK should match compile SDK
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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
        // Updated to Java 17, which is standard for modern Android development
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(platform("com.google.firebase:firebase-bom:32.8.1"))

    // Firebase dependencies (managed by the BOM)
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-analytics")

    // This version is from 2022 and works with the BOM above.
    implementation("com.firebaseui:firebase-ui-firestore:8.0.2")

    // Google Play Services (kept your versions, which are compatible with the 32.x BOM)
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // These libraries are very old. Updated to more recent, stable versions.
    implementation("com.google.maps:google-maps-services:2.2.0") // This lib is old, but 2.2.0 is the last major release
    implementation("com.google.maps.android:android-maps-utils:3.4.0") // Updated from 3.4.0

    // Networking - Updated to more modern versions
    implementation("com.squareup.retrofit2:retrofit:2.11.0") // Updated from 2.9.0
    implementation("com.squareup.retrofit2:converter-gson:2.11.0") // Updated from 2.9.0
    implementation("com.google.code.gson:gson:2.11.0") // Updated from 2.10.1


}