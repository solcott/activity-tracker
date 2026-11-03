package io.github.solcott.activitytracker.appview.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Response

/**
 * The HTTP client for hosts learned from DID documents and handles (identity/outbound-requests): PDS endpoints, `did:web` documents and
 * handle `.well-known` checks. Unless [allowPrivate], it refuses to connect to a private, loopback, link-local, unique-local, CGNAT,
 * reserved, unspecified or multicast address. The [Dns] filter checks the addresses a name resolves to, and OkHttp connects only to those,
 * so a name that resolves differently later can't get past it; the interceptor covers IP-literal hosts, for which OkHttp skips [Dns]. Ktor
 * follows redirects itself, so every hop passes both again. Configured endpoints (PLC, Jetstream, labelers, Ozone) use the trusted client
 * instead.
 */
object GuardedHttp {
    fun create(allowPrivate: Boolean, dns: Dns = Dns.SYSTEM): HttpClient = create(allowPrivate, dns, ::isPrivate)

    /** [create] with [private] in place of [isPrivate], for tests that need a loopback server to count as public. */
    internal fun create(allowPrivate: Boolean, dns: Dns, private: (InetAddress) -> Boolean): HttpClient =
        HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                config {
                    followRedirects(false)
                    followSslRedirects(false)
                    dns(if (allowPrivate) dns else PublicDns(dns, private))
                }
                if (!allowPrivate) addInterceptor(PublicLiteralInterceptor(private))
            }
        }

    /**
     * Whether [address] is one an identity's host may not point the AppView at. An IPv6 address embedding an IPv4 one (IPv4-mapped, NAT64,
     * 6to4, Teredo) counts as that IPv4 address, since a gateway may forward to it.
     */
    fun isPrivate(address: InetAddress): Boolean {
        val ip = (address as? Inet6Address)?.embeddedIpv4() ?: address
        val bytes = ip.address.map { it.toInt() and BYTE }
        return ip.isLoopbackAddress ||
            ip.isSiteLocalAddress ||
            ip.isLinkLocalAddress ||
            ip.isAnyLocalAddress ||
            ip.isMulticastAddress ||
            when (ip) {
                is Inet4Address -> reservedIpv4(bytes)
                // fc00::/7 (unique local).
                else -> bytes[0] and ULA_MASK == ULA_PREFIX
            }
    }

    /**
     * 0.0.0.0/8 ("this network"), 100.64.0.0/10 (carrier-grade NAT), 192.0.0.0/24 (protocol assignments), 198.18.0.0/15 (benchmarking) and
     * 240.0.0.0/4 (reserved, with the broadcast address).
     */
    private fun reservedIpv4(bytes: List<Int>): Boolean =
        bytes[0] == 0 ||
            (bytes[0] == CGNAT_FIRST && bytes[1] and CGNAT_MASK == CGNAT_SECOND) ||
            (bytes[0] == IETF_FIRST && bytes[1] == 0 && bytes[2] == 0) ||
            (bytes[0] == BENCHMARK_FIRST && bytes[1] and BENCHMARK_MASK == BENCHMARK_SECOND) ||
            bytes[0] >= RESERVED_FIRST

    /**
     * The IPv4 address this one carries: `::ffff:a.b.c.d` (IPv4-mapped) and `64:ff9b::a.b.c.d` (NAT64) in their last four bytes, 6to4
     * (`2002::/16`) in bytes 2 to 5, and Teredo (`2001:0::/32`) its client's, the last four bytes inverted. Null for any other address.
     */
    private fun Inet6Address.embeddedIpv4(): InetAddress? {
        val bytes = address.map { it.toInt() and BYTE }
        val ipv4 =
            when {
                bytes.startsWith(MAPPED) || bytes.startsWith(NAT64) -> bytes.subList(IPV6_BYTES - IPV4_BYTES, IPV6_BYTES)
                bytes.startsWith(SIX_TO_FOUR) -> bytes.subList(SIX_TO_FOUR.size, SIX_TO_FOUR.size + IPV4_BYTES)
                bytes.startsWith(TEREDO) -> bytes.subList(IPV6_BYTES - IPV4_BYTES, IPV6_BYTES).map { it xor BYTE }
                else -> return null
            }
        return InetAddress.getByAddress(ByteArray(IPV4_BYTES) { ipv4[it].toByte() })
    }

    private fun List<Int>.startsWith(prefix: List<Int>) = size >= prefix.size && subList(0, prefix.size) == prefix

    private class PublicDns(private val delegate: Dns, private val private: (InetAddress) -> Boolean) : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            delegate.lookup(hostname).filterNot(private).ifEmpty { throw PrivateAddressException(hostname) }
    }

    private class PublicLiteralInterceptor(private val private: (InetAddress) -> Boolean) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val host = chain.request().url.host
            if (isIpLiteral(host) && private(InetAddress.getByName(host))) throw PrivateAddressException(host)
            return chain.proceed(chain.request())
        }

        /** OkHttp gives IPv6 hosts without brackets and canonicalizes IPv4 ones, so this never sends a name to DNS. */
        private fun isIpLiteral(host: String) = ':' in host || IPV4.matches(host)

        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    }

    private const val BYTE = 0xff
    private const val CGNAT_FIRST = 100
    private const val CGNAT_SECOND = 64
    private const val CGNAT_MASK = 0xc0
    private const val ULA_MASK = 0xfe
    private const val ULA_PREFIX = 0xfc
    private const val IETF_FIRST = 192
    private const val BENCHMARK_FIRST = 198
    private const val BENCHMARK_SECOND = 18
    private const val BENCHMARK_MASK = 0xfe
    private const val RESERVED_FIRST = 240
    private const val IPV4_BYTES = 4
    private const val IPV6_BYTES = 16
    private val MAPPED = List(10) { 0 } + listOf(BYTE, BYTE)
    private val NAT64 = listOf(0x00, 0x64, 0xff, 0x9b) + List(8) { 0 }
    private val SIX_TO_FOUR = listOf(0x20, 0x02)
    private val TEREDO = listOf(0x20, 0x01, 0x00, 0x00)
}

/** [host] resolves only to addresses the AppView may not reach for an identity. */
class PrivateAddressException(host: String) : UnknownHostException("$host resolves only to private addresses")
