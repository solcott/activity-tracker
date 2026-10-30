plugins { id("activitytracker.kmp.compose") }

// The design system: the adaptive wrappers every screen builds from (ui/adaptive), the per-platform theme, charts and test tags. No app
// contracts and no feature code; :core-data only for number formatting.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.uiResources)
            // api: the adaptive wrappers take and return Calf and Haze types.
            api(libs.calf.ui)
            api(libs.haze)
            api(libs.kotlinx.collections.immutable)
            api(libs.vico.multiplatform)

            implementation(projects.coreData)
            // SF Symbols-style glyphs for the Compose-drawn iOS chrome (the native bars take SF Symbol names instead).
            implementation(libs.calf.cupertino.icons)
            implementation(libs.compose.material.icons.core)
            implementation(libs.haze.blur)
        }
    }
}
