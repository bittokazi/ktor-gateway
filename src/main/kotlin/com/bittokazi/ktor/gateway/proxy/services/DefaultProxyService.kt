package com.bittokazi.ktor.gateway.proxy.services

import com.bittokazi.ktor.gateway.clients.idp.IdpClient
import com.bittokazi.ktor.gateway.clients.idp.entity.RefreshTokenRequest
import com.bittokazi.ktor.gateway.clients.proxy.ProxyClient
import com.bittokazi.ktor.gateway.common.AuthType
import com.bittokazi.ktor.gateway.common.CallResult
import com.bittokazi.ktor.gateway.common.OauthClient
import com.bittokazi.ktor.gateway.common.RouteRule
import com.bittokazi.ktor.gateway.proxy.config.ProxyConfig
import com.bittokazi.ktor.gateway.security.GatewayUserSession
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.plugins.origin
import io.ktor.server.request.path
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.filter
import io.ktor.utils.io.jvm.javaio.copyTo
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DefaultProxyService(
    val proxyConfig: ProxyConfig,
    val idpClient: IdpClient,
    val proxyClient: ProxyClient,
) : ProxyService {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    init {
        log.info("[INFO] DefaultProxyService is enabled ✅")
    }

    override suspend fun handle(call: ApplicationCall) {
        val origin = call.request.origin
        val portPart =
            when {
                (origin.scheme == "http" && origin.serverPort == 80) -> ""
                (origin.scheme == "https" && origin.serverPort == 443) -> ""
                else -> ":${origin.serverPort}"
            }
        val domain = "${origin.serverHost}$portPart"

        val path = call.request.path()

        val rule: RouteRule =
            getRule(domain, path, call) ?: return call.respond(
                HttpStatusCode.NotFound,
                mapOf("error" to "No route found for path $domain$path"),
            )

        when (rule.authType) {
            AuthType.NONE -> {}

            AuthType.SESSION_BASED ->
                getOauthClient(domain, call)?.let { oauthConfig ->
                    val session = call.sessions.get<GatewayUserSession>()
                    if (session == null || session.expiresAt < System.currentTimeMillis()) {
                        session?.refreshToken?.let { refreshToken ->
                            when (
                                val result =
                                    idpClient.fetchRefreshToken(
                                        RefreshTokenRequest(refreshToken),
                                        oauthConfig,
                                    )
                            ) {
                                is CallResult.Success -> {
                                    val expiresAt = System.currentTimeMillis() + (60 * 1000)
                                    val userSession = GatewayUserSession(expiresAt, result.outcome.refresh_token)
                                    call.sessions.set(userSession)
                                }

                                is CallResult.Failure -> {
                                    getInvalidSessionAction(call)
                                    return
                                }
                            }
                        } ?: run {
                            getInvalidSessionAction(call)
                            return
                        }
                    }
                } ?: return call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    mapOf("error" to "No oauth config found for domain $domain"),
                )

            AuthType.TOKEN_BASED ->
                getOauthClient(domain, call)?.let { oauthConfig ->
                    val token =
                        when (val authHeader = call.request.parseAuthorizationHeader()) {
                            is HttpAuthHeader.Single -> {
                                if (authHeader.authScheme.equals("Bearer", ignoreCase = true)) {
                                    authHeader.blob
                                } else {
                                    null
                                }
                            }

                            else -> null
                        }

                    token?.let { token ->
                        when (
                            val result =
                                idpClient.validateToken(
                                    token,
                                    oauthConfig,
                                )
                        ) {
                            is CallResult.Success ->
                                if (!result.outcome.active) {
                                    return call.respond(
                                        HttpStatusCode.Unauthorized,
                                        mapOf("error" to "Token expired"),
                                    )
                                }

                            is CallResult.Failure -> return call.respond(
                                HttpStatusCode.Unauthorized,
                                mapOf("error" to "Invalid token"),
                            )
                        }
                    } ?: return call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid / Missing token"),
                    )
                } ?: return call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    mapOf("error" to "No oauth config found for domain $domain"),
                )
        }

        val targetUrl = rule.target + call.request.uri

        try {
            val response = proxyClient.request(call, targetUrl)

            response
                .headers
                .filter { key, _ -> !isRestrictedHeader(key) }
                .forEach { key, values ->
                    values
                        .forEach {
                                value ->
                            call.response.headers.append(key, value)
                        }
                }

            call.response.status(response.status)

            // Stream response body (no buffering!)
            call.respondOutputStream {
                response.bodyAsChannel().copyTo(this)
            }
        } catch (e: Exception) {
            log.error("[FAILURE] Exception while requesting {}", e.message)
            call.respond(
                HttpStatusCode.BadGateway,
                mapOf("error" to "Upstream error"),
            )
        }
    }

    override suspend fun getRule(
        domain: String?,
        path: String,
        call: ApplicationCall,
    ): RouteRule? {
        return proxyConfig.routes[domain]
            ?.filter { path == it.prefix || path.startsWith(it.prefix) }
            ?.maxByOrNull { it.prefix.length } ?: run {
            proxyConfig.routes[""]
                ?.filter { path == it.prefix || path.startsWith(it.prefix) }
                ?.maxByOrNull { it.prefix.length }
        }
    }

    override suspend fun getOauthClient(
        domain: String?,
        call: ApplicationCall,
    ): OauthClient? =
        proxyConfig.oauthClients[domain]
            ?: proxyConfig.oauthClients[""]

    private suspend fun getInvalidSessionAction(call: ApplicationCall) {
        // No session/expired: save original request so we can come back later
        call.sessions.clear("GATEWAY_USER_SESSION")
        val authRequestUrl = call.request.uri
        call.sessions.set("GATEWAY_ORIGINAL_URL", authRequestUrl)
        call.respondRedirect("/gateway/login")
    }

    fun isRestrictedHeader(name: String): Boolean {
        return name.equals("Connection", true) ||
            name.equals("Keep-Alive", true) ||
            name.equals("Proxy-Authenticate", true) ||
            name.equals("Proxy-Authorization", true) ||
            name.equals("TE", true) ||
            name.equals("Trailer", true) ||
            name.equals("Transfer-Encoding", true) ||
            name.equals("Upgrade", true) ||
            name.equals("Content-Length", true)
    }
}
