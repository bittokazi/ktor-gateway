package com.bittokazi.ktor.gateway.security.controllers

import com.bittokazi.ktor.auth.domains.token.OauthTokenResponse
import com.bittokazi.ktor.gateway.clients.idp.IdpClient
import com.bittokazi.ktor.gateway.clients.idp.IdpClientErrorCode
import com.bittokazi.ktor.gateway.clients.proxy.ProxyClient
import com.bittokazi.ktor.gateway.common.AuthType
import com.bittokazi.ktor.gateway.common.CallResult
import com.bittokazi.ktor.gateway.common.OauthClient
import com.bittokazi.ktor.gateway.common.RouteRule
import com.bittokazi.ktor.gateway.config.TestSessionOriginalUrlPlugin
import com.bittokazi.ktor.gateway.proxy.config.ProxyConfig
import com.bittokazi.ktor.gateway.proxy.services.DefaultProxyService
import com.bittokazi.ktor.gateway.proxy.services.ProxyService
import com.bittokazi.ktor.gateway.security.GatewayUserSession
import com.bittokazi.ktor.gateway.security.services.DefaultLoginService
import com.bittokazi.ktor.gateway.security.services.LoginService
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.gson.gson
import io.ktor.serialization.jackson.jackson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.InternalAPI
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import kotlin.test.assertContains

@OptIn(InternalAPI::class)
@RunWith(MockitoJUnitRunner::class)
@ExtendWith(MockitoExtension::class)
class IdpLoginRoutesTest {
    @Mock
    private lateinit var idpClient: IdpClient

    @Mock
    private lateinit var proxyClient: ProxyClient

    @Mock
    private lateinit var loginService: LoginService

    @Test
    fun testLoginRedirectSuccessAfterRulesValidated() =
        testApplication {
            application {
                dependencies {
                    provide<LoginService> {
                        loginService
                    }
                    provide<ProxyService> {
                        DefaultProxyService(
                            proxyConfig =
                                ProxyConfig(
                                    enabled = true,
                                    routes =
                                        mapOf(
                                            "" to
                                                listOf(
                                                    RouteRule(
                                                        prefix = "/api/hello",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.SESSION_BASED,
                                                    ),
                                                ),
                                        ),
                                    oauthClients =
                                        mapOf(
                                            "" to
                                                OauthClient(
                                                    clientId = "test-client",
                                                    clientSecret = "test-secret",
                                                    scopes = listOf("read", "write"),
                                                    authorizeUrl = "http://idp:8080/authorize",
                                                    tokenUrl = "http://idp:8080/token",
                                                    issuer = "http://idp:8080",
                                                    jwksUrl = "http://idp:8080/jwks",
                                                    logoutUrl = "http://idp:8080/logout",
                                                ),
                                        ),
                                ),
                            idpClient = idpClient,
                            proxyClient = proxyClient,
                        )
                    }
                }
                install(ContentNegotiation) {
                    jackson { enable(SerializationFeature.INDENT_OUTPUT) }
                    gson {}
                    json()
                }
                configureIdpLoginRoutes()
            }

            val client =
                createClient {
                    followRedirects = false
                }
            val response = client.get("/gateway/login")

            assertEquals(302, response.status.value)
            assertContains(
                response.headers["Location"] ?: "",
                "http://idp:8080/authorize?client_id=test-client&response_type=code&scope=read+write" +
                    "&redirect_uri=http://localhost/gateway/callback",
            )
        }

    @Test
    fun testLoginRedirectErrorNoIdpClientForDomain() =
        testApplication {
            application {
                dependencies {
                    provide {
                        loginService
                    }
                    provide<ProxyService> {
                        DefaultProxyService(
                            proxyConfig =
                                ProxyConfig(
                                    enabled = true,
                                    routes =
                                        mapOf(
                                            "" to
                                                listOf(
                                                    RouteRule(
                                                        prefix = "/api/hello",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.SESSION_BASED,
                                                    ),
                                                ),
                                        ),
                                    oauthClients =
                                        mapOf(
                                            "example.com" to
                                                OauthClient(
                                                    clientId = "test-client",
                                                    clientSecret = "test-secret",
                                                    scopes = listOf("read", "write"),
                                                    authorizeUrl = "http://idp:8080/authorize",
                                                    tokenUrl = "http://idp:8080/token",
                                                    issuer = "http://idp:8080",
                                                    jwksUrl = "http://idp:8080/jwks",
                                                    logoutUrl = "http://idp:8080/logout",
                                                ),
                                        ),
                                ),
                            idpClient = idpClient,
                            proxyClient = proxyClient,
                        )
                    }
                }
                install(ContentNegotiation) {
                    jackson { enable(SerializationFeature.INDENT_OUTPUT) }
                    gson {}
                    json()
                }
                configureIdpLoginRoutes()
            }

            val client =
                createClient {
                    followRedirects = false
                }
            val response = client.get("/gateway/login")

            assertEquals(404, response.status.value)
            assertContains(response.bodyAsText(), "No oauth client found for domain")
        }

