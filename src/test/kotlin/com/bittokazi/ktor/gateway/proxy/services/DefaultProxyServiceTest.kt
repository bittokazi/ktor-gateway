package com.bittokazi.ktor.gateway.proxy.services

import com.bittokazi.ktor.gateway.clients.idp.IdpClient
import com.bittokazi.ktor.gateway.clients.proxy.ProxyClient
import com.bittokazi.ktor.gateway.common.OauthClient
import com.bittokazi.ktor.gateway.common.RouteRule
import com.bittokazi.ktor.gateway.proxy.config.ProxyConfig
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
class DefaultProxyServiceTest {
    @Mock
    lateinit var idpClient: IdpClient

    @Mock
    lateinit var proxyClient: ProxyClient

    @Mock
    lateinit var call: ApplicationCall

    private lateinit var defaultProxyService: DefaultProxyService

    @Test
    fun testDefaultRule() =
        runTest {
            defaultProxyService =
                DefaultProxyService(
                    idpClient = idpClient,
                    proxyClient = proxyClient,
                    proxyConfig =
                        ProxyConfig(
                            enabled = true,
                            routes =
                                mapOf(
                                    "" to
                                        listOf(
                                            RouteRule(
                                                prefix = "/api",
                                                target = "http://localhost:8081",
                                            ),
                                        ),
                                ),
                        ),
                )

            var actual = defaultProxyService.getRule("", "/api/v1", call)
            assertEquals("http://localhost:8081", actual?.target)

            actual = defaultProxyService.getRule("", "/", call)
            assertNull(actual)
        }

    @Test
    fun testRuleFallbackToDefault() =
        runTest {
            defaultProxyService =
                DefaultProxyService(
                    idpClient = idpClient,
                    proxyClient = proxyClient,
                    proxyConfig =
                        ProxyConfig(
                            enabled = true,
                            routes =
                                mapOf(
                                    "example.com" to
                                        listOf(
                                            RouteRule(
                                                prefix = "/api",
                                                target = "http://localhost:8082",
                                            ),
                                        ),
                                    "" to
                                        listOf(
                                            RouteRule(
                                                prefix = "/fallback",
                                                target = "http://localhost:8081",
                                            ),
                                        ),
                                ),
                        ),
                )

            var actual = defaultProxyService.getRule("example.com", "/api/v1", call)
            assertEquals("http://localhost:8082", actual?.target)

            actual = defaultProxyService.getRule("", "/fallback", call)
            assertEquals("http://localhost:8081", actual?.target)

            actual = defaultProxyService.getRule("example.com", "/fallback", call)
            assertEquals("http://localhost:8081", actual?.target)

            actual = defaultProxyService.getRule("example1.com", "/api", call)
            assertNull(actual)
        }

    @Test
    fun testGetRuleUsesLongestMatchingPrefix() =
        runTest {
            defaultProxyService =
                DefaultProxyService(
                    idpClient = idpClient,
                    proxyClient = proxyClient,
                    proxyConfig =
                        ProxyConfig(
                            enabled = true,
                            routes =
                                mapOf(
                                    "" to
                                        listOf(
                                            RouteRule("/api", "http://service1"),
                                            RouteRule("/api/v1", "http://service2"),
                                            RouteRule("/api/v1/users", "http://service3"),
                                        ),
                                ),
                        ),
                )

            val rule = defaultProxyService.getRule("", "/api/v1/users/123", call)
            assertEquals("http://service3", rule?.target)
        }

    @Test
    fun testGetRuleUsesBasePrefix() =
        runTest {
            defaultProxyService =
                DefaultProxyService(
                    idpClient = idpClient,
                    proxyClient = proxyClient,
                    proxyConfig =
                        ProxyConfig(
                            enabled = true,
                            routes =
                                mapOf(
                                    "" to
                                        listOf(
                                            RouteRule("/", "http://service1"),
                                            RouteRule("/api/v1", "http://service2"),
                                            RouteRule("/api/v1/users", "http://service3"),
                                            RouteRule("/api", "http://service4"),
                                            RouteRule("/api/", "http://service5"),
                                        ),
                                ),
                        ),
                )

            var rule = defaultProxyService.getRule("", "/any/route", call)
            assertEquals("http://service1", rule?.target)

            rule = defaultProxyService.getRule("", "/", call)
            assertEquals("http://service1", rule?.target)

            rule = defaultProxyService.getRule("", "/api", call)
            assertEquals("http://service4", rule?.target)

            rule = defaultProxyService.getRule("", "/api-public", call)
            assertEquals("http://service1", rule?.target)

            rule = defaultProxyService.getRule("", "/api/v1/any", call)
            assertEquals("http://service2", rule?.target)

            rule = defaultProxyService.getRule("", "/api/v1", call)
            assertEquals("http://service2", rule?.target)

            rule = defaultProxyService.getRule("", "/api/v1/users", call)
            assertEquals("http://service3", rule?.target)

            rule = defaultProxyService.getRule("", "/api/", call)
            assertEquals("http://service5", rule?.target)
        }

