package com.bittokazi.ktor.gateway.printer.service

interface TableInfoPrinterService {
    fun printTable(
        headers: List<String>,
        rows: List<List<String>>,
    )
}
