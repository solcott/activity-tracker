plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.coreLexicon)
            api(libs.kotlinx.coroutines.core)
            api(libs.ktor.client.core)

            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.engine.defaults)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.json)
        }
        commonTest.dependencies { implementation(libs.ktor.client.mock) }
    }
}
