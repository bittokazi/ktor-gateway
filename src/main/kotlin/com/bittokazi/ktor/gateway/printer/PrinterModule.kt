package com.bittokazi.ktor.gateway.printer

import com.bittokazi.ktor.gateway.printer.service.DefaultTableInfoPrinterService
import com.bittokazi.ktor.gateway.printer.service.TableInfoPrinterService
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

fun Application.printerModule() {
    dependencies {
        provide<TableInfoPrinterService>(
            DefaultTableInfoPrinterService::class,
        )
    }
}
