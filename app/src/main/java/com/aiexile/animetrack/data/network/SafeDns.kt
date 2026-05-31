package com.aiexile.animetrack.data.network

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

class SafeDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname == "api.bgm.tv" || hostname == "bgm.tv") {
            return try {
                val systemAddresses = Dns.SYSTEM.lookup(hostname)

                val isPolluted = systemAddresses.any { address ->
                    val ip = address.hostAddress
                    ip.startsWith("31.13.") ||
                    ip.startsWith("157.240.") ||
                    ip.startsWith("168.143.")
                }

                if (isPolluted) {
                    throw UnknownHostException("Detected DNS Pollution from ISP!")
                }
                systemAddresses
            } catch (e: Exception) {
                if (hostname == "api.bgm.tv") {
                    listOf(InetAddress.getByName("154.85.102.32"))
                } else {
                    listOf(InetAddress.getByName("104.21.36.170"))
                }
            }
        }

        return Dns.SYSTEM.lookup(hostname)
    }
}
