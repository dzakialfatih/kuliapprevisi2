plugins {
//    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.kuliapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.kuliapp"
        minSdk = 24
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation ("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation(libs.circleimageview)

    // Firebase BOM and features
//    implementation(platform("com.google.firebase:firebase-bom:33.14.0"))
    implementation("com.google.firebase:firebase-analytics")
//    implementation("com.google.firebase:firebase-auth")
//    implementation(libs.google.firebase.firestore.ktx)
//    implementation(platform(libs.firebase.bom))
//    implementation("libs.firebase.auth.ktx")
//    implementation("libs.firebase.firestore.ktx")
//    implementation(libs.firebase.storage.ktx)
//    implementation(libs.firebase.messaging.ktx)
    implementation (platform("com.google.firebase:firebase-bom:32.3.1"))

    // Firebase Dependencies
    implementation ("com.google.firebase:firebase-auth-ktx")
//    implementation ("com.google.firebase:firebase-firestore-ktx")
    implementation ("com.google.firebase:firebase-firestore:25.1.4")

    // Play Services - gunakan versi yang compatible
    implementation ("com.google.android.gms:play-services-auth:20.6.0")
    implementation(libs.firebase.firestore)
    implementation(libs.google.firebase.storage.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
