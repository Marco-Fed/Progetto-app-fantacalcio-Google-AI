package com.example

import com.example.data.model.Role
import com.example.data.repository.CsvImportResult
import com.example.data.repository.CsvImporter
import com.example.data.repository.ListoneImportResult
import com.example.data.repository.ListoneImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class ListoneImporterTest {

    @Test
    fun testParseCsvWithSemicolon() {
        val csv = """
            R;Nome;Squadra;Qt.A;FVM
            A;Lautaro Martinez;Inter;38;367
            A;Gudmundsson A.;Fiorentina;25;180
            C;Pellegrini Lo.;Roma;20;150
            P;Martinez Jo.;Inter;12;40
        """.trimIndent()

        val result = ListoneImporter.parseStream(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        assertTrue(result is ListoneImportResult.Success)
        val success = result as ListoneImportResult.Success
        assertEquals(4, success.players.size)

        val lautaro = success.players.find { it.name.contains("Lautaro", ignoreCase = true) || it.name.contains("Martinez", ignoreCase = true) && it.role == Role.A }
        assertNotNull(lautaro)
        assertEquals(Role.A, lautaro?.role)
        assertEquals("Inter", lautaro?.team)

        // Verify stats are automatically enriched
        assertTrue("Lautaro should have 2025/26 stats", lautaro?.stats2025_26Json?.isNotBlank() == true)
    }

    @Test
    fun testParseTabDelimitedExcelPaste() {
        val excelPaste = "R\tNome\tSquadra\tQt.A\tFVM\n" +
                "A\tThuram Marcus\tInter\t33\t310\n" +
                "C\tPaz Nico\tComo\t22\t247"

        val result = ListoneImporter.parseText(excelPaste)
        assertTrue(result is ListoneImportResult.Success)
        val success = result as ListoneImportResult.Success
        assertEquals(2, success.players.size)
        assertEquals("Thuram Marcus", success.players[0].name)
    }

    @Test
    fun testCsvImporterDelegation() {
        val csv = "R,Nome,Squadra,Qt,FVM\nA,Vlahovic,Juventus,35,320\n"
        val result = CsvImporter.parseCsv(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        assertTrue(result is CsvImportResult.Success)
        val success = result as CsvImportResult.Success
        assertEquals(1, success.players.size)
        assertEquals("Vlahovic", success.players[0].name)
    }
}
