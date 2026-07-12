package com.bittokazi.ktor.gateway.clients.idp

import com.bittokazi.ktor.gateway.clients.idp.entity.RefreshTokenRequest
import com.bittokazi.ktor.gateway.common.CallResult
import com.bittokazi.ktor.gateway.common.OauthClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.RequestConnectionPoint
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.ApplicationRequest
import io.ktor.util.Attributes
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class IdpClientTest {
    @Mock
    lateinit var call: ApplicationCall

    @Mock
    lateinit var request: ApplicationRequest

    @Mock
    lateinit var attributes: Attributes

    @Mock
    lateinit var origin: RequestConnectionPoint

    @Test
    fun `fetchRefreshToken returns success when idp responds with 200`() =
        runTest {
            val responseJson =
                """{"access_token":"test-access-token","refresh_token":"test-refresh-token-2","token_type":"bearer","expires_in":3600}"""

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val client =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val idpClient = IdpClient(client)

            val oauthClient =
                OauthClient(
                    clientId = "test-client",
                    clientSecret = "test-secret",
                    scopes = listOf("read"),
                    authorizeUrl = "http://idp/authorize",
                    tokenUrl = "http://idp/token",
                    introspectUrl = "http://idp/introspect",
                    logoutUrl = "http://idp/logout",
                )

            val result = idpClient.fetchRefreshToken(RefreshTokenRequest("test-refresh-token"), oauthClient)

            assertTrue(result is CallResult.Success)
            val outcome = result.outcome

            // verify fields were parsed from JSON
            assertEquals("test-access-token", outcome.access_token)
            assertEquals("test-refresh-token-2", outcome.refresh_token)
            assertEquals("bearer", outcome.token_type)
            assertEquals(3600, outcome.expires_in)
        }

    @Test
    fun `fetchRefreshToken returns failure when idp responds with 401`() =
        runTest {
            val responseJson = ""

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val client =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val idpClient = IdpClient(client)

            val oauthClient =
                OauthClient(
                    clientId = "test-client",
                    clientSecret = "test-secret",
                    scopes = listOf("read"),
                    authorizeUrl = "http://idp/authorize",
                    tokenUrl = "http://idp/token",
                    introspectUrl = "http://idp/introspect",
                    logoutUrl = "http://idp/logout",
                )

            val result = idpClient.fetchRefreshToken(RefreshTokenRequest("test-refresh-token"), oauthClient)

            assertTrue(result is CallResult.Failure)
            assertEquals(IdpClientErrorCode.UNAUTHORIZED, result.errorCode)
        }

    @ParameterizedTest
    @ValueSource(ints = [404, 400, 422, 403, 500, 502, 406])
    fun `fetchRefreshToken returns failure when idp responds with different status codes`(statusCode: Int) =
        runTest {
            val responseJson = ""

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.fromValue(statusCode),
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val client =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val idpClient = IdpClient(client)

            val oauthClient =
                OauthClient(
                    clientId = "test-client",
                    clientSecret = "test-secret",
                    scopes = listOf("read"),
                    authorizeUrl = "http://idp/authorize",
                    tokenUrl = "http://idp/token",
                    introspectUrl = "http://idp/introspect",
                    logoutUrl = "http://idp/logout",
                )

            val result = idpClient.fetchRefreshToken(RefreshTokenRequest("test-refresh-token"), oauthClient)

            assertTrue(result is CallResult.Failure)
            assertEquals(IdpClientErrorCode.BAD_REQUEST, result.errorCode)
        }

    @Test
    fun `fetchAccessToken returns success when idp responds with 200`() =
        runTest {
            setupCallMocks()

            val responseJson =
                """{"access_token":"test-access-token","refresh_token":"test-refresh-token-2","token_type":"bearer","expires_in":3600}"""

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val client =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val idpClient = IdpClient(client)

            val oauthClient =
                OauthClient(
                    clientId = "test-client",
                    clientSecret = "test-secret",
                    scopes = listOf("read"),
                    authorizeUrl = "http://idp/authorize",
                    tokenUrl = "http://idp/token",
                    introspectUrl = "http://idp/introspect",
                    logoutUrl = "http://idp/logout",
                )

            val result = idpClient.fetchAccessToken(call, "", oauthClient)

            assertTrue(result is CallResult.Success)
            val outcome = result.outcome

            // verify fields were parsed from JSON
            assertEquals("test-access-token", outcome.access_token)
            assertEquals("test-refresh-token-2", outcome.refresh_token)
            assertEquals("bearer", outcome.token_type)
            assertEquals(3600, outcome.expires_in)
        }

    @Test
    fun `fetchAccessToken returns failure when idp responds with 400`() =
        runTest {
            setupCallMocks()
            val responseJson = ""

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val client =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val idpClient = IdpClient(client)

            val oauthClient =
                OauthClient(
                    clientId = "test-client",
                    clientSecret = "test-secret",
                    scopes = listOf("read"),
                    authorizeUrl = "http://idp/authorize",
                    tokenUrl = "http://idp/token",
                    introspectUrl = "http://idp/introspect",
                    logoutUrl = "http://idp/logout",
                )

            val result = idpClient.fetchAccessToken(call, "", oauthClient)

            assertTrue(result is CallResult.Failure)
            assertEquals(IdpClientErrorCode.BAD_REQUEST, result.errorCode)
        }

    @Test
    fun `fetchAccessToken returns failure when idp responds with 401`() =
        runTest {
            setupCallMocks()
            val responseJson = ""

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val client =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val idpClient = IdpClient(client)

            val oauthClient =
                OauthClient(
                    clientId = "test-client",
                    clientSecret = "test-secret",
                    scopes = listOf("read"),
                    authorizeUrl = "http://idp/authorize",
                    tokenUrl = "http://idp/token",
                    introspectUrl = "http://idp/introspect",
                    logoutUrl = "http://idp/logout",
                )

            val result = idpClient.fetchAccessToken(call, "", oauthClient)

            assertTrue(result is CallResult.Failure)
            assertEquals(IdpClientErrorCode.UNAUTHORIZED, result.errorCode)
        }

    @Test
    fun `tokenIntrospect returns success when idp responds with 200`() =
        runTest {
            val responseJson =
                """{"active": "true"}"""

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val client =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val idpClient = IdpClient(client)

            val oauthClient =
                OauthClient(
                    clientId = "test-client",
                    clientSecret = "test-secret",
                    scopes = listOf("read"),
                    authorizeUrl = "http://idp/authorize",
                    tokenUrl = "http://idp/token",
                    introspectUrl = "http://idp/introspect",
                    logoutUrl = "http://idp/logout",
                )

            val result = idpClient.tokenIntrospect("test-token", oauthClient)

            assertTrue(result is CallResult.Success)
            val outcome = result.outcome

            // verify fields were parsed from JSON
            assertEquals(true, outcome.active)
        }

    @Test
    fun `tokenIntrospect returns failure when idp responds with 401`() =
        runTest {
            val responseJson = ""

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val client =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val idpClient = IdpClient(client)

            val oauthClient =
                OauthClient(
                    clientId = "test-client",
                    clientSecret = "test-secret",
                    scopes = listOf("read"),
                    authorizeUrl = "http://idp/authorize",
                    tokenUrl = "http://idp/token",
                    introspectUrl = "http://idp/introspect",
                    logoutUrl = "http://idp/logout",
                )

            val result = idpClient.tokenIntrospect("test-token", oauthClient)

            assertTrue(result is CallResult.Failure)
            assertEquals(IdpClientErrorCode.UNAUTHORIZED, result.errorCode)
        }

    @ParameterizedTest
    @ValueSource(ints = [404, 400, 422, 403, 500, 502, 406])
    fun `tokenIntrospect returns failure when idp responds with different status codes`(statusCode: Int) =
        runTest {
            val responseJson = ""

            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.fromValue(statusCode),
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val client =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val idpClient = IdpClient(client)

            val oauthClient =
                OauthClient(
                    clientId = "test-client",
                    clientSecret = "test-secret",
                    scopes = listOf("read"),
                    authorizeUrl = "http://idp/authorize",
                    tokenUrl = "http://idp/token",
                    introspectUrl = "http://idp/introspect",
                    logoutUrl = "http://idp/logout",
                )

            val result = idpClient.tokenIntrospect("test-token", oauthClient)

            assertTrue(result is CallResult.Failure)
            assertEquals(IdpClientErrorCode.BAD_REQUEST, result.errorCode)
        }

    private fun setupCallMocks() {
        given(call.request).willReturn(request)
        given(request.call).willReturn(call)
        given(call.attributes).willReturn(attributes)
        given(request.origin).willReturn(origin)
        given(origin.scheme).willReturn("https")
        given(origin.serverHost).willReturn("example.com")
        given(origin.serverPort).willReturn(443)
    }
}
