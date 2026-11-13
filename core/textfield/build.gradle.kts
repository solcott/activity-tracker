plugins {
    id("activitytracker.kmp.library")
    alias(libs.plugins.kotlin.compose)
}

// The text field state a screen's State holds: Compose's own class everywhere but iOS, where it is a class of snapshot state Swift export
// can carry (dev/ios-swift-interop). Its own module because both the contracts and the design system's field take it, and neither may
// depend on
// the other.
kotlin {
    sourceSets {
        // api: @Stable and @Composable are in this module's signatures.
        commonMain.dependencies { api(libs.compose.runtime) }
        // api: off iOS the type is Compose's TextFieldState, which callers edit and pass to Compose fields.
        nonAppleMain.dependencies { api(libs.compose.foundation) }
        iosMain.dependencies {
            // api: the iOS class publishes its text as a Flow, which Swift iterates.
            api(libs.kotlinx.coroutines.core)

            // rememberTextFieldState saves the text with rememberSaveable. No Compose Foundation: nothing on iOS draws a Compose field.
            implementation(libs.compose.runtime.saveable)
        }
    }
}
