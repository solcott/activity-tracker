import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Android application setup matching [KmpLibraryConventionPlugin]: SDK levels from the version catalog, the Amazon JDK toolchain and JVM 17
 * bytecode.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("activitytracker.quality")

            extensions.configure<ApplicationExtension> {
                compileSdk = libs.version("android-compileSdk").toInt()
                defaultConfig {
                    minSdk = libs.version("android-minSdk").toInt()
                    targetSdk = libs.version("android-targetSdk").toInt()
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                // R8 shrinks, optimizes and obfuscates release builds, with AGP's default rules plus the module's src/*/keepRules.
                buildTypes.named("release") { optimization { enable = true } }
            }

            // AGP 9's built-in Kotlin registers the `kotlin` extension.
            extensions.configure<KotlinAndroidProjectExtension> {
                amazonJdkToolchain(target)
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }
        }
    }
}
