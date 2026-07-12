package com.bittokazi.ktor.gateway.proxy.routes

import com.bittokazi.ktor.gateway.proxy.services.ProxyService
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun Application.proxyRoutes() {
    val log: Logger = LoggerFactory.getLogger(javaClass)
    val proxyService: ProxyService by dependencies

    routing {
        route("{...}") {
            handle {
                proxyService.handle(call)
            }
        }
    }

    log.info("[INFO] proxyRoutes -> Routes configured ✅")
}