    @Test
    fun testLogoutRedirectSuccessAfterRulesValidated() =
        testApplication {
            application {
                dependencies {
                    provide {
                        loginService
                    }
                    provide<ProxyService> {
                        DefaultProxyService(
                            proxyConfig =
                                ProxyConfig(
                                    enabled = true,
                                    routes =
                                        mapOf(
                                            "" to
                                                listOf(
                                                    RouteRule(
                                                        prefix = "/api/hello",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.SESSION_BASED,
                                                    ),
                                                ),
                                        ),
                                    oauthClients =
                                        mapOf(
                                            "" to
                                                OauthClient(
                                                    clientId = "test-client",
                                                    clientSecret = "test-secret",
                                                    scopes = listOf("read", "write"),
                                                    authorizeUrl = "http://idp:8080/authorize",
                                                    tokenUrl = "http://idp:8080/token",
                                                    issuer = "http://idp:8080",
                                                    jwksUrl = "http://idp:8080/jwks",
                                                    logoutUrl = "http://idp:8080/logout",
                                                ),
                                        ),
                                ),
                            idpClient = idpClient,
                            proxyClient = proxyClient,
                        )
                    }
                }
                install(ContentNegotiation) {
                    jackson { enable(SerializationFeature.INDENT_OUTPUT) }
                    gson {}
                    json()
                }
                configureIdpLoginRoutes()
            }

            val client =
                createClient {
                    followRedirects = false
                }
            val response = client.get("/gateway/logout")

            assertEquals(302, response.status.value)
            assertContains(
                response.headers["Location"] ?: "",
                "http://idp:8080/logout?client_id=test-client",
            )
        }

    @Test
    fun testLogoutRedirectErrorNoIdpClientForDomain() =
        testApplication {
            application {
                dependencies {
                    provide {
                        loginService
                    }
                    provide<ProxyService> {
                        DefaultProxyService(
                            proxyConfig =
                                ProxyConfig(
                                    enabled = true,
                                    routes =
                                        mapOf(
                                            "" to
                                                listOf(
                                                    RouteRule(
                                                        prefix = "/api/hello",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.SESSION_BASED,
                                                    ),
                                                ),
                                        ),
                                    oauthClients =
                                        mapOf(
                                            "example.com" to
                                                OauthClient(
                                                    clientId = "test-client",
                                                    clientSecret = "test-secret",
                                                    scopes = listOf("read", "write"),
                                                    authorizeUrl = "http://idp:8080/authorize",
                                                    tokenUrl = "http://idp:8080/token",
                                                    issuer = "http://idp:8080",
                                                    jwksUrl = "http://idp:8080/jwks",
                                                    logoutUrl = "http://idp:8080/logout",
                                                ),
                                        ),
                                ),
                            idpClient = idpClient,
                            proxyClient = proxyClient,
                        )
                    }
                }
                install(ContentNegotiation) {
                    jackson { enable(SerializationFeature.INDENT_OUTPUT) }
                    gson {}
                    json()
                }
                configureIdpLoginRoutes()
            }

            val client =
                createClient {
                    followRedirects = false
                }
            val response = client.get("/gateway/logout")

            assertEquals(404, response.status.value)
            assertContains(response.bodyAsText(), "No oauth client found for domain")
        }

