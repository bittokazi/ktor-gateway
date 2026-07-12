package com.bittokazi.ktor.gateway.printer.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DefaultTableInfoPrinterService : TableInfoPrinterService {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    init {
        log.info("[INFO] DefaultTableInfoPrinterService is enabled ✅")
    }

    override fun printTable(
        headers: List<String>,
        rows: List<List<String>>,
    ) {
        val columnWidths =
            headers.mapIndexed { index, header ->
                maxOf(header.length, rows.maxOfOrNull { it[index].length } ?: 0) + 2
            }

        val separator = columnWidths.joinToString("+", "+", "+") { "-".repeat(it) }

        fun formatRow(row: List<String>) = row.mapIndexed { i, s -> s.padEnd(columnWidths[i]) }.joinToString("|", "|", "|")

        println(separator)
        println(formatRow(headers))
        println(separator)
        rows.forEach { println(formatRow(it)) }
        println(separator)
    }
}
