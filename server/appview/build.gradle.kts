plugins {
    id("activitytracker.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    application
    id("activitytracker.native.oauth")
}

application { mainClass.set("io.github.solcott.activitytracker.appview.ApplicationKt") }

// Every test class starts its own H2 database and Flyway run, and the suite outgrew Gradle's default 512 MB test heap.
tasks.test { maxHeapSize = "1g" }

/**
 * The local-stack defaults the AppView runs with, as the variables to add to the environment it would otherwise get: real env vars win.
 * APPVIEW_PUBLIC_URL defaults to the URL the native apps were built with (the device host's AppView sibling in device mode, else the quick
 * tunnel's), so the AppView serves matching OAuth client metadata and that host is its DID. In device mode every client addresses it by
 * that DID; otherwise the web and desktop apps address it as did:web:localhost:<port>, which is also accepted via APPVIEW_EXTRA_AUDIENCES.
 * Called at execution, so neither the env nor the properties are read during configuration.
 */
fun localStackEnvironment(
    publicUrl: Provider<String>,
    deviceMode: Provider<Boolean>,
    rootDir: File,
): (Map<String, String>) -> Map<String, String> = { current ->
    val added = linkedMapOf<String, String>()
    fun default(key: String, value: () -> String?) {
        if (key !in current && key !in added) value()?.let { added[key] = it }
    }
    val dockerEnv = rootDir.resolve("docker/.env")
    fun fromDockerEnv(key: String): String? =
        dockerEnv
            .takeIf { it.isFile }
            ?.readLines()
            ?.map(String::trim)
            ?.firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')
            ?.takeIf(String::isNotBlank)

    default("APPVIEW_PORT") { "8081" }
    default("APPVIEW_PUBLIC_URL") { publicUrl.orNull }
    if (("APPVIEW_PUBLIC_URL" in current || "APPVIEW_PUBLIC_URL" in added) && !deviceMode.get()) {
        default("APPVIEW_EXTRA_AUDIENCES") { "did:web:localhost%3A${current["APPVIEW_PORT"] ?: added["APPVIEW_PORT"]}" }
    }
    // Every PDS and did:web on the local stack is localhost, which the AppView otherwise refuses (identity/outbound-requests).
    default("APPVIEW_ALLOW_PRIVATE_ADDRESSES") { "true" }
    // The signing key scripts/dev.sh generates into docker/.env, so the AppView can sync athletes' tier spaces.
    default("APPVIEW_SIGNING_KEY") { fromDockerEnv("APPVIEW_SIGNING_KEY") }
    // How many athletes make a proposed segment active, when docker/.env sets it.
    default("SEGMENT_ACTIVE_ATHLETES") { fromDockerEnv("SEGMENT_ACTIVE_ATHLETES") }
    // The segment importer's account and how many riders make a staged candidate ridden, when docker/.env sets them.
    for (key in listOf("SEGMENT_IMPORTER_DID", "SEGMENT_CANDIDATE_RIDERS")) default(key) { fromDockerEnv(key) }
    // Terrain tiles scripts/terrain-fetch.sh writes into build/terrain, so segments get terrain elevation
    // (segments/segment-elevation).
    default("TERRAIN_TILES_DIR") { rootDir.resolve("build/terrain").takeIf { it.isDirectory }?.path }
    // The AppView's account on the local Ozone's team (docker/scripts/setup-labeler.sh).
    for (key in listOf("APPVIEW_TRIAGE_DID", "APPVIEW_TRIAGE_PASSWORD")) default(key) { fromDockerEnv(key) }
    // The local Ozone's labeler account (docker/scripts/setup-labeler.sh), at the port compose publishes.
    if ("LABELER_DID" !in current) {
        fromDockerEnv("OZONE_SERVER_DID")?.let { did ->
            added["LABELER_DID"] = did
            default("LABELER_URL") { "http://localhost:3010" }
        }
    }
    added
}

tasks.named<JavaExec>(ApplicationPlugin.TASK_RUN_NAME) {
    val localStack = localStackEnvironment(nativeOAuth.effectiveAppviewPublicUrl, nativeOAuth.deviceMode, rootDir)
    workingDir = rootDir
    doFirst { with(this as JavaExec) { environment(localStack(environment.mapValues { it.value.toString() })) } }
}

// The same environment as a file, for scripts/run-appview.sh: it starts the installed distribution as a plain JVM, so no Gradle
// daemon stays busy for as long as the AppView runs. JAVA_HOME is the toolchain's, the JDK `run` would use.
tasks.register("localStackEnv") {
    description = "Writes the AppView's local-stack environment to build/local-stack/appview.env."
    val localStack = localStackEnvironment(nativeOAuth.effectiveAppviewPublicUrl, nativeOAuth.deviceMode, rootDir)
    val javaHome = javaToolchains.launcherFor(java.toolchain).map { it.metadata.installationPath.asFile.path }
    val envFile = layout.buildDirectory.file("local-stack/appview.env")
    doNotTrackState("Reads the caller's environment and docker/.env")
    doLast {
        val variables = localStack(System.getenv()) + ("JAVA_HOME" to javaHome.get())
        envFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(variables.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" })
            // It holds the signing key and the triage password from docker/.env.
            setReadable(false, false)
            setReadable(true, true)
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
    implementation(libs.caffeine)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.hikari)
    implementation(libs.ipaddress)
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
