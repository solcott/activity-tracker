package io.github.solcott.activitytracker.appview.net

import inet.ipaddr.IPAddressNetwork.IPAddressGenerator
import inet.ipaddr.IPAddressString
import inet.ipaddr.ipv4.IPv4Address
import inet.ipaddr.ipv6.IPv6Address
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
     * Whether [address] is one an identity's host may not point the AppView at: in one of the [REFUSED] blocks. An IPv6 address embedding
     * an IPv4 one (IPv4-mapped, IPv4-compatible, NAT64, 6to4, Teredo) counts as that IPv4 address, since a gateway may forward to it.
     */
    fun isPrivate(address: InetAddress): Boolean {
        val ip = ADDRESSES.from(address).let { (it as? IPv6Address)?.embeddedIpv4() ?: it }
        return REFUSED.any { it.contains(ip) }
    }

    /** The IPv4 address this one carries, Teredo's being its client's (stored inverted); null for any other address. */
    private fun IPv6Address.embeddedIpv4(): IPv4Address? =
        when {
            isIPv4Mapped || isIPv4Compatible || isWellKnownIPv4Translatable -> embeddedIPv4Address
            is6To4 -> get6To4IPv4Address()
            isTeredo -> IPv4Address(embeddedIPv4Address.intValue().inv())
            else -> null
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

    private val ADDRESSES = IPAddressGenerator()

    /** Blocks no identity's host may resolve to: none is a public internet address. */
    private val REFUSED =
        listOf(
                "0.0.0.0/8", // "This network", with the unspecified address.
                "10.0.0.0/8", // Private.
                "100.64.0.0/10", // Carrier-grade NAT.
                "127.0.0.0/8", // Loopback.
                "169.254.0.0/16", // Link-local.
                "172.16.0.0/12", // Private.
                "192.0.0.0/24", // Protocol assignments.
                "192.168.0.0/16", // Private.
                "198.18.0.0/15", // Benchmarking.
                "224.0.0.0/3", // Multicast, then reserved up to the broadcast address.
                "::/128", // Unspecified.
                "::1/128", // Loopback.
                "64:ff9b:1::/48", // Local-use NAT64, which puts the IPv4 address where its operator chooses.
                "fc00::/7", // Unique local.
                "fe80::/10", // Link-local.
                "fec0::/10", // Site-local (deprecated).
                "ff00::/8", // Multicast.
            )
            .map { IPAddressString(it).address }
}

/** [host] resolves only to addresses the AppView may not reach for an identity. */
class PrivateAddressException(host: String) : UnknownHostException("$host resolves only to private addresses")
