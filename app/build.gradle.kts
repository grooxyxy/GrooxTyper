plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.grooxtyper.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.grooxtyper.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17 -frtti -fexceptions")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = "162005"
            keyAlias = "key0"
            keyPassword = "162005"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Model .pt/.onnx sudah terkompresi; jangan dikompresi ulang di APK.
    androidResources {
        noCompress += listOf("pt", "onnx")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose UI
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ML Kit Text Recognition (multi-script: Latin/Inggris, China, Jepang, Korea)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-chinese:16.0.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-japanese:16.0.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-korean:16.0.1")

    // ONNX Runtime Mobile: inferensi model bubble (YOLO detect) on-device.
    // v1.22.0 (mendukung opset 22): model bd.onnx berstempel opset 22 dan
    // DITOLAK ORT 1.20 (maks opset 21) dengan ORT_INVALID_ARGUMENT.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")

    // Ink API (androidx.ink) 1.0.0 -versi stabil pertama. Dipakai untuk kuas
    // bertekstur: shape + grain + dynamics ala brush pack SFX/webtoon.
    //   - ink-brush     : Brush, BrushFamily, BrushPaint.TextureLayer (tekstur)
    //   - ink-strokes   : InProgressStroke -> Stroke dari titik sentuh
    //   - ink-rendering : CanvasStrokeRenderer menggambar Stroke ke android.graphics.Canvas
    //   - ink-nativeloader: libink.so (butuh ABI arm64-v8a/x86_64, sudah di abiFilters)
    // Versi 1.0.0 dipakai, bukan alpha 1.1.0: API factory-nya
    // createWithColorIntArgb, sementara createWithComposeColor baru ada di alpha.
    val inkVersion = "1.0.0"
    implementation("androidx.ink:ink-brush:$inkVersion")
    implementation("androidx.ink:ink-strokes:$inkVersion")
    implementation("androidx.ink:ink-rendering:$inkVersion")
    implementation("androidx.ink:ink-nativeloader:$inkVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
