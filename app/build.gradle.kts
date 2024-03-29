plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
//    id("kotlin-android-extensions")
}

android {
    namespace = "com.example.facedetect"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.facedetect"
        minSdk = 24
        targetSdk = 31
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation ("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation ("androidx.camera:camera-core:1.3.1")
    implementation ("com.google.mlkit:face-detection:16.1.5")
    implementation ("com.google.mlkit:face-mesh-detection:16.0.0-beta1")
    implementation ("com.google.mlkit:image-labeling:17.0.7")
    implementation ("com.google.mlkit:object-detection:17.0.0")
    // To recognize Latin script
    implementation ("com.google.mlkit:text-recognition:16.0.0")
//    implementation ("com.google.guava:guava:27.1-android")
//    implementation ("com.google.android.gms:play-services-mlkit-face-detection:17.1.0")

    //image classification


    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}