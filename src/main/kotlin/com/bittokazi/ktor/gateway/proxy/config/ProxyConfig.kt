package com.bittokazi.ktor.gateway.proxy.config

import com.bittokazi.ktor.gateway.common.OauthClient
import com.bittokazi.ktor.gateway.common.RouteRule
import io.ktor.server.plugins.di.annotations.Property
import kotlinx.serialization.Serializable

@Serializable
data class ProxyConfig(
    @Property("proxy.enabled") val enabled: Boolean = false,
    @Property("proxy.routes") val routes: Map<String, List<RouteRule>> = mapOf(),
    @Property("proxy.oauth-clients") val oauthClients: Map<String, OauthClient> = mapOf(),
    @Property("proxy.gateway-oauth-base-path") val gatewayOauthBasePath: String? = "/gateway",
    @Property("proxy.session-validity-in-seconds") val sessionValidityInSeconds: Long? = 3600,
)
