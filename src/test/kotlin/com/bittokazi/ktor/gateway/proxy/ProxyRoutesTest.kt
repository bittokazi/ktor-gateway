package com.bittokazi.ktor.gateway.proxy

import com.bittokazi.ktor.auth.domains.token.OauthTokenResponse
import com.bittokazi.ktor.gateway.clients.idp.IdpClient
import com.bittokazi.ktor.gateway.clients.idp.IdpClientErrorCode
import com.bittokazi.ktor.gateway.clients.idp.entity.TokenIntrospectResult
import com.bittokazi.ktor.gateway.clients.proxy.ProxyClient
import com.bittokazi.ktor.gateway.common.AuthType
import com.bittokazi.ktor.gateway.common.CallResult
import com.bittokazi.ktor.gateway.common.OauthClient
import com.bittokazi.ktor.gateway.common.RouteRule
import com.bittokazi.ktor.gateway.config.TestSessionPlugin
import com.bittokazi.ktor.gateway.proxy.config.ProxyConfig
import com.bittokazi.ktor.gateway.proxy.routes.proxyRoutes
import com.bittokazi.ktor.gateway.proxy.services.DefaultProxyService
import com.bittokazi.ktor.gateway.proxy.services.NoopProxyService
import com.bittokazi.ktor.gateway.proxy.services.ProxyService
import com.bittokazi.ktor.gateway.security.GatewayUserSession
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.client.call.HttpClientCall
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.DefaultHttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.serialization.jackson.jackson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.testing.testApplication
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.currentCoroutineContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
class ProxyRoutesTest {
    @Mock
    lateinit var idpClient: IdpClient

    @Mock
    lateinit var proxyClient: ProxyClient

    @Mock
    lateinit var httpClientCall: HttpClientCall

