import com.android.build.api.variant.HostTestBuilder
import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask

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

    // Reference screenshots of every screen and shared component, from :app-fixtures' samples in AppPreview (dev/screenshot-tests).
    testOptions {
        // The preview renderer needs the app's compiled resources (themes, strings).
        unitTests.isIncludeAndroidResources = true
        screenshotTests.create("screenshotTest") {
            engineVersion = libs.versions.screenshot.get()
            targetVariants.add("debug")
            // Layoutlib renders the same sources identically on one machine; this only absorbs anti-aliasing noise.
            imageDifferenceThreshold = 0.0001f
            dependencies {
                implementation(projects.appCommonUi)
                implementation(projects.appFixtures)
                implementation(projects.featureFeedfilterUi)
                implementation(projects.featureMediaUi)
                implementation(projects.featureReportUi)
                implementation(projects.featureSearchUi)
                implementation(projects.uiDesignsystem)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui.tooling)
                implementation(libs.screenshot.validation.api)
            }
        }
    }
}

androidComponents {
    // AGP registers Compose Preview tasks for every variant, not only the suite's, and they fail to configure without unit tests.
    beforeVariants(selector().withBuildType("release")) { variant -> variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true }
    onVariants { variant ->
        // Derived from activitytracker.appviewPublicUrl, like NetworkConfigs.NATIVE_REDIRECT_SCHEME and the AppView's.
        variant.manifestPlaceholders.put("oauthRedirectScheme", nativeOAuth.redirectScheme)
        // Unit tests merge the app manifest too, now that they include Android resources (for the screenshot suite).
        variant.hostTests.values.forEach { it.manifestPlaceholders.put("oauthRedirectScheme", nativeOAuth.redirectScheme) }
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.coreData)
    implementation(projects.coreLocation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
}

// Layoutlib renders every screenshot in one JVM, which outgrows Test's default 512m heap.
tasks.withType<Test>().matching { "ScreenshotTest" in it.name }.configureEach { maxHeapSize = "4g" }

// AGP doesn't add the suite to check.
tasks.named("check") { dependsOn("testDebugScreenshotTestDefaultTestSuite") }

// The ktfmt plugin formats AGP's classic source sets, not a test suite's.
val screenshotTestSources = fileTree("src/screenshotTest/kotlin")
val ktfmtCheckScreenshotTest = tasks.register<KtfmtCheckTask>("ktfmtCheckScreenshotTest") { source = screenshotTestSources }

tasks.register<KtfmtFormatTask>("ktfmtFormatScreenshotTest") { source = screenshotTestSources }

tasks.named("ktfmtCheck") { dependsOn(ktfmtCheckScreenshotTest) }

tasks.named("check") { dependsOn(ktfmtCheckScreenshotTest) }
