package com.bittokazi.ktor.gateway

import com.bittokazi.ktor.gateway.common.GatewayConfig
import com.bittokazi.ktor.gateway.proxy.config.ProxyConfig
import com.bittokazi.ktor.gateway.security.configureSecurityModule
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.plugins.di.dependencies

val GatewayPlugin =
    createApplicationPlugin(
        "GatewayPlugin",
        createConfiguration = ::GatewayConfig,
    ) {
        val config = pluginConfig

        when (config.proxyConfig) {
            is ProxyConfig -> {
                application.dependencies {
                    provide {
                        config.proxyConfig
                    }
                }
            }
            else -> {
                application.dependencies {
                    provide(ProxyConfig::class)
                }
            }
        }

        application.configureFrameworks(gatewayConfig = config)
        application.configureSerialization()

        if (config.configureHttp) {
            application.configureHTTP()
        }

        application.configureSecurityModule(
            config.gatewayOauthBasePath,
            config.sessionValidityInSeconds,
        )
        application.applicationEventListeners()
    }
