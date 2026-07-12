package com.bittokazi.ktor.gateway.proxy.services

import com.bittokazi.ktor.gateway.common.OauthClient
import com.bittokazi.ktor.gateway.common.RouteRule
import io.ktor.server.application.ApplicationCall

interface ProxyService {
    suspend fun handle(call: ApplicationCall)

    suspend fun getRule(
        domain: String?,
        path: String,
        call: ApplicationCall,
    ): RouteRule?

    suspend fun getOauthClient(
        domain: String?,
        call: ApplicationCall,
    ): OauthClient?
}
