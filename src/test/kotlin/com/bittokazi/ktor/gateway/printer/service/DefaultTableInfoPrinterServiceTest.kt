package com.bittokazi.ktor.gateway.printer.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertContains
import kotlin.test.assertEquals

class DefaultTableInfoPrinterServiceTest {
    private lateinit var printerService: DefaultTableInfoPrinterService
    private lateinit var outputStream: ByteArrayOutputStream
    private lateinit var originalOut: PrintStream

    @BeforeEach
    fun setUp() {
        printerService = DefaultTableInfoPrinterService()
        outputStream = ByteArrayOutputStream()
        originalOut = System.out
        System.setOut(PrintStream(outputStream))
    }

    private fun getOutput(): String = outputStream.toString().trim()

    private fun tearDown() {
        System.setOut(originalOut)
    }

    @Test
    fun testPrintTableWithSimpleData() {
        val headers = listOf("Name", "Age")
        val rows =
            listOf(
                listOf("Alice", "30"),
                listOf("Bob", "25"),
            )

        printerService.printTable(headers, rows)
        val output = getOutput()

        // Verify output contains headers
        assertContains(output, "Name")
        assertContains(output, "Age")

        // Verify output contains data
        assertContains(output, "Alice")
        assertContains(output, "Bob")
        assertContains(output, "30")
        assertContains(output, "25")

        // Verify output has separators
        assertContains(output, "+")
        assertContains(output, "-")
        assertContains(output, "|")

        tearDown()
    }

    @Test
    fun testPrintTableWithSingleRow() {
        val headers = listOf("Product")
        val rows = listOf(listOf("Laptop"))

        printerService.printTable(headers, rows)
        val output = getOutput()

        assertContains(output, "Product")
        assertContains(output, "Laptop")
        assertContains(output, "+")
        assertContains(output, "-")
        assertContains(output, "|")

        tearDown()
    }

    @Test
    fun testPrintTableWithEmptyRows() {
        val headers = listOf("Column1", "Column2", "Column3")
        val rows = emptyList<List<String>>()

        printerService.printTable(headers, rows)
        val output = getOutput()

        // Should still print headers and separators
        assertContains(output, "Column1")
        assertContains(output, "Column2")
        assertContains(output, "Column3")
        assertContains(output, "+")
        assertContains(output, "-")
        assertContains(output, "|")

        tearDown()
    }

    @Test
    fun testPrintTableWithMultipleColumnsAndRows() {
        val headers = listOf("ID", "Name", "Department", "Salary")
        val rows =
            listOf(
                listOf("1", "John", "Engineering", "100000"),
                listOf("2", "Jane", "HR", "80000"),
                listOf("3", "Jack", "Sales", "75000"),
            )

        printerService.printTable(headers, rows)
        val output = getOutput()

        // Verify all headers
        assertContains(output, "ID")
        assertContains(output, "Name")
        assertContains(output, "Department")
        assertContains(output, "Salary")

        // Verify all data
        assertContains(output, "John")
        assertContains(output, "Jane")
        assertContains(output, "Jack")
        assertContains(output, "Engineering")
        assertContains(output, "100000")

        tearDown()
    }

    @Test
    fun testPrintTableWithVaryingColumnWidths() {
        val headers = listOf("A", "LongHeader")
        val rows =
            listOf(
                listOf("Short", "X"),
                listOf("VeryLongValue", "AnotherLongValue"),
            )

        printerService.printTable(headers, rows)
        val output = getOutput()

        assertContains(output, "A")
        assertContains(output, "LongHeader")
        assertContains(output, "Short")
        assertContains(output, "VeryLongValue")
        assertContains(output, "AnotherLongValue")

        tearDown()
    }

    @Test
    fun testPrintTableWithHeaderLongerThanContent() {
        val headers = listOf("VeryLongHeaderName", "Y")
        val rows = listOf(listOf("A", "B"))

        printerService.printTable(headers, rows)
        val output = getOutput()

        assertContains(output, "VeryLongHeaderName")
        assertContains(output, "A")
        assertContains(output, "B")

        tearDown()
    }

    @Test
    fun testPrintTableWithContentLongerThanHeader() {
        val headers = listOf("Name", "Value")
        val rows = listOf(listOf("ShortHeader", "VeryLongContentValueHere"))

        printerService.printTable(headers, rows)
        val output = getOutput()

        assertContains(output, "Name")
        assertContains(output, "Value")
        assertContains(output, "ShortHeader")
        assertContains(output, "VeryLongContentValueHere")

        tearDown()
    }

    @Test
    fun testPrintTableWithSpecialCharacters() {
        val headers = listOf("Symbol", "Description")
        val rows =
            listOf(
                listOf("@", "At Sign"),
                listOf("#", "Hash"),
                listOf("$", "Dollar"),
            )

        printerService.printTable(headers, rows)
        val output = getOutput()

        assertContains(output, "Symbol")
        assertContains(output, "Description")
        assertContains(output, "@")
        assertContains(output, "#")
        assertContains(output, "$")

        tearDown()
    }

    @Test
    fun testPrintTableWithNumbers() {
        val headers = listOf("Quantity", "Price", "Total")
        val rows =
            listOf(
                listOf("10", "99.99", "999.90"),
                listOf("5", "49.99", "249.95"),
            )

        printerService.printTable(headers, rows)
        val output = getOutput()

        assertContains(output, "Quantity")
        assertContains(output, "Price")
        assertContains(output, "Total")
        assertContains(output, "999.90")
        assertContains(output, "249.95")

        tearDown()
    }