    @Test
    fun testOauthCallbackSuccess() =
        testApplication {
            application {
                install(Sessions) {
                    cookie<GatewayUserSession>("GATEWAY_USER_SESSION") {
                        cookie.httpOnly = true
                        cookie.secure = false
                        cookie.maxAgeInSeconds = 31536000
                    }
                    cookie<String>("GATEWAY_ORIGINAL_URL") {
                        cookie.httpOnly = true
                        cookie.secure = false
                    }
                }

                dependencies {
                    provide<LoginService> {
                        DefaultLoginService(
                            idpClient = idpClient,
                        )
                    }
                    provide<ProxyService> {
                        DefaultProxyService(
                            proxyConfig =
                                ProxyConfig(
                                    enabled = true,
                                    routes =
                                        mapOf(
                                            "" to
                                                listOf(
                                                    RouteRule(
                                                        prefix = "/api/hello",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.SESSION_BASED,
                                                    ),
                                                ),
                                        ),
                                    oauthClients =
                                        mapOf(
                                            "" to
                                                OauthClient(
                                                    clientId = "test-client",
                                                    clientSecret = "test-secret",
                                                    scopes = listOf("read", "write"),
                                                    authorizeUrl = "http://idp:8080/authorize",
                                                    tokenUrl = "http://idp:8080/token",
                                                    issuer = "http://idp:8080",
                                                    jwksUrl = "http://idp:8080/jwks",
                                                    logoutUrl = "http://idp:8080/logout",
                                                ),
                                        ),
                                ),
                            idpClient = idpClient,
                            proxyClient = proxyClient,
                        )
                    }
                }
                install(ContentNegotiation) {
                    jackson { enable(SerializationFeature.INDENT_OUTPUT) }
                    gson {}
                    json()
                }
                configureIdpLoginRoutes()
                install(TestSessionOriginalUrlPlugin) {
                    url = "http://localhost:8080/original"
                }
            }

            given(idpClient.fetchAccessToken(any(), any(), any())).willReturn(
                CallResult.Success(
                    OauthTokenResponse(
                        access_token = "test-access-token",
                        refresh_token = "test-refresh-token",
                        token_type = "bearer",
                        expires_in = 3600,
                    ),
                ),
            )

            val client =
                createClient {
                    followRedirects = false
                }
            val response = client.get("/gateway/callback?code=code")

            assertEquals(302, response.status.value)
            assertContains(
                response.headers["Location"] ?: "",
                "http://localhost:8080/original",
            )
        }

    @Test
    fun testOauthCallbackErrorCodeMissing() =
        testApplication {
            application {
                dependencies {
                    provide<LoginService> {
                        DefaultLoginService(
                            idpClient = idpClient,
                        )
                    }
                    provide<ProxyService> {
                        DefaultProxyService(
                            proxyConfig =
                                ProxyConfig(
                                    enabled = true,
                                    routes =
                                        mapOf(
                                            "" to
                                                listOf(
                                                    RouteRule(
                                                        prefix = "/api/hello",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.SESSION_BASED,
                                                    ),
                                                ),
                                        ),
                                    oauthClients =
                                        mapOf(
                                            "" to
                                                OauthClient(
                                                    clientId = "test-client",
                                                    clientSecret = "test-secret",
                                                    scopes = listOf("read", "write"),
                                                    authorizeUrl = "http://idp:8080/authorize",
                                                    tokenUrl = "http://idp:8080/token",
                                                    issuer = "http://idp:8080",
                                                    jwksUrl = "http://idp:8080/jwks",
                                                    logoutUrl = "http://idp:8080/logout",
                                                ),
                                        ),
                                ),
                            idpClient = idpClient,
                            proxyClient = proxyClient,
                        )
                    }
                }
                install(ContentNegotiation) {
                    jackson { enable(SerializationFeature.INDENT_OUTPUT) }
                    gson {}
                    json()
                }
                configureIdpLoginRoutes()
            }

            val client =
                createClient {
                    followRedirects = false
                }
            val response = client.get("/gateway/callback")

            assertEquals(400, response.status.value)
            assertContains(
                response.bodyAsText(),
                "MISSING_CODE",
            )
        }

    @ParameterizedTest
    @EnumSource(IdpClientErrorCode::class)
    fun testOauthCallbackErrors(idpClientErrorCode: IdpClientErrorCode) =
        testApplication {
            application {
                install(Sessions) {
                    cookie<GatewayUserSession>("GATEWAY_USER_SESSION") {
                        cookie.httpOnly = true
                        cookie.secure = false
                        cookie.maxAgeInSeconds = 31536000
                    }
                    cookie<String>("GATEWAY_ORIGINAL_URL") {
                        cookie.httpOnly = true
                        cookie.secure = false
                    }
                }

                dependencies {
                    provide<LoginService> {
                        DefaultLoginService(
                            idpClient = idpClient,
                        )
                    }
                    provide<ProxyService> {
                        DefaultProxyService(
                            proxyConfig =
                                ProxyConfig(
                                    enabled = true,
                                    routes =
                                        mapOf(
                                            "" to
                                                listOf(
                                                    RouteRule(
                                                        prefix = "/api/hello",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.SESSION_BASED,
                                                    ),
                                                ),
                                        ),
                                    oauthClients =
                                        mapOf(
                                            "" to
                                                OauthClient(
                                                    clientId = "test-client",
                                                    clientSecret = "test-secret",
                                                    scopes = listOf("read", "write"),
                                                    authorizeUrl = "http://idp:8080/authorize",
                                                    tokenUrl = "http://idp:8080/token",
                                                    issuer = "http://idp:8080",
                                                    jwksUrl = "http://idp:8080/jwks",
                                                    logoutUrl = "http://idp:8080/logout",
                                                ),
                                        ),
                                ),
                            idpClient = idpClient,
                            proxyClient = proxyClient,
                        )
                    }
                }
                install(ContentNegotiation) {
                    jackson { enable(SerializationFeature.INDENT_OUTPUT) }
                    gson {}
                    json()
                }
                configureIdpLoginRoutes()
                install(TestSessionOriginalUrlPlugin) {
                    url = "http://localhost:8080/original"
                }
            }

            given(idpClient.fetchAccessToken(any(), any(), any())).willReturn(
                CallResult.Failure(idpClientErrorCode),
            )

            val client =
                createClient {
                    followRedirects = false
                }
            val response = client.get("/gateway/callback?code=code")

            val expectedStatusCode =
                when (idpClientErrorCode) {
                    IdpClientErrorCode.UNAUTHORIZED -> 401
                    IdpClientErrorCode.BAD_REQUEST -> 400
                }

            assertEquals(expectedStatusCode, response.status.value)
            assertContains(
                response.bodyAsText(),
                idpClientErrorCode.name,
            )
        }

