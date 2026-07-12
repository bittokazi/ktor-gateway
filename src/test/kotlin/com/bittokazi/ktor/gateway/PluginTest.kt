package com.bittokazi.ktor.gateway

import com.bittokazi.ktor.gateway.common.AuthType
import com.bittokazi.ktor.gateway.common.RouteRule
import com.bittokazi.ktor.gateway.proxy.config.ProxyConfig
import io.ktor.client.request.get
import io.ktor.server.application.install
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.InternalAPI
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.times
import kotlin.test.assertTrue

@OptIn(InternalAPI::class)
@RunWith(MockitoJUnitRunner::class)
@ExtendWith(MockitoExtension::class)
class PluginTest {
    @Test
    fun testGatewayPluginConfig() =
        testApplication {
            application {
                install(GatewayPlugin) {
                    gatewayOauthBasePath = "/gateway"
                    sessionValidityInSeconds = 3600
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
                        )
                }

                val proxyConfig: ProxyConfig by dependencies

                assertTrue(proxyConfig.enabled)
                assertEquals(1, proxyConfig.routes.size)
            }
        }
}
