import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.withAndroid
import dev.whyoleg.cryptography.gradle.CryptographyExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Shared KMP setup for every client-side module: Android, iOS (device + Apple-silicon simulator), Web (wasmJs), plus a JVM target used for
 * fast unit tests and for code shared with the AppView.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            pluginManager.apply("activitytracker.quality")

            // cryptography-kotlin's CryptoKit provider is Swift. This adds linker options for the
            // *selected* Xcode's Swift libraries so iOS test executables link; Xcode app builds
            // don't need it.
            pluginManager.apply("dev.whyoleg.cryptography")
            extensions.configure<CryptographyExtension> { configureSwiftLinkerOpts.set(true) }

            extensions.configure<KotlinMultiplatformExtension> {
                amazonJdkToolchain(target)
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    namespace = derivedNamespace
                    compileSdk = libs.version("android-compileSdk").toInt()
                    minSdk = libs.version("android-minSdk").toInt()
                    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
                    withHostTest {}
                }
                // Only Android needs 17; the JVM target (tests and code shared with the AppView) runs on the toolchain JDK.
                jvm { compilerOptions { jvmTarget.set(JvmTarget.fromTarget(libs.version("jdk"))) } }
                iosArm64()
                iosSimulatorArm64()
                // Libraries only need a wasm test environment, and wasm tests are disabled repo-wide. Node rather
                // than browser keeps Compose's browser-test bundling check (it hooks *BrowserTest tasks, and fails
                // for modules without an executable) out of the graph; the app bundles in :webApp's browser target.
                @OptIn(ExperimentalWasmDsl::class) wasmJs { nodejs { testTask { enabled = false } } }

                // Experimental KGP/AGP APIs (re-check on upgrades): the hierarchy-template DSL is the only way to declare the
                // custom intermediate source sets below, and withAndroid() is how AGP's KMP library target joins a group.
                @OptIn(ExperimentalKotlinGradlePluginApi::class)
                applyDefaultHierarchyTemplate {
                    common {
                        group("commonJvm") {
                            withJvm()
                            @Suppress("UnstableApiUsage") withAndroid()
                        }
                        group("web") {
                            withJs()
                            withWasmJs()
                        }
                        // group("native") rather than withNative(), so the shared nativeMain/iosMain (not just
                        // the per-target leaves) depend on these and can see their declarations.
                        group("nonWeb") {
                            @Suppress("UnstableApiUsage") withAndroid()
                            group("native")
                            withJvm()
                        }
                        group("nonAndroid") {
                            group("native")
                            withJvm()
                            withJs()
                            withWasmJs()
                        }
                        // Desktop and web: no GPS recording, only manual entry.
                        group("nonMobile") {
                            withJvm()
                            withJs()
                            withWasmJs()
                        }
                    }
                }

                sourceSets.getByName("commonTest").dependencies {
                    implementation(kotlin("test"))
                    implementation(libs.findLibrary("kotlinx-coroutines-test").get())
                }

                compilerOptions {
                    freeCompilerArgs.addAll(
                        "-Xexpect-actual-classes",
                        OPT_IN_EXPERIMENTAL_TIME,
                        "-opt-in=kotlin.uuid.ExperimentalUuidApi",
                    )
                }
            }
        }
}
