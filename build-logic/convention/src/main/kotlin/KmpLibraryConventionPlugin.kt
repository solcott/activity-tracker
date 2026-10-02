import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import dev.whyoleg.cryptography.gradle.CryptographyExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
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

            // cryptography-kotlin's CryptoKit provider is Swift. This adds linker options for the
            // *selected* Xcode's Swift libraries so iOS test executables link; Xcode app builds
            // don't need it.
            pluginManager.apply("dev.whyoleg.cryptography")
            extensions.configure<CryptographyExtension> { configureSwiftLinkerOpts.set(true) }

            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    namespace = derivedNamespace
                    compileSdk = libs.version("android-compileSdk").toInt()
                    minSdk = libs.version("android-minSdk").toInt()
                    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
                    withHostTest {}
                }
                jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
                iosArm64()
                iosSimulatorArm64()
                @OptIn(ExperimentalWasmDsl::class) wasmJs { browser { testTask { enabled = false } } }

                applyDefaultHierarchyTemplate()

                compilerOptions {
                    freeCompilerArgs.addAll(
                        "-Xexpect-actual-classes",
                        "-opt-in=kotlin.time.ExperimentalTime",
                        "-opt-in=kotlin.uuid.ExperimentalUuidApi",
                    )
                }
            }
        }
}