    @Test
    fun testPrintTableFormatWithSeparators() {
        val headers = listOf("A", "B")
        val rows = listOf(listOf("1", "2"))

        printerService.printTable(headers, rows)
        val output = getOutput()

        // Split output into lines
        val lines = output.split("\n")

        // Should have at least 5 lines: separator, header, separator, row, separator
        assertEquals(true, lines.size >= 5)

        // First and last lines should be separators
        assertEquals(true, lines[0].startsWith("+"))
        assertEquals(true, lines[lines.size - 1].startsWith("+"))

        // Second line should be header
        assertEquals(true, lines[1].startsWith("|"))
        assertContains(lines[1], "A")
        assertContains(lines[1], "B")

        tearDown()
    }

    @Test
    fun testPrintTablePaddingCorrectness() {
        val headers = listOf("X", "LongName")
        val rows = listOf(listOf("Y", "Z"))

        printerService.printTable(headers, rows)
        val output = getOutput()

        // Verify that all separator lines have the same format (same number of dashes and plus signs)
        val lines = output.split("\n")
        val separatorLines = lines.filter { it.startsWith("+") }
        val dataLines = lines.filter { it.startsWith("|") }

        // All separator lines should have the same length
        val separatorLengths = separatorLines.map { it.length }
        if (separatorLengths.isNotEmpty()) {
            val expectedSeparatorLength = separatorLengths[0]
            for (length in separatorLengths) {
                assertEquals(
                    expectedSeparatorLength,
                    length,
                    "All separator lines should have consistent width",
                )
            }
        }

        // All data lines should have the same length
        val dataLengths = dataLines.map { it.length }
        if (dataLengths.size > 1) {
            val expectedDataLength = dataLengths[0]
            for (length in dataLengths) {
                assertEquals(
                    expectedDataLength,
                    length,
                    "All data lines should have consistent width",
                )
            }
        }

        // Separator lines and data lines should have the same length (for proper alignment)
        if (separatorLengths.isNotEmpty() && dataLengths.isNotEmpty()) {
            assertEquals(
                separatorLengths[0],
                dataLengths[0],
                "Separator and data lines should have the same width",
            )
        }

        tearDown()
    }

    @Test
    fun testPrintTableWithSingleColumn() {
        val headers = listOf("OnlyColumn")
        val rows =
            listOf(
                listOf("Value1"),
                listOf("Value2"),
                listOf("Value3"),
            )

        printerService.printTable(headers, rows)
        val output = getOutput()

        assertContains(output, "OnlyColumn")
        assertContains(output, "Value1")
        assertContains(output, "Value2")
        assertContains(output, "Value3")

        tearDown()
    }

    @Test
    fun testPrintTableWithManyColumns() {
        val headers = listOf("C1", "C2", "C3", "C4", "C5")
        val rows = listOf(listOf("a", "b", "c", "d", "e"))

        printerService.printTable(headers, rows)
        val output = getOutput()

        assertContains(output, "C1")
        assertContains(output, "C2")
        assertContains(output, "C3")
        assertContains(output, "C4")
        assertContains(output, "C5")
        assertContains(output, "a")
        assertContains(output, "b")
        assertContains(output, "c")
        assertContains(output, "d")
        assertContains(output, "e")

        tearDown()
    }

    @Test
    fun testPrintTableWithWhitespace() {
        val headers = listOf("Name", "Description")
        val rows =
            listOf(
                listOf("Item 1", "Has spaces"),
                listOf("Item 2", "Also has spaces"),
            )

        printerService.printTable(headers, rows)
        val output = getOutput()

        assertContains(output, "Item 1")
        assertContains(output, "Item 2")
        assertContains(output, "Has spaces")
        assertContains(output, "Also has spaces")

        tearDown()
    }

    @Test
    fun testPrintTableWithNumericStrings() {
        val headers = listOf("ID", "Count", "Amount")
        val rows =
            listOf(
                listOf("001", "100", "1000.00"),
                listOf("002", "50", "500.00"),
            )

        printerService.printTable(headers, rows)
        val output = getOutput()

        assertContains(output, "001")
        assertContains(output, "002")
        assertContains(output, "100")
        assertContains(output, "1000.00")

        tearDown()
    }

    @Test
    fun testPrintTableStructure() {
        val headers = listOf("First", "Second")
        val rows = listOf(listOf("1", "2"), listOf("3", "4"))

        printerService.printTable(headers, rows)
        val output = getOutput()

        val lines = output.split("\n").filter { it.isNotEmpty() }

        // Structure: +---+---+
        //           | First |Second |
        //           +---+---+
        //           | 1 | 2 |
        //           +---+---+
        //           | 3 | 4 |
        //           +---+---+

        // Verify line pattern: separator, header, separator, row1, row2/separator
        assertEquals(true, lines[0].startsWith("+"))
        assertEquals(true, lines[1].startsWith("|"))
        assertEquals(true, lines[2].startsWith("+"))

        tearDown()
    }

    @Test
    fun testPrintTableWithLargeDataset() {
        val headers = listOf("ID", "Name", "Status")
        val rows =
            (1..100).map { i ->
                listOf(i.toString(), "User$i", if (i % 2 == 0) "Active" else "Inactive")
            }

        printerService.printTable(headers, rows)
        val output = getOutput()

        // Verify some samples from the large dataset
        assertContains(output, "ID")
        assertContains(output, "Name")
        assertContains(output, "Status")
        assertContains(output, "User1")
        assertContains(output, "User50")
        assertContains(output, "User100")
        assertContains(output, "Active")
        assertContains(output, "Inactive")

        tearDown()
    }
}