    @Test
    fun testLoginRedirectErrorNoIdpClientForDomainWithHttps() =
        testApplication {
            application {
                dependencies {
                    provide {
                        loginService
                    }
                    provide<ProxyService> {
                        DefaultProxyService(
                            proxyConfig =
                                ProxyConfig(
                                    enabled = true,
                                    routes =
                                        mapOf(
                                            "" to
                                                listOf(
                                                    RouteRule(
                                                        prefix = "/api/hello",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.SESSION_BASED,
                                                    ),
                                                ),
                                        ),
                                    oauthClients =
                                        mapOf(
                                            "example.com" to
                                                OauthClient(
                                                    clientId = "test-client",
                                                    clientSecret = "test-secret",
                                                    scopes = listOf("read", "write"),
                                                    authorizeUrl = "http://idp:8080/authorize",
                                                    tokenUrl = "http://idp:8080/token",
                                                    issuer = "http://idp:8080",
                                                    jwksUrl = "http://idp:8080/jwks",
                                                    logoutUrl = "http://idp:8080/logout",
                                                ),
                                        ),
                                ),
                            idpClient = idpClient,
                            proxyClient = proxyClient,
                        )
                    }
                }
                install(ContentNegotiation) {
                    jackson { enable(SerializationFeature.INDENT_OUTPUT) }
                    gson {}
                    json()
                }
                configureIdpLoginRoutes()
            }

            val client =
                createClient {
                    followRedirects = false
                }
            val response = client.get("https://localhost/gateway/login")

            assertEquals(404, response.status.value)
            assertContains(response.bodyAsText(), "No oauth client found for domain")
        }

    @Test
    fun testLoginRedirectErrorNoIdpClientForDomainWithDifferentPort() =
        testApplication {
            application {
                dependencies {
                    provide {
                        loginService
                    }
                    provide<ProxyService> {
                        DefaultProxyService(
                            proxyConfig =
                                ProxyConfig(
                                    enabled = true,
                                    routes =
                                        mapOf(
                                            "" to
                                                listOf(
                                                    RouteRule(
                                                        prefix = "/api/hello",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.SESSION_BASED,
                                                    ),
                                                ),
                                        ),
                                    oauthClients =
                                        mapOf(
                                            "example.com" to
                                                OauthClient(
                                                    clientId = "test-client",
                                                    clientSecret = "test-secret",
                                                    scopes = listOf("read", "write"),
                                                    authorizeUrl = "http://idp:8080/authorize",
                                                    tokenUrl = "http://idp:8080/token",
                                                    issuer = "http://idp:8080",
                                                    jwksUrl = "http://idp:8080/jwks",
                                                    logoutUrl = "http://idp:8080/logout",
                                                ),
                                        ),
                                ),
                            idpClient = idpClient,
                            proxyClient = proxyClient,
                        )
                    }
                }
                install(ContentNegotiation) {
                    jackson { enable(SerializationFeature.INDENT_OUTPUT) }
                    gson {}
                    json()
                }
                configureIdpLoginRoutes()
            }

            val client =
                createClient {
                    followRedirects = false
                }
            val response = client.get("http://localhost:443/gateway/login")

            assertEquals(404, response.status.value)
            assertContains(response.bodyAsText(), "No oauth client found for domain")
        }
}
