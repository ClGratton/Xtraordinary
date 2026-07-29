plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.screenshot)
}

android {
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    namespace = "com.xteink.companion"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.xteink.companion"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("community") {
            dimension = "distribution"
            applicationIdSuffix = ".community"
            versionNameSuffix = "-community"
            buildConfigField("boolean", "PLAY_DISTRIBUTION", "false")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "PLAY_DISTRIBUTION", "true")
            buildConfigField(
                "String",
                "PRO_PRODUCT_ID",
                "\"${providers.gradleProperty("XTRAORDINARY_PRO_PRODUCT_ID").getOrElse("xtraordinary_pro")}\"",
            )
            buildConfigField(
                "String",
                "REWARDED_AD_UNIT_ID",
                "\"${providers.gradleProperty("XTRAORDINARY_REWARDED_AD_UNIT_ID").getOrElse("ca-app-pub-3940256099942544/5224354917")}\"",
            )
            buildConfigField(
                "String",
                "ENTITLEMENT_BACKEND_URL",
                "\"${providers.gradleProperty("XTRAORDINARY_ENTITLEMENT_BACKEND_URL").getOrElse("")}\"",
            )
            manifestPlaceholders["xtraordinaryAdMobAppId"] =
                providers.gradleProperty("XTRAORDINARY_ADMOB_APP_ID")
                    .getOrElse("ca-app-pub-3940256099942544~3347511713")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    "playImplementation"(libs.play.billing)
    "playImplementation"(libs.google.mobile.ads)
    "playImplementation"(libs.google.ump)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(platform(libs.androidx.compose.bom))
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

tasks.matching { it.name == "assemblePlayRelease" || it.name == "bundlePlayRelease" }.configureEach {
    doFirst {
        val appId = providers.gradleProperty("XTRAORDINARY_ADMOB_APP_ID").orNull
        val adUnitId = providers.gradleProperty("XTRAORDINARY_REWARDED_AD_UNIT_ID").orNull
        val backendUrl = providers.gradleProperty("XTRAORDINARY_ENTITLEMENT_BACKEND_URL").orNull
        require(!appId.isNullOrBlank() && !appId.contains("3940256099942544")) {
            "Set a production XTRAORDINARY_ADMOB_APP_ID for Play release builds."
        }
        require(!adUnitId.isNullOrBlank() && !adUnitId.contains("3940256099942544")) {
            "Set a production XTRAORDINARY_REWARDED_AD_UNIT_ID for Play release builds."
        }
        require(!backendUrl.isNullOrBlank() && backendUrl.startsWith("https://")) {
            "Set an HTTPS XTRAORDINARY_ENTITLEMENT_BACKEND_URL for Play release builds."
        }
    }
}
