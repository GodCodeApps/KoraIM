plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.kora.imcore"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    buildFeatures {
        aidl = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.netty)
    implementation(libs.gson)
    implementation(libs.roomKtx)
    implementation(libs.livedataKtx)

    annotationProcessor(libs.roomCompiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

}