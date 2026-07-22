package com.bittokazi.ktor.gateway

import com.bittokazi.ktor.gateway.common.RouteRule
import com.bittokazi.ktor.gateway.proxy.config.ProxyConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(GatewayPlugin) {
        gatewayOauthBasePath = "/gateway"
        sessionValidityInSeconds = 3600
        // if proxyConfig is set from code than application.yaml configuration will be ignored
        proxyConfig =
            ProxyConfig(
                enabled = true,
                routes =
                    mapOf(
                        "/api" to
                            listOf(
                                RouteRule(
                                    prefix = "/api",
                                    target = "http://localhost:8081",
                                ),
                            ),
                    ),
            )
    }
}
