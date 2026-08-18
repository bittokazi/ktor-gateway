package com.bittokazi.ktor.gateway

import com.bittokazi.ktor.gateway.clients.configureClientModule
import com.bittokazi.ktor.gateway.common.GatewayConfig
import com.bittokazi.ktor.gateway.printer.printerModule
import com.bittokazi.ktor.gateway.proxy.proxyModule
import io.ktor.server.application.Application

fun Application.configureFrameworks(gatewayConfig: GatewayConfig) {
    configureClientModule(gatewayConfig)
    proxyModule()
    printerModule()
}
