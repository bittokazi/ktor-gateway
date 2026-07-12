package com.bittokazi.ktor.gateway.config

import com.bittokazi.ktor.gateway.security.GatewayUserSession
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set

val TestSessionPlugin =
    createApplicationPlugin(
        "TestSessionPlugin",
        createConfiguration = ::TestSessionConfig,
    ) {
        onCall {
            val expiresAt = pluginConfig.expiresAt
            val userSession = GatewayUserSession(expiresAt, refreshToken = pluginConfig.refreshToken)
            it.sessions.set(userSession)
        }
    }

val TestSessionOriginalUrlPlugin =
    createApplicationPlugin(
        "TestSessionOriginalUrlPlugin",
        createConfiguration = ::TestSessionOriginalUrlConfig,
    ) {
        onCall {
            it.sessions.set("GATEWAY_ORIGINAL_URL", pluginConfig.url)
        }
    }

data class TestSessionConfig(
    var expiresAt: Long = System.currentTimeMillis() - 60000,
    var refreshToken: String = "refresh_token",
)

data class TestSessionOriginalUrlConfig(
    var url: String = "",
)
