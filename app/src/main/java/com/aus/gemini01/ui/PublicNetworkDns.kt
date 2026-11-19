package com.aus.gemini01.ui

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/** Resolves image hosts normally but refuses private/local destinations, including redirects. */
internal class PublicNetworkDns(
    private val delegate: Dns = Dns.SYSTEM
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (addresses.isEmpty() || addresses.any(::isRestrictedAddress)) {
            throw UnknownHostException("Image host resolves to a non-public address")
        }
        return addresses
    }
}

private fun isRestrictedAddress(address: InetAddress): Boolean {
    if (
        address.isAnyLocalAddress ||
        address.isLoopbackAddress ||
        address.isLinkLocalAddress ||
        address.isSiteLocalAddress ||
        address.isMulticastAddress
    ) {
        return true
    }

    val bytes = address.address
    if (bytes.size == 4) return isRestrictedIpv4(bytes)

    if (bytes.size == 16) {
        val first = bytes[0].toInt() and 0xff
        val isIpv6UniqueLocal = (first and 0xfe) == 0xfc
        if (isIpv6UniqueLocal) return true

        // Reject IPv4-mapped IPv6 answers when the embedded IPv4 address is local.
        val isIpv4Mapped = bytes.take(10).all { it == 0.toByte() } &&
            (bytes[10].toInt() and 0xff) == 0xff &&
            (bytes[11].toInt() and 0xff) == 0xff
        if (isIpv4Mapped && isRestrictedIpv4(bytes.copyOfRange(12, 16))) return true
    }

    return false
}

private fun isRestrictedIpv4(bytes: ByteArray): Boolean {
    val first = bytes[0].toInt() and 0xff
    val second = bytes[1].toInt() and 0xff
    val third = bytes[2].toInt() and 0xff
    return first == 0 ||
        first == 10 ||
        first == 127 ||
        (first == 169 && second == 254) ||
        (first == 172 && second in 16..31) ||
        (first == 192 && (second == 0 || second == 168)) ||
        (first == 198 && (second in 18..19 || second == 51)) ||
        (first == 203 && second == 0 && third == 113) ||
        (first == 100 && second in 64..127) ||
        first >= 224
}
