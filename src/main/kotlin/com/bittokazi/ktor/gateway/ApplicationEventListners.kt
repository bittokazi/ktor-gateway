package com.bittokazi.ktor.gateway

import com.bittokazi.ktor.gateway.printer.service.TableInfoPrinterService
import com.bittokazi.ktor.gateway.proxy.config.ProxyConfig
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.plugins.di.dependencies

fun Application.applicationEventListeners() {
    val proxyConfig: ProxyConfig by dependencies
    val tableInfoPrinterService: TableInfoPrinterService by dependencies

    monitor.subscribe(ApplicationStarted) { application ->

        application.environment.log.info(
            "[INFO] Configured Oauth Clients [\uD83D\uDD10]",
        )

        tableInfoPrinterService.printTable(
            listOf("Domain", "Client Id", "Scopes", "Authorization URL", "Introspect URL", "Token URL", "Logout URL"),
            proxyConfig.oauthClients.map { client ->
                listOf(
                    client.key,
                    client.value.clientId,
                    client.value.scopes.joinToString(","),
                    client.value.authorizeUrl,
                    client.value.introspectUrl,
                    client.value.tokenUrl,
                    client.value.logoutUrl,
                )
            },
        )

        application.environment.log.info(
            "[INFO] Configured Routes [\uD83D\uDD00]",
        )

        tableInfoPrinterService.printTable(
            listOf("Domain", "Auth Type", "Path", "Target"),
            proxyConfig.routes.flatMap { route ->
                route.value.map {
                    listOf(
                        route.key,
                        it.authType.toString(),
                        it.prefix,
                        it.target,
                    )
                }
            },
        )

        application.environment.log.info(
            "[INFO] Application started successfully [\uD83D\uDE80]",
        )
    }
}
