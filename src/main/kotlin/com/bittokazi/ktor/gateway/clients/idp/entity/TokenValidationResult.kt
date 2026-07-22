package com.bittokazi.ktor.gateway.clients.idp.entity

import kotlinx.serialization.Serializable

@Serializable
data class TokenValidationResult(
    val active: Boolean,
)
