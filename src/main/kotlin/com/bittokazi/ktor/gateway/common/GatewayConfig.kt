package com.bittokazi.ktor.gateway.common

import com.bittokazi.ktor.gateway.proxy.config.ProxyConfig

data class GatewayConfig(
    var gatewayOauthBasePath: String = "/gateway",
    var sessionValidityInSeconds: Long = 3600,
    var proxyConfig: ProxyConfig? = null,
    var configureHttp: Boolean = true
)
