package com.bittokazi.ktor.gateway

import com.bittokazi.ktor.gateway.common.AuthType
import com.bittokazi.ktor.gateway.common.OauthClient
import com.bittokazi.ktor.gateway.common.RouteRule
import com.bittokazi.ktor.gateway.printer.service.TableInfoPrinterService
import com.bittokazi.ktor.gateway.proxy.config.ProxyConfig
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.InternalAPI
import org.junit.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@OptIn(InternalAPI::class)
@RunWith(MockitoJUnitRunner::class)
@ExtendWith(MockitoExtension::class)
class ApplicationEventListenersTest {
    @Mock
    lateinit var tableInfoPrinterService: TableInfoPrinterService

    @Test
    fun testLoginRedirectSuccessAfterRulesValidated() =
        testApplication {
            application {
                dependencies {
                    provide {
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
                                            issuer = "http://idp:8080",
                                            jwksUrl = "http://idp:8080/jwks",
                                            logoutUrl = "http://idp:8080/logout",
                                        ),
                                ),
                        )
                    }
                    provide {
                        tableInfoPrinterService
                    }
                }

                applicationEventListeners()

                monitor.subscribe(ApplicationStarted) { application ->
                    verify(tableInfoPrinterService, times(2)).printTable(
                        any(),
                        any(),
                    )
                }
            }
        }
}
