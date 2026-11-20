package io.github.solcott.activitytracker.appview.ops

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.slf4j.Logger

/** Prometheus' text exposition format. */
private val PROMETHEUS_TEXT = ContentType.parse("text/plain; version=0.0.4; charset=utf-8")

/** `GET /metrics`: [metrics] in Prometheus text format. Mounted only on the internal metrics server. */
fun Route.metricsRoutes(metrics: AppViewMetrics) {
    get("/metrics") { call.respondText(metrics.scrape(), PROMETHEUS_TEXT) }
}

/** The internal metrics server: `/metrics` alone, on [port] at [host], apart from the public port (dev/worker-telemetry). */
fun metricsServer(metrics: AppViewMetrics, host: String, port: Int): EmbeddedServer<*, *> =
    embeddedServer(Netty, port = port, host = host) { routing { metricsRoutes(metrics) } }

/** Starts the internal metrics server on [port] at [host], apart from the public port (dev/worker-telemetry). */
fun AppViewMetrics.serve(host: String, port: Int, log: Logger) {
    log.info("Serving metrics at http://{}:{}/metrics", host, port)
    metricsServer(this, host, port).start(wait = false)
}

/**
 * Times the public server's requests into [metrics] by method, route template and status, with requests that match no route under one
 * shared route (never their path), and binds the JVM and process metrics.
 */
fun Application.installRequestMetrics(metrics: AppViewMetrics) {
    install(MicrometerMetrics) {
        registry = metrics.registry
        metricName = "http.server.requests"
        distinctNotRegisteredRoutes = false
    }
}
