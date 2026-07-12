package com.bittokazi.ktor.gateway.common

import kotlinx.serialization.Serializable

@Serializable
data class RouteRule(
    val prefix: String,
    val target: String,
    val authType: AuthType = AuthType.NONE,
)

enum class AuthType {
    NONE,
    SESSION_BASED,
    TOKEN_BASED,
}

@Serializable
data class OauthClient(
    val clientId: String,
    val clientSecret: String,
    val scopes: List<String>,
    val authorizeUrl: String,
    val tokenUrl: String,
    val introspectUrl: String,
    val logoutUrl: String,
)
