package com.bittokazi.ktor.gateway.security

import com.bittokazi.ktor.auth.services.SessionCustomizer
import com.bittokazi.ktor.gateway.security.controllers.configureIdpLoginRoutes
import com.bittokazi.ktor.gateway.security.services.DefaultLoginService
import com.bittokazi.ktor.gateway.security.services.LoginService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.util.hex
import kotlinx.serialization.Serializable
import kotlin.random.Random

fun Application.configureSecurityModule(
    gatewayOauthBasePath: String = "/gateway",
    sessionValidityInSeconds: Long = 3600,
) {
    dependencies {
        provide<LoginService>(DefaultLoginService::class)
    }

    val sessionCustomizer: SessionCustomizer? by dependencies

    var secretEncryptKey = hex(Random.nextBytes(16).joinToString("") { "%02x".format(it) }) // 16 bytes = AES128
    var secretSignKey = hex(Random.nextBytes(16).joinToString("") { "%02x".format(it) }) // 16 bytes

    sessionCustomizer?.let {
        if (it.encryptionKey != null || it.signingKey != null) {
            secretEncryptKey = hex(it.encryptionKey!!)
            secretSignKey = hex(it.signingKey!!)
        }
    }

    install(Sessions) {
        cookie<GatewayUserSession>("GATEWAY_USER_SESSION") {
            cookie.httpOnly = true
            cookie.secure = false // set true in production (HTTPS only)
            cookie.maxAgeInSeconds = 31536000
            transform(SessionTransportTransformerEncrypt(secretEncryptKey, secretSignKey))
        }
        cookie<String>("GATEWAY_ORIGINAL_URL") {
            cookie.httpOnly = true
            cookie.secure = false // set true in production (HTTPS only)
        }
    }

    configureIdpLoginRoutes(
        gatewayOauthBasePath = gatewayOauthBasePath,
        sessionValidityInSeconds = sessionValidityInSeconds,
    )
}

@Serializable
data class GatewayUserSession(
    val expiresAt: Long,
    val refreshToken: String? = null,
)
