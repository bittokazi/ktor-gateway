package com.bittokazi.ktor.gateway.clients

import com.auth0.jwk.JwkProviderBuilder
import com.bittokazi.ktor.gateway.clients.idp.IdpClient
import com.bittokazi.ktor.gateway.clients.proxy.ProxyClient
import com.bittokazi.ktor.gateway.common.GatewayConfig
import com.bittokazi.ktor.gateway.proxy.config.ProxyConfig
import com.bittokazi.ktor.gateway.security.services.DefaultCustomJwkProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import kotlinx.serialization.json.Json
import java.net.URI

fun Application.configureClientModule(gatewayConfig: GatewayConfig) {
    val proxyConfig: ProxyConfig by dependencies

    dependencies {
        provide {
            IdpClient(
                HttpClient(CIO) {
                    install(ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                            },
                        )
                    }
                },
                DefaultCustomJwkProvider(
                    providers =
                        proxyConfig.oauthClients.entries.associate { oauthClientConfig ->
                            oauthClientConfig.value.issuer to
                                JwkProviderBuilder(URI(oauthClientConfig.value.jwksUrl).toURL()).build()
                        },
                ),
            )
        }

        provide {
            ProxyClient(
                HttpClient(CIO) {
                    followRedirects = false

                    install(ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                            },
                        )
                    }
                },
                requestTimeout = gatewayConfig.requestTimeoutMillis,
                connectTimeout = gatewayConfig.connectTimeoutMillis,
                socketTimeout = gatewayConfig.socketTimeoutMillis,
            )
        }
    }
}
