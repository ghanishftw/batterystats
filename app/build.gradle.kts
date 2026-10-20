import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Signing comes from keystore.properties locally, or from environment variables in CI.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val storeFilePath = signingValue("storeFile", "KEYSTORE_FILE")
val hasSigningKey = storeFilePath != null && rootProject.file(storeFilePath).exists()
// Release builds must be signed. Only F-Droid's build server, which signs with its own key,
// opts out with -PallowUnsigned=true.
val allowUnsigned = providers.gradleProperty("allowUnsigned").orNull == "true"

android {
    namespace = "com.gnsh.batterystats"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gnsh.batterystats"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasSigningKey) {
            create("release") {
                storeFile = rootProject.file(storeFilePath!!)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigningKey) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            // Falls back to the standard debug key when the release key isn't available.
            if (hasSigningKey) signingConfig = signingConfigs.getByName("release")
        }
    }

    dependenciesInfo {
        // Google-encrypted dependency metadata blocks F-Droid/IzzyOnDroid scanners.
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures { compose = true }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.graphics.shapes)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
}

// Fail fast instead of quietly producing an unsigned release APK.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    // A plain local Boolean keeps the action configuration-cache friendly.
    val signed: Boolean = hasSigningKey || allowUnsigned
    doFirst {
        check(signed) {
            "No signing key: add keystore.properties or set KEYSTORE_FILE/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD"
        }
    }
}
