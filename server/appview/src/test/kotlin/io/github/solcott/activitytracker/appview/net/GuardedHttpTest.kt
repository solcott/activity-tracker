package io.github.solcott.activitytracker.appview.net

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.Dns

/** identity/outbound-requests: the guarded client refuses private addresses before any request reaches them. */
class GuardedHttpTest {
    private val hits = CopyOnWriteArrayList<String>()
    private val server: EmbeddedServer<*, *> =
        embeddedServer(Netty, port = 0, host = "127.0.0.1") {
                routing {
                    get("/doc") {
                        hits += "/doc"
                        call.respondText("ok")
                    }
                    get("/hop") {
                        hits += "/hop"
                        call.respondRedirect("http://internal.test:$port/doc")
                    }
                }
            }
            .start(wait = false)
    private val port = runBlocking { server.engine.resolvedConnectors().first().port }

    @AfterTest fun stop() = server.stop(0, 0)

    private fun HttpClient.fetch(url: String) = runBlocking { use { it.get(url).bodyAsText() } }

    @Test
    fun loopbackIsRefusedBeforeAnyRequest() {
        assertFailsWith<PrivateAddressException> { GuardedHttp.create(allowPrivate = false).fetch("http://127.0.0.1:$port/doc") }
        assertFailsWith<PrivateAddressException> { GuardedHttp.create(allowPrivate = false).fetch("http://localhost:$port/doc") }
        assertFailsWith<PrivateAddressException> { GuardedHttp.create(allowPrivate = false).fetch("http://[::1]:$port/doc") }
        assertEquals(emptyList(), hits)
    }

    @Test
    fun allowedForLocalDevelopment() {
        assertEquals("ok", GuardedHttp.create(allowPrivate = true).fetch("http://127.0.0.1:$port/doc"))
        assertEquals("ok", GuardedHttp.create(allowPrivate = true).fetch("http://localhost:$port/doc"))
        assertEquals(listOf("/doc", "/doc"), hits)
    }

    @Test
    fun aRedirectToAPrivateHostIsRefused() {
        // public.test is the loopback server, counted as public here; internal.test resolves to an address counted as private.
        val internal = InetAddress.getByName("127.0.0.2")
        val dns = Dns { host ->
            when (host) {
                "public.test" -> listOf(InetAddress.getByName("127.0.0.1"))
                "internal.test" -> listOf(internal)
                else -> Dns.SYSTEM.lookup(host)
            }
        }
        val client = GuardedHttp.create(allowPrivate = false, dns) { it == internal }
        assertFailsWith<PrivateAddressException> { client.fetch("http://public.test:$port/hop") }
        assertEquals(listOf("/hop"), hits, "the redirect target was never requested")
    }

    @Test
    fun classifiesAddresses() {
        val private =
            listOf(
                "127.0.0.1",
                "10.0.0.5",
                "172.16.0.1",
                "192.168.1.10",
                "169.254.169.254",
                "100.64.0.1",
                "0.0.0.0",
                "0.1.2.3",
                "224.0.0.1",
                "::1",
                "::",
                "fe80::1",
                "fc00::1",
                "fd12:3456::1",
                "ff02::1",
                "::ffff:127.0.0.1",
                "::ffff:10.0.0.5",
            )
        val public = listOf("8.8.8.8", "100.128.0.1", "172.32.0.1", "2606:4700:4700::1111", "::ffff:8.8.8.8")
        for (ip in private) assertTrue(GuardedHttp.isPrivate(InetAddress.getByName(ip)), ip)
        for (ip in public) assertFalse(GuardedHttp.isPrivate(InetAddress.getByName(ip)), ip)
    }
}