    @ParameterizedTest
    @ValueSource(ints = [200, 201, 400, 404, 500])
    fun testProxyRoutesGetsResponseOnCorrectRule(statusCodeValue: Int) =
        testApplication {
            application {
                dependencies {
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
                                                        authType = AuthType.NONE,
                                                    ),
                                                ),
                                        ),
                                ),
                            idpClient = idpClient,
                            proxyClient = proxyClient,
                        )
                    }
                }
                install(ContentNegotiation) {
                    jackson {
                        enable(SerializationFeature.INDENT_OUTPUT)
                    }

                    gson {}

                    json()
                }
                proxyRoutes()
            }

            val body = ByteReadChannel("handled: /api/hello?x=1")
            val statusCode = HttpStatusCode.fromValue(statusCodeValue)

            given(proxyClient.request(any(), any())).willReturn(
                DefaultHttpResponse(
                    call = httpClientCall,
                    responseData =
                        HttpResponseData(
                            statusCode = statusCode,
                            requestTime = GMTDate.START,
                            headers =
                                Headers.build {
                                    this["Content-Type"] = "text/plain"
                                },
                            version = HttpProtocolVersion.HTTP_1_1,
                            body = body,
                            callContext = currentCoroutineContext(),
                        ),
                ),
            )
            given(httpClientCall.bodyNullable(any())).willReturn(body)

            val client = createClient { }
            val response = client.get("/api/hello?x=1")

            assertEquals(statusCodeValue, response.status.value)
            assertEquals("handled: /api/hello?x=1", response.bodyAsText())
        }

    @Test
    fun testProxyRoutesGetsResponseErrorOnRouteNotFound() =
        testApplication {
            application {
                dependencies {
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
                                                        prefix = "/api/hello/world",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.NONE,
                                                    ),
                                                ),
                                        ),
                                ),
                            idpClient = idpClient,
                            proxyClient = proxyClient,
                        )
                    }
                }
                install(ContentNegotiation) {
                    jackson {
                        enable(SerializationFeature.INDENT_OUTPUT)
                    }

                    gson {}

                    json()
                }
                proxyRoutes()
            }

            val client = createClient { }
            val response = client.get("/api/hello?x=1")

            assertEquals(404, response.status.value)
            assertContains(response.bodyAsText(), "No route found for path")
        }

    @Test
    fun testProxyRoutesGetsResponseErrorOnUpstreamError() =
        testApplication {
            application {
                dependencies {
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
                                                        authType = AuthType.NONE,
                                                    ),
                                                ),
                                        ),
                                ),
                            idpClient = idpClient,
                            proxyClient = proxyClient,
                        )
                    }
                }
                install(ContentNegotiation) {
                    jackson {
                        enable(SerializationFeature.INDENT_OUTPUT)
                    }

                    gson {}

                    json()
                }
                proxyRoutes()
            }

            given(proxyClient.request(any(), any())).willThrow(
                RuntimeException("Upstream error"),
            )

            val client = createClient { }
            val response = client.get("/api/hello?x=1")

            assertEquals(502, response.status.value)
            assertContains(response.bodyAsText(), "Upstream error")
        }

    @Test
    fun testProxyRoutesGetsResponseErrorOnRouteNotFoundOnHttps() =
        testApplication {
            application {
                dependencies {
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
                                                        prefix = "/api/hello/world",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.NONE,
                                                    ),
                                                ),
                                        ),
                                ),
                            idpClient = idpClient,
                            proxyClient = proxyClient,
                        )
                    }
                }
                install(ContentNegotiation) {
                    jackson {
                        enable(SerializationFeature.INDENT_OUTPUT)
                    }

                    gson {}

                    json()
                }
                proxyRoutes()
            }

            val client = createClient { }
            val response = client.get("https://localhost:443/api/hello?x=1")

            assertEquals(404, response.status.value)
            assertContains(response.bodyAsText(), "No route found for path")
        }

    @Test
    fun testProxyRoutesGetsResponseErrorOnRouteNotFoundOnHttpWithDifferentPort() =
        testApplication {
            application {
                dependencies {
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
                                                        prefix = "/api/hello/world",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.NONE,
                                                    ),
                                                ),
                                        ),
                                ),
                            idpClient = idpClient,
                            proxyClient = proxyClient,
                        )
                    }
                }
                install(ContentNegotiation) {
                    jackson {
                        enable(SerializationFeature.INDENT_OUTPUT)
                    }

                    gson {}

                    json()
                }
                proxyRoutes()
            }

            val client = createClient { }
            val response = client.get("http://localhost:443/api/hello?x=1")

            assertEquals(404, response.status.value)
            assertContains(response.bodyAsText(), "No route found for path")
        }

    // ============ SESSION_BASED Authentication Tests ============

    @Test
    fun testSessionBasedAuthWithValidSession() =
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
                                                        prefix = "/api/protected",
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
                                                    introspectUrl = "http://idp:8080/introspect",
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
                proxyRoutes()
                install(TestSessionPlugin) {
                    expiresAt = System.currentTimeMillis() + 60000
                    refreshToken = "refresh_token"
                }
            }

            val body = ByteReadChannel("session protected content")
            given(proxyClient.request(any(), any())).willReturn(
                DefaultHttpResponse(
                    call = httpClientCall,
                    responseData =
                        HttpResponseData(
                            statusCode = HttpStatusCode.OK,
                            requestTime = GMTDate.START,
                            headers = Headers.Empty,
                            version = HttpProtocolVersion.HTTP_1_1,
                            body = body,
                            callContext = currentCoroutineContext(),
                        ),
                ),
            )
            given(httpClientCall.bodyNullable(any())).willReturn(body)

            val client = createClient { }
            // First request to set session - valid session flow
            val response = client.get("/api/protected")
            assertEquals(200, response.status.value)
            assertEquals("session protected content", response.bodyAsText())
        }

    @Test
    fun testSessionBasedAuthWithExpiredSessionAndSuccessfulRefresh() =
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
                                                        prefix = "/api/protected",
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
                                                    introspectUrl = "http://idp:8080/introspect",
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
                proxyRoutes()
                install(TestSessionPlugin)
            }

            val body = ByteReadChannel("refreshed and proxied content")
            given(proxyClient.request(any(), any())).willReturn(
                DefaultHttpResponse(
                    call = httpClientCall,
                    responseData =
                        HttpResponseData(
                            statusCode = HttpStatusCode.OK,
                            requestTime = GMTDate.START,
                            headers = Headers.Empty,
                            version = HttpProtocolVersion.HTTP_1_1,
                            body = body,
                            callContext = currentCoroutineContext(),
                        ),
                ),
            )
            given(httpClientCall.bodyNullable(any())).willReturn(body)

            // Mock successful token refresh
            given(idpClient.fetchRefreshToken(any(), any())).willReturn(
                CallResult.Success(
                    OauthTokenResponse(
                        access_token = "new-access-token",
                        refresh_token = "new-refresh-token",
                        token_type = "Bearer",
                        expires_in = 3600,
                    ),
                ),
            )

            val client = createClient { }
            val response = client.get("/api/protected")
            // Refresh token should be called when session is expired or missing
            assertEquals(200, response.status.value)
            assertEquals("refreshed and proxied content", response.bodyAsText())
        }

    @Test
    fun testSessionBasedAuthWithExpiredSessionAndFailedRefresh() =
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
                                                        prefix = "/api/protected",
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
                                                    introspectUrl = "http://idp:8080/introspect",
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
                proxyRoutes()
                install(TestSessionPlugin)
            }

            // Mock failed token refresh
            given(idpClient.fetchRefreshToken(any(), any())).willReturn(
                CallResult.Failure(IdpClientErrorCode.UNAUTHORIZED),
            )

            val client =
                createClient {
                    followRedirects = false
                }
            val response = client.get("/api/protected")

            assertEquals(302, response.status.value)
            assertContains(response.headers["Location"] ?: "", "/gateway/login")
        }

    @Test
    fun testSessionBasedAuthWithNoSession() =
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
                                                        prefix = "/api/protected",
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
                                                    introspectUrl = "http://idp:8080/introspect",
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
                proxyRoutes()
            }

            val client =
                createClient {
                    followRedirects = false
                }
            val response = client.get("/api/protected")

            assertEquals(302, response.status.value)
            assertContains(response.headers["Location"] ?: "", "/gateway/login")
        }

    @Test
    fun testSessionBasedAuthWithNoOauthConfig() =
        testApplication {
            application {
                install(Sessions) {
                    cookie<GatewayUserSession>("GATEWAY_USER_SESSION")
                }
                dependencies {
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
                                                        prefix = "/api/protected",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.SESSION_BASED,
                                                    ),
                                                ),
                                        ),
                                    oauthClients = mapOf(), // No oauth config
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
                proxyRoutes()
            }

            val client = createClient { }
            val response = client.get("/api/protected")

            assertEquals(422, response.status.value)
            assertContains(response.bodyAsText(), "No oauth config found for domain")
        }

    // ============ TOKEN_BASED Authentication Tests ============

    @Test
    fun testTokenBasedAuthWithValidActiveToken() =
        testApplication {
            application {
                dependencies {
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
                                                        prefix = "/api/token-protected",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.TOKEN_BASED,
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
                                                    introspectUrl = "http://idp:8080/introspect",
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
                proxyRoutes()
            }

            val body = ByteReadChannel("token protected content")
            given(proxyClient.request(any(), any())).willReturn(
                DefaultHttpResponse(
                    call = httpClientCall,
                    responseData =
                        HttpResponseData(
                            statusCode = HttpStatusCode.OK,
                            requestTime = GMTDate.START,
                            headers = Headers.Empty,
                            version = HttpProtocolVersion.HTTP_1_1,
                            body = body,
                            callContext = currentCoroutineContext(),
                        ),
                ),
            )
            given(httpClientCall.bodyNullable(any())).willReturn(body)

            // Mock successful token introspection with active token
            given(idpClient.tokenIntrospect(any(), any())).willReturn(
                CallResult.Success(TokenIntrospectResult(active = true)),
            )

            val client = createClient { }
            val response =
                client.get("/api/token-protected") {
                    header("Authorization", "Bearer valid-access-token-123")
                }

            assertEquals(200, response.status.value)
            assertEquals("token protected content", response.bodyAsText())
        }

    @Test
    fun testTokenBasedAuthWithValidButInactiveToken() =
        testApplication {
            application {
                dependencies {
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
                                                        prefix = "/api/token-protected",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.TOKEN_BASED,
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
                                                    introspectUrl = "http://idp:8080/introspect",
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
                proxyRoutes()
            }

            // Mock successful token introspection but token is inactive (expired)
            given(idpClient.tokenIntrospect(any(), any())).willReturn(
                CallResult.Success(TokenIntrospectResult(active = false)),
            )

            val client = createClient { }
            val response =
                client.get("/api/token-protected") {
                    header("Authorization", "Bearer expired-token-123")
                }

            assertEquals(401, response.status.value)
            assertContains(response.bodyAsText(), "Token expired")
        }

    @Test
    fun testTokenBasedAuthWithInvalidToken() =
        testApplication {
            application {
                dependencies {
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
                                                        prefix = "/api/token-protected",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.TOKEN_BASED,
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
                                                    introspectUrl = "http://idp:8080/introspect",
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
                proxyRoutes()
            }

            // Mock token introspection failure
            given(idpClient.tokenIntrospect(any(), any())).willReturn(
                CallResult.Failure(IdpClientErrorCode.BAD_REQUEST),
            )

            val client = createClient { }
            val response =
                client.get("/api/token-protected") {
                    header("Authorization", "Bearer invalid-token-123")
                }

            assertEquals(401, response.status.value)
            assertContains(response.bodyAsText(), "Invalid token")
        }

    @Test
    fun testTokenBasedAuthWithMissingToken() =
        testApplication {
            application {
                dependencies {
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
                                                        prefix = "/api/token-protected",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.TOKEN_BASED,
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
                                                    introspectUrl = "http://idp:8080/introspect",
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
                proxyRoutes()
            }

            val client = createClient { }
            val response = client.get("/api/token-protected")
            // No Authorization header

            assertEquals(401, response.status.value)
            assertContains(response.bodyAsText(), "Invalid / Missing token")
        }

    @Test
    fun testTokenBasedAuthWithInvalidAuthScheme() =
        testApplication {
            application {
                dependencies {
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
                                                        prefix = "/api/token-protected",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.TOKEN_BASED,
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
                                                    introspectUrl = "http://idp:8080/introspect",
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
                proxyRoutes()
            }

            val client = createClient { }
            val response =
                client.get("/api/token-protected") {
                    header("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ=")
                }
            // Using Basic auth instead of Bearer

            assertEquals(401, response.status.value)
            assertContains(response.bodyAsText(), "Invalid / Missing token")
        }

    @Test
    fun testTokenBasedAuthWithNoOauthConfig() =
        testApplication {
            application {
                dependencies {
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
                                                        prefix = "/api/token-protected",
                                                        target = "http://localhost:8080",
                                                        authType = AuthType.TOKEN_BASED,
                                                    ),
                                                ),
                                        ),
                                    oauthClients = mapOf(), // No oauth config
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
                proxyRoutes()
            }

            val client = createClient { }
            val response =
                client.get("/api/token-protected") {
                    header("Authorization", "Bearer valid-token-123")
                }

            assertEquals(422, response.status.value)
            assertContains(response.bodyAsText(), "No oauth config found for domain")
        }

    @ParameterizedTest
    @ValueSource(ints = [200, 201, 400, 404, 500])
    fun testProxyRoutesGetsResponseNoopProxy() =
        testApplication {
            application {
                dependencies {
                    provide<ProxyService> {
                        NoopProxyService()
                    }
                }
                install(ContentNegotiation) {
                    jackson {
                        enable(SerializationFeature.INDENT_OUTPUT)
                    }

                    gson {}

                    json()
                }
                proxyRoutes()
            }

            val client = createClient { }
            val response = client.get("/api/hello?x=1")

            assertEquals(502, response.status.value)
            assertContains(response.bodyAsText(), "No Proxy Client is Enabled")
        }
}
