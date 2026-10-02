plugins {
    id("activitytracker.android.application")
    alias(libs.plugins.kotlin.compose)
    id("activitytracker.native.oauth")
}

android {
    namespace = "io.github.solcott.activitytracker.android"

    defaultConfig {
        applicationId = "io.github.solcott.activitytracker"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }
}

androidComponents {
    onVariants { variant ->
        // Derived from activitytracker.appviewPublicUrl, like NetworkConfigs.NATIVE_REDIRECT_SCHEME and the AppView's.
        variant.manifestPlaceholders.put("oauthRedirectScheme", nativeOAuth.redirectScheme)
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
}
