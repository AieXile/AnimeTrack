package com.aiexile.animetrack.data.network

import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.ProxyType
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

/**
 * 支持用户名/密码认证（RFC 1929）的动态 SOCKS5 Socket 工厂。
 *
 * 不使用 Proxy(Type.SOCKS)：OkHttp 对 SOCKS 代理直接构造 java.net.Socket(proxy)，
 * 由 JVM SocksSocketImpl 完成握手，而 JVM 仅支持无认证/GSSAPI，无法携带用户名密码。
 * 因此让本工厂返回的 Socket 在 connect() 时读取运行时代理设置：
 * - SOCKS5 代理启用：实际连接代理服务器，完成认证握手后 CONNECT 到目标地址；
 * - 否则：退化为普通直连。对 OkHttp 完全透明，配置保存后新连接立即生效。
 *
 * 新增文件理由：OkHttp/JVM 均无现成的带认证 SOCKS5 客户端实现，且无维护良好的
 * 第三方依赖可用，按 RFC 1928/1929 自行实现（仅 CONNECT 命令，协议紧凑）。
 */
class DynamicProxySocketFactory : SocketFactory() {

    override fun createSocket(): Socket = DynamicProxySocket()

    // OkHttp 仅调用无参 createSocket()；其余重载按普通直连处理
    override fun createSocket(host: String, port: Int): Socket = Socket(host, port)

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        Socket(host, port, localHost, localPort)

    override fun createSocket(host: InetAddress, port: Int): Socket = Socket(host, port)

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        Socket(address, port, localAddress, localPort)
}

/** connect() 时按运行时代理设置切换直连 / SOCKS5 隧道的 Socket */
private class DynamicProxySocket : Socket() {

    override fun connect(address: SocketAddress, timeout: Int) {
        val endpoint = address as? InetSocketAddress
            ?: throw IOException("Unsupported socket address: $address")
        val config = currentSocks5Settings()
        if (config != null) {
            connectThroughSocks5(this, config, endpoint, timeout)
        } else {
            super.connect(address, timeout)
        }
    }
}

/** SOCKS5 代理生效中的运行时配置 */
internal data class Socks5ProxyConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String
)

