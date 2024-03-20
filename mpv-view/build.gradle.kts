plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "io.viper.android.mpv.view"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags += ""
                arguments += arrayOf("-DANDROID_STL=c++_shared","-DDEPS_DIR=${projectDir}/build/deps/")
            }
        }
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
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

val extractNativeLibs = configurations.create("extractNativeLibs")

dependencies {

//    implementation(libs.androidx.core.ktx)
//    implementation(libs.androidx.appcompat)
//    implementation(libs.material)

    extractNativeLibs("com.viper.android.mpv:native-libs:0.0.1-SNAPSHOT")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

tasks.register("extractNativeLibs") {
    doLast {
        configurations["extractNativeLibs"].files.forEach {
            val file = it.absoluteFile
            var pkgName = file.name.split("-")[0]
            if (file.name.startsWith("native-libs")) {
                pkgName = "native-libs"
            }
            copy {
                from(zipTree(file))
                into("$projectDir/build/deps/$pkgName/")
                include("arm64-v8a/**")
                include("armeabi-v7a/**")
                include("x86/**")
                include("x86_64/**")
            }
        }
    }
}

tasks["preBuild"].dependsOn(tasks["extractNativeLibs"])