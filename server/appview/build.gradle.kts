plugins {
    id("activitytracker.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    application
    id("activitytracker.native.oauth")
}

application { mainClass.set("io.github.solcott.activitytracker.appview.ApplicationKt") }

// Local-stack defaults; real env vars win. APPVIEW_PUBLIC_URL defaults to the tunnel URL the native apps were built with, so the AppView
// serves matching OAuth client metadata. That makes the tunnel host its DID, so the web app (which addresses the AppView as
// did:web:localhost:<port>) is also accepted via APPVIEW_EXTRA_AUDIENCES. Resolved at execution so neither the env nor the property is
// read during configuration.
tasks.named<JavaExec>(ApplicationPlugin.TASK_RUN_NAME) {
    val configuredPublicUrl = nativeOAuth.configuredAppviewPublicUrl
    val dockerEnv = rootDir.resolve("docker/.env")
    workingDir = rootDir
    doFirst {
        with(this as JavaExec) {
            if ("APPVIEW_PORT" !in environment) environment("APPVIEW_PORT", "8081")
            if ("APPVIEW_PUBLIC_URL" !in environment) configuredPublicUrl.orNull?.let { environment("APPVIEW_PUBLIC_URL", it) }
            if ("APPVIEW_PUBLIC_URL" in environment && "APPVIEW_EXTRA_AUDIENCES" !in environment) {
                environment("APPVIEW_EXTRA_AUDIENCES", "did:web:localhost%3A${environment["APPVIEW_PORT"]}")
            }
            fun fromDockerEnv(key: String): String? =
                dockerEnv
                    .takeIf { it.isFile }
                    ?.readLines()
                    ?.map(String::trim)
                    ?.firstOrNull { it.startsWith("$key=") }
                    ?.substringAfter('=')
                    ?.takeIf(String::isNotBlank)
            // The signing key scripts/dev.sh generates into docker/.env, so the AppView can sync athletes' tier spaces.
            if ("APPVIEW_SIGNING_KEY" !in environment) fromDockerEnv("APPVIEW_SIGNING_KEY")?.let { environment("APPVIEW_SIGNING_KEY", it) }
            // The AppView's account on the local Ozone's team (docker/scripts/setup-labeler.sh).
            for (key in listOf("APPVIEW_TRIAGE_DID", "APPVIEW_TRIAGE_PASSWORD")) {
                if (key !in environment) fromDockerEnv(key)?.let { environment(key, it) }
            }
            // The local Ozone's labeler account (docker/scripts/setup-labeler.sh), at the port compose publishes.
            if ("LABELER_DID" !in environment) {
                fromDockerEnv("OZONE_SERVER_DID")?.let {
                    environment("LABELER_DID", it)
                    if ("LABELER_URL" !in environment) environment("LABELER_URL", "http://localhost:3010")
                }
            }
        }
    }
}

dependencies {
    // Not used by a route yet: keeps the FIT parser proven on the server classpath for when the AppView reads activity files.
    implementation(projects.coreActivityfile)
    implementation(projects.coreAtproto)
    implementation(libs.bouncycastle)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.hikari)
    // Decodes labelers' subscribeLabels frames (DAG-CBOR); the server only, clients never see CBOR.
    implementation(libs.jackson.dataformat.cbor)
    implementation(libs.kermit)
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
    testImplementation(libs.garmin.fit)
    testImplementation(libs.h2)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.server.test.host)
}