    @Test
    fun testHostSpecificRuleOverridesDefaultRule() =
        runTest {
            defaultProxyService =
                DefaultProxyService(
                    idpClient = idpClient,
                    proxyClient = proxyClient,
                    proxyConfig =
                        ProxyConfig(
                            enabled = true,
                            routes =
                                mapOf(
                                    "example.com" to listOf(RouteRule("/api", "http://host-service")),
                                    "" to listOf(RouteRule("/api", "http://default-service")),
                                ),
                        ),
                )

            val rule = defaultProxyService.getRule("example.com", "/api/users", call)
            assertEquals("http://host-service", rule?.target)
        }

    @Test
    fun testUnknownHostFallsBackToDefaultRule() =
        runTest {
            defaultProxyService =
                DefaultProxyService(
                    idpClient = idpClient,
                    proxyClient = proxyClient,
                    proxyConfig =
                        ProxyConfig(
                            enabled = true,
                            routes = mapOf("" to listOf(RouteRule("/api", "http://default"))),
                        ),
                )

            assertEquals("http://default", defaultProxyService.getRule("unknown.com", "/api/test", call)?.target)
        }

    @Test
    fun testUnknownHostWithoutMatchingDefaultReturnsNull() =
        runTest {
            defaultProxyService =
                DefaultProxyService(
                    idpClient = idpClient,
                    proxyClient = proxyClient,
                    proxyConfig = ProxyConfig(enabled = true, routes = mapOf("" to listOf(RouteRule("/other", "http://service")))),
                )

            assertNull(defaultProxyService.getRule("unknown.com", "/api", call))
        }

    @Test
    fun testGetRuleWithNoRoutesReturnsNull() =
        runTest {
            defaultProxyService =
                DefaultProxyService(
                    idpClient = idpClient,
                    proxyClient = proxyClient,
                    proxyConfig = ProxyConfig(enabled = true),
                )

            assertNull(defaultProxyService.getRule("", "/api", call))
        }

    @Test
    fun testExactPrefixMatch() =
        runTest {
            defaultProxyService =
                DefaultProxyService(
                    idpClient = idpClient,
                    proxyClient = proxyClient,
                    proxyConfig =
                        ProxyConfig(
                            enabled = true,
                            routes = mapOf("" to listOf(RouteRule("/api", "http://service"))),
                        ),
                )

            assertEquals("http://service", defaultProxyService.getRule("", "/api", call)?.target)
        }

    // OAuth tests

    @Test
    fun testGetOauthClientForHost() =
        runTest {
            val client = OauthClient("client", "secret", "", listOf(), "", "", "", "")

            defaultProxyService =
                DefaultProxyService(
                    idpClient = idpClient,
                    proxyClient = proxyClient,
                    proxyConfig = ProxyConfig(enabled = true, oauthClients = mapOf("example.com" to client)),
                )

            assertEquals(client, defaultProxyService.getOauthClient("example.com", call))
        }

    @Test
    fun testGetOauthClientFallsBackToDefault() =
        runTest {
            val client = OauthClient("default", "secret", "", listOf(), "", "", "", "")

            defaultProxyService =
                DefaultProxyService(
                    idpClient = idpClient,
                    proxyClient = proxyClient,
                    proxyConfig = ProxyConfig(enabled = true, oauthClients = mapOf("" to client)),
                )

            assertEquals(client, defaultProxyService.getOauthClient("unknown.com", call))
        }

    @Test
    fun testHostSpecificOauthClientOverridesDefault() =
        runTest {
            val defaultClient = OauthClient("default", "secret", "", listOf(), "", "", "", "")
            val hostClient = OauthClient("host", "secret", "", listOf(), "", "", "", "")

            defaultProxyService =
                DefaultProxyService(
                    idpClient = idpClient,
                    proxyClient = proxyClient,
                    proxyConfig =
                        ProxyConfig(
                            enabled = true,
                            oauthClients = mapOf("" to defaultClient, "example.com" to hostClient),
                        ),
                )

            assertEquals(hostClient, defaultProxyService.getOauthClient("example.com", call))
        }

    @Test
    fun testGetOauthClientReturnsNull() =
        runTest {
            defaultProxyService =
                DefaultProxyService(
                    idpClient = idpClient,
                    proxyClient = proxyClient,
                    proxyConfig = ProxyConfig(enabled = true),
                )

            assertNull(defaultProxyService.getOauthClient("example.com", call))
        }
}
