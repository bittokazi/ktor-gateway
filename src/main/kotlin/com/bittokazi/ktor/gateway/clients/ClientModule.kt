package com.bittokazi.ktor.gateway.clients

import com.bittokazi.ktor.gateway.clients.idp.IdpClient
import com.bittokazi.ktor.gateway.clients.proxy.ProxyClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import kotlinx.serialization.json.Json

fun Application.configureClientModule() {
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
            )
        }
    }
}
