package com.bittokazi.ktor.gateway.security.services

import com.auth0.jwk.JwkProvider

interface CustomJwkProvider {
    fun getJwkProvider(issuer: String): JwkProvider
}

class DefaultCustomJwkProvider(
    val providers: Map<String, JwkProvider>,
) : CustomJwkProvider {
    override fun getJwkProvider(issuer: String): JwkProvider {
        return providers[issuer] ?: throw IllegalArgumentException("No JWK provider found for issuer: $issuer")
    }
}
