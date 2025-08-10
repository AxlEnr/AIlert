plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.ailert"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ailert"
        minSdk = 30
        targetSdk = 34
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Usa las dependencias oficiales de TensorFlow Lite (versión estable más reciente)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")  // Runtime principal
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")  // Utilidades para pre/post-procesamiento
    implementation("org.tensorflow:tensorflow-lite-task-audio:0.4.4")  // Procesamiento de audio
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}