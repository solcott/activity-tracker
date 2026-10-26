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
 * unspecified or multicast address. The [Dns] filter checks the addresses a name resolves to, and OkHttp connects only to those, so a name
 * that resolves differently later can't get past it; the interceptor covers IP-literal hosts, for which OkHttp skips [Dns]. Ktor follows
 * redirects itself, so every hop passes both again. Configured endpoints (PLC, Jetstream, labelers, Ozone) use the trusted client instead.
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

    /** Whether [address] is one an identity's host may not point the AppView at. IPv4-mapped IPv6 forms count as their IPv4 address. */
    fun isPrivate(address: InetAddress): Boolean {
        val ip = (address as? Inet6Address)?.mappedIpv4() ?: address
        val bytes = ip.address.map { it.toInt() and BYTE }
        return ip.isLoopbackAddress ||
            ip.isSiteLocalAddress ||
            ip.isLinkLocalAddress ||
            ip.isAnyLocalAddress ||
            ip.isMulticastAddress ||
            when (ip) {
                // 0.0.0.0/8 ("this network") and 100.64.0.0/10 (carrier-grade NAT).
                is Inet4Address -> bytes[0] == 0 || (bytes[0] == CGNAT_FIRST && bytes[1] and CGNAT_MASK == CGNAT_SECOND)
                // fc00::/7 (unique local).
                else -> bytes[0] and ULA_MASK == ULA_PREFIX
            }
    }

    private fun Inet6Address.mappedIpv4(): InetAddress? {
        val bytes = address
        val mapped = (0 until MAPPED_PREFIX).all { bytes[it] == 0.toByte() } && bytes[MAPPED_PREFIX] == FF && bytes[MAPPED_PREFIX + 1] == FF
        return if (mapped) InetAddress.getByAddress(bytes.copyOfRange(MAPPED_PREFIX + 2, bytes.size)) else null
    }

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
    private const val MAPPED_PREFIX = 10
    private const val FF = 0xff.toByte()
}

/** [host] resolves only to addresses the AppView may not reach for an identity. */
class PrivateAddressException(host: String) : UnknownHostException("$host resolves only to private addresses")
