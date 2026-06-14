plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.simplespider.dy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.simplespider.dy"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"https://10.0.2.2:8000/api\""
        )
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
        buildConfig = true
    }
}

val bundledCaSource = rootProject.file("trust-ca/api_trust_ca.pem")
val bundledCaDest = file("src/main/res/raw/api_trust_ca.pem")
val networkSecurityConfig = file("src/main/res/xml/network_security_config.xml")

tasks.register("prepareBundledCa") {
    doLast {
        val hasBundledCa = bundledCaSource.exists()
        if (hasBundledCa) {
            bundledCaDest.parentFile.mkdirs()
            bundledCaSource.copyTo(bundledCaDest, overwrite = true)
        } else if (bundledCaDest.exists()) {
            bundledCaDest.delete()
        }

        val bundledCaLine = if (hasBundledCa) {
            "            <certificates src=\"@raw/api_trust_ca\" />\n"
        } else {
            ""
        }

        networkSecurityConfig.parentFile.mkdirs()
        networkSecurityConfig.writeText(
            buildString {
                appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                appendLine("""<network-security-config>""")
                appendLine("""    <base-config cleartextTrafficPermitted="false">""")
                appendLine("""        <trust-anchors>""")
                appendLine("""            <certificates src="system" />""")
                appendLine("""            <certificates src="user" />""")
                append(bundledCaLine)
                appendLine("""        </trust-anchors>""")
                appendLine("""    </base-config>""")
                appendLine("""</network-security-config>""")
            },
        )
    }
}

tasks.named("preBuild").configure {
    dependsOn("prepareBundledCa")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
