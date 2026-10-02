plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktfmt)
}

ktfmt {
    kotlinLangStyle()
    maxWidth.set(140)
}

dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.android.gradle.plugin)
    implementation(libs.cryptography.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.ktfmt.gradle.plugin)
    implementation(libs.sort.dependencies.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "activitytracker.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "activitytracker.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("ktfmt") {
            id = "activitytracker.ktfmt"
            implementationClass = "KtfmtConventionPlugin"
        }
        register("quality") {
            id = "activitytracker.quality"
            implementationClass = "QualityConventionPlugin"
        }
        register("nativeOAuth") {
            id = "activitytracker.native.oauth"
            implementationClass = "NativeOAuthConventionPlugin"
        }
    }
}
