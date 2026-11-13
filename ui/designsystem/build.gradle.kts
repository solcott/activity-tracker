plugins { id("activitytracker.kmp.compose") }

// The design system: the adaptive wrappers every screen builds from (ui/adaptive), the per-platform theme, charts and test tags. No app
// contracts and no feature code; :core-data for number formatting and the charts' shared rules.
kotlin {
    sourceSets {
        commonMain.dependencies {
            // The charts take its ChartAxis and BandStop, the rules every UI's charts share.
            api(projects.coreData)
            // AppTextField takes the app's TextFieldState, as the contracts do.
            api(projects.coreTextfield)
            api(projects.uiResources)
            // api: the adaptive wrappers take and return Calf types.
            api(libs.calf.ui)
            api(libs.kotlinx.collections.immutable)
            api(libs.vico.multiplatform)

            // SF Symbols-style glyphs for the Compose-drawn iOS chrome (the native bars take SF Symbol names instead).
            implementation(libs.calf.cupertino.icons)
            implementation(libs.compose.material.icons.core)
        }
    }
}
