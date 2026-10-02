plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    id("activitytracker.quality")
}

application { mainClass.set("io.github.solcott.activitytracker.appview.ApplicationKt") }

kotlin {
    jvmToolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
    compilerOptions { freeCompilerArgs.addAll("-opt-in=kotlin.time.ExperimentalTime") }
}

dependencies {
    implementation(projects.core.atproto)
    implementation(libs.bouncycastle)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.hikari)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.logback)
    implementation(libs.postgresql)

    testImplementation(kotlin("test"))
    testImplementation(libs.h2)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
}

tasks.named<JavaExec>("run") {
    // Local-stack defaults; override with real env vars in other environments.
    environment("APPVIEW_PORT", System.getenv("APPVIEW_PORT") ?: "8081")
    workingDir = rootDir
}
