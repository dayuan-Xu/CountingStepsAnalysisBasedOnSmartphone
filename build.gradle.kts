plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.finalapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.finalapp"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // 设置支持的SO库架构（开发者可以根据需要，选择一个或多个平台的so）
            abiFilters.addAll(listOf("armeabi", "armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation("com.baidu.lbsyun:BaiduMapSDK_Map:7.6.4")
    implementation("com.baidu.lbsyun:BaiduMapSDK_Util:7.6.4")
    implementation("com.baidu.lbsyun:BaiduMapSDK_Location_All:9.6.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Room 数据库
    val room_version = "2.4.3"
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")

    // Gson 用于序列化
    implementation("com.google.code.gson:gson:2.9.0")
}

