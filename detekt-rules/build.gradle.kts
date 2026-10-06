plugins { id("activitytracker.kotlin.jvm") }

// The repo's own detekt rules. activitytracker.quality adds this module to every other module's detektPlugins.
dependencies {
    compileOnly(libs.detekt.api)

    testImplementation(kotlin("test"))
    testImplementation(libs.detekt.api)
    testImplementation(libs.detekt.test)
}
