plugins {
    id("activitytracker.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    application
    id("activitytracker.native.oauth")
}

application { mainClass.set("io.github.solcott.activitytracker.appview.ApplicationKt") }

// Local-stack defaults; real env vars win. APPVIEW_PUBLIC_URL defaults to the URL the native apps were built with (the device host's
// AppView
// sibling in device mode, else the quick tunnel's), so the AppView serves matching OAuth client metadata and that host is its DID. In
// device mode every client addresses it by that DID; otherwise the web and desktop apps address it as did:web:localhost:<port>, which is
// also accepted via APPVIEW_EXTRA_AUDIENCES. Resolved at execution so neither the env nor the properties are read during configuration.
tasks.named<JavaExec>(ApplicationPlugin.TASK_RUN_NAME) {
    val publicUrl = nativeOAuth.effectiveAppviewPublicUrl
    val deviceMode = nativeOAuth.deviceMode
    val dockerEnv = rootDir.resolve("docker/.env")
    workingDir = rootDir
    doFirst {
        with(this as JavaExec) {
            if ("APPVIEW_PORT" !in environment) environment("APPVIEW_PORT", "8081")
            if ("APPVIEW_PUBLIC_URL" !in environment) publicUrl.orNull?.let { environment("APPVIEW_PUBLIC_URL", it) }
            if ("APPVIEW_PUBLIC_URL" in environment && "APPVIEW_EXTRA_AUDIENCES" !in environment && !deviceMode.get()) {
                environment("APPVIEW_EXTRA_AUDIENCES", "did:web:localhost%3A${environment["APPVIEW_PORT"]}")
            }
            // Every PDS and did:web on the local stack is localhost, which the AppView otherwise refuses (identity/outbound-requests).
            if ("APPVIEW_ALLOW_PRIVATE_ADDRESSES" !in environment) environment("APPVIEW_ALLOW_PRIVATE_ADDRESSES", "true")
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
            // How many athletes make a proposed segment active, when docker/.env sets it.
            if ("SEGMENT_ACTIVE_ATHLETES" !in environment)
                fromDockerEnv("SEGMENT_ACTIVE_ATHLETES")?.let { environment("SEGMENT_ACTIVE_ATHLETES", it) }
            // The segment importer's account and how many riders make a staged candidate ridden, when docker/.env sets them.
            for (key in listOf("SEGMENT_IMPORTER_DID", "SEGMENT_CANDIDATE_RIDERS")) {
                if (key !in environment) fromDockerEnv(key)?.let { environment(key, it) }
            }
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
    // Segment geometry and cells (SegmentGeometry, SegmentCells), shared with clients.
    implementation(projects.coreLocation)
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
    implementation(libs.ktor.client.okhttp)
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
