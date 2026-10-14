package com.aiexile.animetrack.model

/** 网络代理协议类型（存 DataStore 时取 name） */
enum class ProxyType {
    /** WEB 代理（HTTP/HTTPS CONNECT 隧道） */
    HTTP,

    /** SOCKS5 代理 */
    SOCKS5
}