/** SOCKS5 代理启用且配置有效时返回配置，否则 null（直连）。供 Socket/DNS 共用。 */
internal fun currentSocks5Settings(): Socks5ProxyConfig? {
    return try {
        val settings = AppContainer.getSettingsRepository()
        if (settings.httpProxyEnabled &&
            settings.httpProxyType == ProxyType.SOCKS5 &&
            settings.httpProxyHost.isNotBlank() &&
            settings.httpProxyPort in 1..65535
        ) {
            Socks5ProxyConfig(
                host = settings.httpProxyHost,
                port = settings.httpProxyPort,
                username = settings.httpProxyUsername,
                password = settings.httpProxyPassword
            )
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

/** 通过 SOCKS5 代理建立到 [target] 的连接（RFC 1928，认证遵循 RFC 1929） */
private fun connectThroughSocks5(
    socket: Socket,
    config: Socks5ProxyConfig,
    target: InetSocketAddress,
    timeout: Int
) {
    socket.connect(InetSocketAddress(config.host, config.port), timeout)
    try {
        socket.tcpNoDelay = true
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        negotiateSocks5(input, output, config.username, config.password)
        sendSocks5Connect(output, target)
        readSocks5ConnectReply(input)
    } catch (e: Exception) {
        try {
            socket.close()
        } catch (_: Exception) {
        }
        throw e
    }
}

/** 方法协商 + 用户名密码认证（RFC 1929） */
private fun negotiateSocks5(input: InputStream, output: OutputStream, username: String, password: String) {
    val hasAuth = username.isNotEmpty() || password.isNotEmpty()

    // 客户端问候：VER NMETHODS METHODS（无认证 0x00 / 用户名密码 0x02）
    val greeting = if (hasAuth) byteArrayOf(0x05, 0x02, 0x00, 0x02) else byteArrayOf(0x05, 0x01, 0x00)
    output.write(greeting)
    output.flush()

    val reply = readFully(input, 2)
    if (reply[0] != 0x05.toByte()) throw IOException("Invalid SOCKS5 version: ${reply[0]}")
    when (reply[1]) {
        0x00.toByte() -> Unit // 服务端接受无认证
        0x02.toByte() -> {
            // RFC 1929：VER ULEN UNAME PLEN PASSWD
            if (!hasAuth) throw IOException("SOCKS5 server requires username/password authentication")
            val user = username.toByteArray(Charsets.UTF_8)
            val pass = password.toByteArray(Charsets.UTF_8)
            if (user.size > 255 || pass.size > 255) {
                throw IOException("SOCKS5 username/password exceeds 255 bytes")
            }
            val authRequest = ByteArray(3 + user.size + pass.size)
            authRequest[0] = 0x01
            authRequest[1] = user.size.toByte()
            user.copyInto(authRequest, 2)
            authRequest[2 + user.size] = pass.size.toByte()
            pass.copyInto(authRequest, 3 + user.size)
            output.write(authRequest)
            output.flush()

            val authReply = readFully(input, 2)
            if (authReply[1] != 0x00.toByte()) throw IOException("SOCKS5 authentication failed")
        }
        0xFF.toByte() -> throw IOException("SOCKS5 server rejected all offered auth methods")
        else -> throw IOException("Unsupported SOCKS5 auth method: ${reply[1]}")
    }
}

/** 发送 CONNECT 命令：VER CMD RSV ATYP DST.ADDR DST.PORT */
private fun sendSocks5Connect(output: OutputStream, target: InetSocketAddress) {
    val host = target.hostString
        ?: throw IOException("SOCKS5 target host unknown")
    val port = target.port

    val addressBytes: ByteArray
    val addressType: Byte
    when {
        host.isIpv4Literal() -> {
            addressType = 0x01
            // 字面量手工解析，避免 InetAddress 解析引入任何 DNS 可能性
            addressBytes = ByteArray(4).also { bytes ->
                host.split('.').forEachIndexed { i, part -> bytes[i] = part.toInt().toByte() }
            }
        }
        host.contains(':') -> {
            // IPv6 字面量：InetAddress 解析字面量不触发 DNS
            addressType = 0x04
            addressBytes = InetAddress.getByName(host).address
        }
        else -> {
            // 域名：ATYP=0x03 由代理侧解析（socks5h 语义），规避本地 DNS 污染/不可达
            val nameBytes = host.toByteArray(Charsets.UTF_8)
            if (nameBytes.size > 255) throw IOException("SOCKS5 target hostname exceeds 255 bytes")
            addressType = 0x03
            addressBytes = byteArrayOf(nameBytes.size.toByte()) + nameBytes
        }
    }

    val request = ByteArray(6 + addressBytes.size)
    request[0] = 0x05 // VER
    request[1] = 0x01 // CMD: CONNECT
    request[2] = 0x00 // RSV
    request[3] = addressType
    addressBytes.copyInto(request, 4)
    val portIndex = 4 + addressBytes.size
    request[portIndex] = (port ushr 8).toByte()
    request[portIndex + 1] = (port and 0xFF).toByte()
    output.write(request)
    output.flush()
}

/** 读取 CONNECT 响应并跳过 BND.ADDR/BND.PORT：VER REP RSV ATYP BND.ADDR BND.PORT */
private fun readSocks5ConnectReply(input: InputStream) {
    val header = readFully(input, 4)
    if (header[0] != 0x05.toByte()) throw IOException("Invalid SOCKS5 version in reply")
    if (header[1] != 0x00.toByte()) throw IOException("SOCKS5 connect rejected, reply code: ${header[1]}")
    when (header[3]) {
        0x01.toByte() -> readFully(input, 4 + 2) // IPv4 + 端口
        0x04.toByte() -> readFully(input, 16 + 2) // IPv6 + 端口
        0x03.toByte() -> { // 域名 + 端口
            val length = readFully(input, 1)[0].toInt() and 0xFF
            readFully(input, length + 2)
        }
        else -> throw IOException("Invalid SOCKS5 reply address type: ${header[3]}")
    }
}

/** 阻塞读取恰好 [length] 字节（SOCKS5 响应可能分片到达） */
private fun readFully(input: InputStream, length: Int): ByteArray {
    val buffer = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val read = input.read(buffer, offset, length - offset)
        if (read < 0) throw IOException("SOCKS5 connection closed prematurely")
        offset += read
    }
    return buffer
}

/** 形如 1.2.3.4 的合法 IPv4 点分字面量 */
private fun String.isIpv4Literal(): Boolean {
    if (!matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) return false
    return split('.').all { it.toInt() in 0..255 }
}
