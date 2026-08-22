package com.bittokazi.ktor.gateway.clients.proxy

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

class ProxyClientTest {
    @Test
    fun `request forwards call and returns proxied response`() =
        testApplication {
            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = "proxied content",
                        status = io.ktor.http.HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "text/plain"),
                    )
                }

            val httpClient =
                HttpClient(mockEngine) {
                    install(ClientContentNegotiation) {
                        json()
                    }
                }

            val proxyClient = ProxyClient(httpClient)

            application {
                routing {
                    post("/proxy") {
                        val resp = proxyClient.request(call, "http://upstream/target")
                        val body = resp.bodyAsText()
                        call.respondText(body, status = resp.status)
                    }
                }
            }

            val client = createClient { }
            val response =
                client.post("/proxy") {
                    setBody("hello-from-client")
                    header("X-Test", "1")
                }

            assertEquals(200, response.status.value)
            assertEquals("proxied content", response.bodyAsText())
        }

    @Test
    fun `request forwards call and returns proxied response with skip hop by hop header false`() =
        testApplication {
            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content = "proxied content",
                        status = io.ktor.http.HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "text/plain"),
                    )
                }

            val httpClient =
                HttpClient(mockEngine) {
                    install(ClientContentNegotiation) {
                        json()
                    }
                }

            val proxyClient = ProxyClient(httpClient)

            application {
                routing {
                    post("/proxy") {
                        val resp = proxyClient.request(call, "http://upstream/target", false)
                        val body = resp.bodyAsText()
                        call.respondText(body, status = resp.status)
                    }
                }
            }

            val client = createClient { }
            val response =
                client.post("/proxy") {
                    setBody("hello-from-client")
                    header("X-Test", "1")
                }

            assertEquals(200, response.status.value)
            assertEquals("proxied content", response.bodyAsText())
        }
}
