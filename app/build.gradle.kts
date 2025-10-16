plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")

    // Corrected the KSP version to a stable version compatible with your other libraries
    id("com.google.devtools.ksp") version "2.2.20-2.0.2"
}

android {
    signingConfigs {
        getByName("debug") {

        }
    }
    namespace = "com.message.bulksend"
    // Changed compileSdk to 34 for better stability with current libraries
    compileSdk = 36

    defaultConfig {
        applicationId = "com.message.bulksendtest"
        minSdk = 29
        // Changed targetSdk to 34 to match compileSdk
        targetSdk = 36
        versionCode = 3
        versionName = "3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      
        signingConfig = signingConfigs.getByName("debug")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        // This version is compatible with the updated Kotlin and BOM versions
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildToolsVersion = "36.1.0"
}

// Added this block for Room schema location, which is a best practice
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    releaseImplementation(libs.androidx.ui.test.manifest)

    // Material Design libraries
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.compose)


// Room Database
    val room_version = "2.8.1"
    implementation("androidx.room:room-runtime:${room_version}")
    ksp("androidx.room:room-compiler:$room_version")
    // Kotlin extensions + Coroutines support
    implementation("androidx.room:room-ktx:${room_version}")

    // Using stable versions for these libraries
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("com.google.code.gson:gson:2.10.1")

// Firebase (using BoM - Bill of Materials)

    // 1. Firebase Android BoM (Bill of Materials)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))

    // 2. Firebase Authentication KTX Library
    // यह Firebase Auth की core functionality प्रदान करता है,
    // जिसमें Email/Password, Anonymous, और दूसरे कई methods शामिल हैं।
    implementation("com.google.firebase:firebase-auth-ktx")

    // 3. Google Sign-In SDK (अगर आप Google Sign-In का इस्तेमाल कर रहे हैं)
    // `GoogleAuthProvider` जैसे classes के लिए यह ज़रूरी है।
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // 4. Firebase Firestore KTX Library
    // यह Firestore database के लिए ज़रूरी है
    implementation("com.google.firebase:firebase-firestore-ktx")

    // 4.5. Firebase Analytics KTX Library
    // यह Firebase Analytics के लिए ज़रूरी है
    implementation("com.google.firebase:firebase-analytics-ktx")

    // 5. Firebase Storage for backup feature
    implementation("com.google.firebase:firebase-storage-ktx:21.0.2")


    // 5. Coroutines support for Firebase
    implementation(libs.kotlinx.coroutines.play.services)

    // Razorpay SDK
    implementation("com.razorpay:checkout:1.6.40")
    
    // OkHttp for API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Google Play Billing Library (In-App Purchase)
    val billing_version = "8.0.0"
    implementation("com.android.billingclient:billing:$billing_version")
    implementation("com.android.billingclient:billing-ktx:$billing_version")











}