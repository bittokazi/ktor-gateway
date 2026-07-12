package com.bittokazi.ktor.gateway.security.controllers

import com.bittokazi.ktor.auth.utils.getBaseUrl
import com.bittokazi.ktor.gateway.common.CallResult
import com.bittokazi.ktor.gateway.common.OauthClient
import com.bittokazi.ktor.gateway.proxy.services.ProxyService
import com.bittokazi.ktor.gateway.security.GatewayUserSession
import com.bittokazi.ktor.gateway.security.services.LoginService
import com.bittokazi.ktor.gateway.security.services.LoginServiceErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun Application.configureIdpLoginRoutes(
    gatewayOauthBasePath: String = "/gateway",
    sessionValidityInSeconds: Long = 3000,
) {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    val proxyService: ProxyService by dependencies
    val loginService: LoginService by dependencies

    routing {
        get("$gatewayOauthBasePath/login") {
            rulesValidator(call, proxyService) { oauthConfig ->
                call.respondRedirect(
                    "${oauthConfig.authorizeUrl}?client_id=${oauthConfig.clientId}&response_type=code" +
                        "&scope=${oauthConfig.scopes.joinToString("+")}" +
                        "&redirect_uri=${call.getBaseUrl()}$gatewayOauthBasePath/callback",
                )
            }
        }

        get("$gatewayOauthBasePath/logout") {
            rulesValidator(call, proxyService) { oauthConfig ->
                call.respondRedirect(
                    "${oauthConfig.logoutUrl}?client_id=${oauthConfig.clientId}",
                )
            }
        }

        get("$gatewayOauthBasePath/callback") {
            rulesValidator(call, proxyService) { oauthConfig ->
                when (val result = loginService.fetchToken(call, oauthConfig)) {
                    is CallResult.Failure -> {
                        when (result.errorCode) {
                            LoginServiceErrorCode.MISSING_CODE ->
                                call.respond(
                                    status = HttpStatusCode.BadRequest,
                                    message = result,
                                )

                            LoginServiceErrorCode.UNAUTHORIZED ->
                                call.respond(
                                    status = HttpStatusCode.Unauthorized,
                                    message = result,
                                )

                            LoginServiceErrorCode.BAD_REQUEST ->
                                call.respond(
                                    status = HttpStatusCode.BadRequest,
                                    message = result,
                                )
                        }
                    }

                    is CallResult.Success -> {
                        val expiresAt = System.currentTimeMillis() + sessionValidityInSeconds * 1000
                        val userSession = GatewayUserSession(expiresAt, result.outcome.refresh_token)
                        call.sessions.set(userSession)

                        val originalUrl = call.sessions.get("GATEWAY_ORIGINAL_URL")
                        if (originalUrl != null) {
                            call.sessions.clear("GATEWAY_ORIGINAL_URL")
                            call.respondRedirect(originalUrl.toString())
                        }
                    }
                }
            }
        }
    }

    log.info("[INFO] configureIdpLoginRoutes -> Routes configured.")
}

private suspend fun rulesValidator(
    call: ApplicationCall,
    proxyService: ProxyService,
    block: suspend (oauthConfig: OauthClient) -> Unit,
) {
    val origin = call.request.origin
    val portPart =
        when {
            (origin.scheme == "http" && origin.serverPort == 80) -> ""
            (origin.scheme == "https" && origin.serverPort == 443) -> ""
            else -> ":${origin.serverPort}"
        }
    val domain = "${origin.serverHost}$portPart"

    proxyService.getOauthClient(domain, call)?.let { oauthConfig ->
        block(oauthConfig)
    } ?: return call.respond(
        HttpStatusCode.NotFound,
        mapOf("error" to "No oauth client found for domain $domain"),
    )
}
