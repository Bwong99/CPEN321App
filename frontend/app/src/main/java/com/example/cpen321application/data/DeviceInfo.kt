package com.example.cpen321application.data

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * The device's own IP address and clock, for the two client-side fields on the
 * Button 1 screen. M1 accepts either a private or a public client IP, so this
 * reads the device's own interfaces rather than asking an external service.
 */
object DeviceInfo {

    /**
     * Formats an instant as `hh:mm:ss GMT+hh:mm`, matching the format the
     * back-end uses for server time so the two rows read consistently.
     */
    fun localTime(now: ZonedDateTime = ZonedDateTime.now()): String {
        // ZoneOffset renders UTC as "Z"; M1 wants an explicit numeric offset.
        val offset = now.offset.id.let { if (it == "Z") "+00:00" else it }
        return "${now.format(TIME_OF_DAY)} GMT$offset"
    }

    /**
     * The device's private IP address, preferring IPv4 because that is what a
     * phone on Wi-Fi normally has. Returns null when the device has no
     * non-loopback address, e.g. with networking disabled.
     */
    fun ipAddress(): String? {
        val candidates = NetworkInterface.getNetworkInterfaces()
            .asSequence()
            .filter { runCatching { it.isUp }.getOrDefault(false) }
            .flatMap { it.inetAddresses.asSequence() }
            .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
            .toList()

        val address = candidates.firstOrNull { it is Inet4Address }
            ?: candidates.firstOrNull()

        return address?.let(::formatAddress)
    }

    // IPv6 addresses from NetworkInterface carry a "%eth0" scope suffix that
    // is meaningless off-device.
    private fun formatAddress(address: InetAddress): String =
        address.hostAddress?.substringBefore('%').orEmpty()
}
