plugins { id("activitytracker.kmp.compose") }

// Every string and drawable the UI modules and app shells read, in one place. The Res class keeps the package it had in :composeApp, so
// moving the resources here changed no imports; public so the other modules can read it.
compose.resources {
    publicResClass = true
    packageOfResClass = "io.github.solcott.activitytracker.composeApp.resources"
}
