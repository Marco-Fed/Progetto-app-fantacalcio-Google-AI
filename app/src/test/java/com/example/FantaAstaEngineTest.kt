package com.example

import com.example.data.model.*
import com.example.data.repository.CsvImportResult
import com.example.data.repository.CsvImporter
import com.example.engine.AlternativeEngine
import com.example.engine.DecisionEngine
import com.example.engine.MonteCarloSimulation
import com.example.engine.QuantitativeEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FantaAstaEngineTest {

    private val defaultConfig = LeagueConfig(
        numTeams = 8,
        initialCredits = 500,
        slotsP = 3,
        slotsD = 8,
        slotsC = 8,
        slotsA = 6
    )

    private val userTeam = TeamEntity(
        name = "Utente",
        isUserTeam = true,
        initialCredits = 500,
        remainingCredits = 500,
        purchasedCountP = 0,
        purchasedCountD = 0,
        purchasedCountC = 0,
        purchasedCountA = 0
    )

    private val opponentTeams = listOf(
        TeamEntity("Opponent 1", false, 500, 500),
        TeamEntity("Opponent 2", false, 500, 500),
        TeamEntity("Opponent 3", false, 500, 500),
        TeamEntity("Opponent 4", false, 500, 500)
    )

    private val allTeams = listOf(userTeam) + opponentTeams

    // 1. Role Auction Phase Tests
    @Test
    fun testRoleAuctionPhase_EarlyPhase() {
        val availableAttackers = PreloadedPlayersData.defaultPlayers.filter { it.role == Role.A }
        val phase = QuantitativeEngine.determineRoleAuctionPhase(
            role = Role.A,
            availablePlayersInRole = availableAttackers,
            allTeams = allTeams,
            config = defaultConfig
        )
        assertEquals(RoleAuctionPhase.EARLY, phase)
    }

    @Test
    fun testRoleAuctionPhase_LatePhaseWhenSlotsFilled() {
        val fullTeams = allTeams.map {
            it.copy(purchasedCountA = 5) // 5/6 filled
        }
        val availableAttackers = PreloadedPlayersData.defaultPlayers.filter { it.role == Role.A }.take(2)
        val phase = QuantitativeEngine.determineRoleAuctionPhase(
            role = Role.A,
            availablePlayersInRole = availableAttackers,
            allTeams = fullTeams,
            config = defaultConfig
        )
        assertEquals(RoleAuctionPhase.LATE, phase)
    }

    // 2. Pricing & Theoretical Value Tests
    @Test
    fun testTheoreticalValue_LautaroHigherThanOthers() {
        val lautaro = PreloadedPlayersData.defaultPlayers.first { it.id == "a_martinez_l" }
        val lucca = PreloadedPlayersData.defaultPlayers.first { it.id == "a_lucca" }

        val lautaroVal = QuantitativeEngine.calculateTheoreticalValue(lautaro, defaultConfig)
        val luccaVal = QuantitativeEngine.calculateTheoreticalValue(lucca, defaultConfig)

        assertTrue("Lautaro ($lautaroVal) should have higher theoretical score than Lucca ($luccaVal)", lautaroVal > luccaVal)
    }

    @Test
    fun testMaxAffordableBid_Preserves1CreditPerEmptySlot() {
        val partialTeam = userTeam.copy(
            remainingCredits = 100,
            purchasedCountP = 3,
            purchasedCountD = 8,
            purchasedCountC = 8,
            purchasedCountA = 3 // 3 attackers remaining
        )
        // Total slots = 25. Purchased = 22. Remaining = 3.
        // Required for other 2 slots = 2 credits.
        // Max bid = 100 - 2 = 98 credits.
        val maxBid = partialTeam.maxAffordableBid(defaultConfig)
        assertEquals(98, maxBid)
        assertEquals(3, partialTeam.minimumCompletionBudget(defaultConfig))
    }

    // 3. Quantitative Evaluation & Decision Consistency
    @Test
    fun testQuantitativeEvaluation_GeneratesValidDecisionAndReasons() {
        val lautaro = PreloadedPlayersData.defaultPlayers.first { it.id == "a_martinez_l" }
        val evaluation = QuantitativeEngine.evaluatePlayer(
            player = lautaro,
            userTeam = userTeam,
            allTeams = allTeams,
            availablePlayers = PreloadedPlayersData.defaultPlayers,
            events = emptyList(),
            config = defaultConfig
        )

        assertNotNull(evaluation.decision)
        assertTrue("Optimal price min should be positive", evaluation.optimalPriceMin > 0)
        assertTrue("Max bid should be >= optimal price min", evaluation.maximumBid >= evaluation.optimalPriceMin)
        assertTrue("Should have buy reasons", evaluation.reasons.buyReasons.isNotEmpty())
        assertTrue("Should have caution reasons", evaluation.reasons.cautionReasons.isNotEmpty())
        assertTrue("Should have valid alternatives", evaluation.alternatives.isNotEmpty())
        assertTrue("Alternatives should not contain target player", evaluation.alternatives.none { it.player.id == lautaro.id })
    }

    @Test
    fun testDecision_BlocksWhenRoleSlotsFull() {
        val fullAttackerTeam = userTeam.copy(
            purchasedCountA = 6 // 6/6 filled
        )
        val lautaro = PreloadedPlayersData.defaultPlayers.first { it.id == "a_martinez_l" }
        val evaluation = QuantitativeEngine.evaluatePlayer(
            player = lautaro,
            userTeam = fullAttackerTeam,
            allTeams = listOf(fullAttackerTeam) + opponentTeams,
            availablePlayers = PreloadedPlayersData.defaultPlayers,
            events = emptyList(),
            config = defaultConfig
        )

        assertEquals(DecisionType.DO_NOT_BID, evaluation.decision)
        assertTrue(evaluation.reasons.cautionReasons.any { it.contains("Slot completati") })
    }

    // 4. Alternative Engine Tests
    @Test
    fun testAlternativeEngine_ReturnsRealisticAlternativesOnly() {
        val dimarco = PreloadedPlayersData.defaultPlayers.first { it.id == "d_dimarco" }
        val defenders = PreloadedPlayersData.defaultPlayers.filter { it.role == Role.D }

        val alts = AlternativeEngine.findAlternatives(
            targetPlayer = dimarco,
            availableInRole = defenders,
            userTeam = userTeam,
            config = defaultConfig
        )

        assertTrue(alts.isNotEmpty())
        assertTrue(alts.all { it.player.role == Role.D })
        assertTrue(alts.none { it.player.id == dimarco.id })
        assertTrue(alts.all { it.starterProb >= 50 })
    }

    // 5. Monte Carlo Simulation Tests (< 5s performance constraint)
    @Test(timeout = 5000)
    fun testMonteCarloSimulation_ExecutesUnderConstraint() {
        val calhanoglu = PreloadedPlayersData.defaultPlayers.first { it.id == "c_calhanoglu" }
        val result = MonteCarloSimulation.runSimulation(
            player = calhanoglu,
            bidPrice = 45,
            userTeam = userTeam,
            allTeams = allTeams,
            availablePlayers = PreloadedPlayersData.defaultPlayers,
            config = defaultConfig,
            isDeepMode = false
        )

        assertTrue("Execution time must be < 2000ms", result.executionTimeMs < 2000)
        assertTrue("Win rate must be between 0.0 and 1.0", result.winRateWithPlayer in 0.0..1.0)
        assertTrue("Average points must be positive", result.avgFinalRosterPointsWithPlayer > 0)
    }

    // 6. CSV Importer Tests (Robustness)
    @Test
    fun testCsvImporter_ParsesSemicolonCsvWithQuotesAndBOM() {
        val bomCsv = "\uFEFFRuolo;Nome;Squadra;Qt;FVM;Titolarità\n" +
                "\"A\";\"Lautaro Martinez\";\"Inter\";40;110;\"96%\"\n" +
                "\"C\";\"Hakan Calhanoglu\";\"Inter\";26;52;\"90%\"\n" +
                "\"D\";\"Federico Dimarco\";\"Inter\";24;45;\"88%\"\n" +
                "\"P\";\"Yann Sommer\";\"Inter\";17;38;\"90%\"\n"

        val inputStream = ByteArrayInputStream(bomCsv.toByteArray(StandardCharsets.UTF_8))
        val result = CsvImporter.parseCsv(inputStream)

        assertTrue(result is CsvImportResult.Success)
        val success = result as CsvImportResult.Success
        assertEquals(4, success.players.size)
        assertEquals("Lautaro Martinez", success.players[0].name)
        assertEquals(Role.A, success.players[0].role)
        assertEquals(96, success.players[0].starterProb2026_27)
    }

    @Test
    fun testCsvImporter_HandlesEmptyOrInvalidCsvGracefully() {
        val emptyStream = ByteArrayInputStream("".toByteArray(StandardCharsets.UTF_8))
        val result = CsvImporter.parseCsv(emptyStream)
        assertTrue(result is CsvImportResult.Error)
    }
}
