package com.bittokazi.ktor.gateway.proxy

import com.bittokazi.ktor.gateway.proxy.config.ProxyConfig
import com.bittokazi.ktor.gateway.proxy.routes.proxyRoutes
import com.bittokazi.ktor.gateway.proxy.services.DefaultProxyService
import com.bittokazi.ktor.gateway.proxy.services.NoopProxyService
import com.bittokazi.ktor.gateway.proxy.services.ProxyService
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

fun Application.proxyModule() {
    val proxyConfig: ProxyConfig by dependencies

    dependencies {
        when (proxyConfig.enabled) {
            true ->
                provide<ProxyService>(
                    DefaultProxyService::class,
                )
            else ->
                provide<ProxyService>(
                    NoopProxyService::class,
                )
        }
    }

    proxyRoutes()
}
