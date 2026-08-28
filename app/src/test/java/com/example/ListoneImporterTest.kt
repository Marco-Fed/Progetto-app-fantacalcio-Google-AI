package com.example

import com.example.data.model.Role
import com.example.data.remote.KaggleHistoricalStatsService
import com.example.data.repository.CsvImportResult
import com.example.data.repository.CsvImporter
import com.example.data.repository.ListoneImportResult
import com.example.data.repository.ListoneImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ListoneImporterTest {

    @org.junit.Before
    fun setup() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        KaggleHistoricalStatsService.ensureInitialized(context)
    }

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

        val lautaro = success.players.find { it.role == Role.A && (it.name.contains("Lautaro", ignoreCase = true) || it.name.contains("Martinez", ignoreCase = true)) }
        assertNotNull(lautaro)
        assertEquals(Role.A, lautaro?.role)
        assertEquals("Inter", lautaro?.team)
        assertEquals(38, lautaro?.quotation)
        assertEquals(367, lautaro?.fvm)
        assertTrue("Expected fantasy points should be positive", (lautaro?.expectedFantasyPoints ?: 0.0) >= 6.0)
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
    fun testExpectedFantasyPointsInPlausibleRange() {
        val testCsv = """
            R;Nome;Squadra;Qt.A;FVM;FM
            P;Sommer;Inter;15;50;5.8
            D;Dimarco;Inter;22;120;6.8
            C;Calhanoglu;Inter;28;210;7.4
            A;Lautaro Martinez;Inter;38;367;8.6
            A;Bench Striker;Empoli;1;1;5.5
        """.trimIndent()

        val result = ListoneImporter.parseStream(ByteArrayInputStream(testCsv.toByteArray(StandardCharsets.UTF_8)))
        assertTrue(result is ListoneImportResult.Success)
        val players = (result as ListoneImportResult.Success).players

        for (player in players) {
            assertTrue(
                "Player ${player.name} expected points (${player.expectedFantasyPoints}) must be within [4.5, 9.0]",
                player.expectedFantasyPoints in 4.5..9.0
            )
        }
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
