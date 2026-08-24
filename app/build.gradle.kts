import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.screenshot)
}

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

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
        versionCode = 51
        versionName = "0.2.0-dev50"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    val oemPermissionAcknowledged = providers
        .gradleProperty("xtraordinaryXteinkOemPermissionAcknowledged")
        .orNull
        ?.equals("true", ignoreCase = true) == true
    buildTypes {
        getByName("debug") {
            // Private maintenance builds retain the known recovery route.
            buildConfigField("boolean", "XTEINK_OEM_FIRMWARE_ALLOWED", "true")
        }
        release {
            isMinifyEnabled = false
            // Public/release variants fail closed unless written OEM permission
            // has been recorded and the release invocation opts in explicitly.
            buildConfigField(
                "boolean",
                "XTEINK_OEM_FIRMWARE_ALLOWED",
                oemPermissionAcknowledged.toString(),
            )
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

    flavorDimensions += "distribution"
    productFlavors {
        create("community") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"community\"")
            buildConfigField("String", "FLIGHT_STATUS_PROXY_ENDPOINT", "\"\"")
            buildConfigField("String", "PLAY_AD_FREE_PRODUCT_ID", "\"\"")
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"\"")
            buildConfigField("String", "ENTITLEMENT_ENDPOINT", "\"\"")
            buildConfigField("String", "COMMUNITY_SOURCE_URL", "\"https://github.com/ClGratton/Xtraordinary\"")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"play\"")
            val flightStatusEndpoint = providers.gradleProperty("xtraordinaryFlightStatusProxy").orNull.orEmpty()
            val playProductId = providers.gradleProperty("xtraordinaryPlayAdFreeProductId")
                .orNull.orEmpty().ifBlank { "xtraordinary_ad_free" }
            val admobAppId = providers.gradleProperty("xtraordinaryAdMobAppId")
                .orNull.orEmpty().ifBlank { "ca-app-pub-3940256099942544~3347511713" }
            val admobBannerId = providers.gradleProperty("xtraordinaryAdMobBannerId")
                .orNull.orEmpty().ifBlank { "ca-app-pub-3940256099942544/9214589741" }
            val entitlementEndpoint = providers.gradleProperty("xtraordinaryEntitlementEndpoint").orNull.orEmpty()
            val communitySourceUrl = providers.gradleProperty("xtraordinaryCommunitySourceUrl")
                .orNull.orEmpty().ifBlank { "https://github.com/ClGratton/Xtraordinary" }
            buildConfigField("String", "FLIGHT_STATUS_PROXY_ENDPOINT", flightStatusEndpoint.asBuildConfigString())
            buildConfigField("String", "PLAY_AD_FREE_PRODUCT_ID", playProductId.asBuildConfigString())
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", admobBannerId.asBuildConfigString())
            buildConfigField("String", "ENTITLEMENT_ENDPOINT", entitlementEndpoint.asBuildConfigString())
            buildConfigField("String", "COMMUNITY_SOURCE_URL", communitySourceUrl.asBuildConfigString())
            manifestPlaceholders["adMobAppId"] = admobAppId
        }
    }

    packaging {
        // Keep one copy of conventional duplicate licence entries instead of
        // deleting them from every APK.
        resources.pickFirsts += setOf("/META-INF/AL2.0", "/META-INF/LGPL2.1")
    }

    sourceSets.getByName("main").assets.srcDir(rootProject.file("release-notices"))
}

tasks.register("writeLegalRuntimeDependencyReports") {
    val reportDirectory = layout.buildDirectory.dir("legal/runtime-dependencies")
    outputs.dir(reportDirectory)

    doLast {
        listOf("communityReleaseRuntimeClasspath", "playReleaseRuntimeClasspath").forEach { configurationName ->
            val coordinates = configurations.getByName(configurationName)
                .incoming.resolutionResult.allComponents
                .mapNotNull { component ->
                    val id = component.id as? ModuleComponentIdentifier ?: return@mapNotNull null
                    "${id.group}\t${id.module}\t${id.version}"
                }
                .distinct()
                .sorted()
            reportDirectory.get().file("$configurationName.tsv").asFile.apply {
                parentFile.mkdirs()
                writeText("group\tmodule\tversion\n${coordinates.joinToString("\n")}\n")
            }
        }
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.play.services.auth)
    implementation("com.google.zxing:core:3.5.3")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(platform(libs.androidx.compose.bom))
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    "playImplementation"(libs.play.billing.ktx)
    "playImplementation"(libs.google.mobile.ads)
    "playImplementation"(libs.google.ump)
}
