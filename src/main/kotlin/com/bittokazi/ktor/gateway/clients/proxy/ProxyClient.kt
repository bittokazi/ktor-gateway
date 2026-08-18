package com.bittokazi.ktor.gateway.clients.proxy

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.headers
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ProxyClient(
    private val client: HttpClient,
    private val requestTimeout: Long = 60_000,
    private val connectTimeout: Long = 20_000,
    private val socketTimeout: Long = 60_000,
) {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    init {
        log.info("[INFO] ProxyClient is created")
    }

    suspend fun request(
        call: ApplicationCall,
        targetUrl: String,
    ): HttpResponse =
        client.request(targetUrl) {
            method = call.request.httpMethod

            timeout {
                requestTimeoutMillis = requestTimeout
                connectTimeoutMillis = connectTimeout
                socketTimeoutMillis = socketTimeout
            }

            // Copy headers, body, etc.
            headers.appendAll(call.request.headers)

            setBody(call.receiveChannel())
        }
}
