plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktfmt)
}

ktfmt {
    kotlinLangStyle()
    maxWidth.set(140)
}

val testPluginClasspath: Configuration by configurations.creating

dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.android.gradle.plugin)
    implementation(libs.cryptography.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.ktfmt.gradle.plugin)
    implementation(libs.sort.dependencies.gradle.plugin)

    // java-gradle-plugin (via kotlin-dsl) already adds gradleTestKit() to the test classpath.
    testImplementation(kotlin("test-junit"))

    testPluginClasspath(libs.android.gradle.plugin)
    testPluginClasspath(libs.compose.compiler.gradle.plugin)
    testPluginClasspath(libs.compose.gradle.plugin)
    testPluginClasspath(libs.kotlin.gradle.plugin)
}

// KGP and the Compose compiler plugin are compileOnly (each build supplies its own), but TestKit fixtures need them to apply the
// convention plugins.
tasks.pluginUnderTestMetadata { pluginClasspath.from(testPluginClasspath) }

// Fixtures that apply the KMP conventions need the real catalog, which those plugins read versions and libraries from.
tasks.test {
    val catalog = layout.projectDirectory.file("../../gradle/libs.versions.toml")
    inputs.file(catalog).withPropertyName("versionCatalog")
    systemProperty("activitytracker.versionCatalog", catalog.asFile.absolutePath)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "activitytracker.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("kmpLibrary") {
            id = "activitytracker.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpWeb") {
            id = "activitytracker.kmp.web"
            implementationClass = "KmpWebConventionPlugin"
        }
        register("kotlinJvm") {
            id = "activitytracker.kotlin.jvm"
            implementationClass = "KotlinJvmConventionPlugin"
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
