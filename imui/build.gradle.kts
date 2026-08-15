plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.kora.imui"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
//    implementation(libs.androidx.appcompat)
//    implementation(libs.androidx.core.ktx)
//    implementation(libs.material)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.refreshLayout)
    implementation(libs.glide)
    implementation(libs.pictureselector)
    api(project(":imcore"))
}
