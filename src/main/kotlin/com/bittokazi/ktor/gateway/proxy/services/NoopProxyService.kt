package com.bittokazi.ktor.gateway.proxy.services

import com.bittokazi.ktor.gateway.common.OauthClient
import com.bittokazi.ktor.gateway.common.RouteRule
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class NoopProxyService : ProxyService {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    init {
        log.info("[INFO] NoopProxyService is enabled ⚠")
    }

    override suspend fun handle(call: ApplicationCall) {
        call.respond(
            HttpStatusCode.BadGateway,
            mapOf("error" to "No Proxy Client is Enabled"),
        )
    }

    override suspend fun getRule(
        domain: String?,
        path: String,
        call: ApplicationCall,
    ): RouteRule? = null

    override suspend fun getOauthClient(
        domain: String?,
        call: ApplicationCall,
    ): OauthClient? = null
}
