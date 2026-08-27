package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.remote.FantacalcioWebService
import com.example.data.remote.IndisponibileInfo
import com.example.data.remote.InjuryParserService
import com.example.data.remote.KaggleHistoricalStatsService
import com.example.engine.MonteCarloSimulation
import com.example.engine.QuantitativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

sealed class AssignmentResult {
    data object Success : AssignmentResult()
    data class AlreadyAssigned(val playerName: String, val teamName: String) : AssignmentResult()
    data class InsufficientCredits(val remainingCredits: Int, val maxBid: Int, val bidPrice: Int) : AssignmentResult()
    data class RoleSlotsFull(val role: Role, val teamName: String) : AssignmentResult()
    data class Error(val message: String) : AssignmentResult()
}

class AuctionRepository(private val database: AppDatabase) {

    val allPlayers: Flow<List<PlayerEntity>> = database.playerDao().getAllPlayersFlow()
    val allTeams: Flow<List<TeamEntity>> = database.teamDao().getAllTeamsFlow()
    val userTeam: Flow<TeamEntity?> = database.teamDao().getUserTeamFlow()
    val auctionEvents: Flow<List<AuctionEventEntity>> = database.auctionEventDao().getAllEventsFlow()
    val leagueConfig: Flow<LeagueConfig?> = database.leagueDao().getLeagueConfigFlow()
    val watchlist: Flow<List<WatchlistEntity>> = database.watchlistDao().getAllWatchlistFlow()

    suspend fun initializeDefaultDataIfEmpty() = withContext(Dispatchers.IO) {
        val currentConfig = database.leagueDao().getLeagueConfig()
        if (currentConfig == null) {
            val defaultConfig = LeagueConfig()
            database.leagueDao().saveLeagueConfig(defaultConfig)
        }

        val existingPlayers = database.playerDao().getAllPlayers()
        if (existingPlayers.isEmpty() || (existingPlayers.size < 200 && existingPlayers.none { it.isPurchased })) {
            database.playerDao().clearAll()
            database.playerDao().insertPlayers(PreloadedPlayersData.defaultPlayers)
        }

        val existingTeams = database.teamDao().getAllTeams()
        if (existingTeams.isEmpty()) {
            val defaultTeams = listOf(
                TeamEntity(name = "La Mia Rosa (Utente)", isUserTeam = true, initialCredits = 500, remainingCredits = 500),
                TeamEntity(name = "FC Spartak", isUserTeam = false, initialCredits = 500, remainingCredits = 500),
                TeamEntity(name = "Real Madrink", isUserTeam = false, initialCredits = 500, remainingCredits = 500),
                TeamEntity(name = "Dinamo Loser", isUserTeam = false, initialCredits = 500, remainingCredits = 500),
                TeamEntity(name = "Atletico MaNonTroppo", isUserTeam = false, initialCredits = 500, remainingCredits = 500),
                TeamEntity(name = "Borussia Porkmund", isUserTeam = false, initialCredits = 500, remainingCredits = 500),
                TeamEntity(name = "Celta Vino", isUserTeam = false, initialCredits = 500, remainingCredits = 500),
                TeamEntity(name = "Paris Saint Gennar", isUserTeam = false, initialCredits = 500, remainingCredits = 500)
            )
            database.teamDao().insertTeams(defaultTeams)
        }
    }

    suspend fun assignPlayer(
        playerId: String,
        teamName: String,
        price: Int
    ): AssignmentResult = withContext(Dispatchers.IO) {
        val player = database.playerDao().getPlayerById(playerId)
            ?: return@withContext AssignmentResult.Error("Giocatore non trovato")
        val team = database.teamDao().getTeamByName(teamName)
            ?: return@withContext AssignmentResult.Error("Squadra '$teamName' non trovata")
        val config = database.leagueDao().getLeagueConfig() ?: LeagueConfig()

        // 1. Check if already purchased
        if (player.isPurchased) {
            val buyer = player.purchasedByTeam ?: "altra squadra"
            return@withContext AssignmentResult.AlreadyAssigned(
                playerName = player.name,
                teamName = buyer
            )
        }

        // 2. Check team credits & slots
        val maxAffordable = team.maxAffordableBid(config)
        if (price > team.remainingCredits || price > maxAffordable) {
            return@withContext AssignmentResult.InsufficientCredits(
                remainingCredits = team.remainingCredits,
                maxBid = maxAffordable,
                bidPrice = price
            )
        }

        if (team.remainingSlotsForRole(player.role, config) <= 0) {
            return@withContext AssignmentResult.RoleSlotsFull(
                role = player.role,
                teamName = teamName
            )
        }

        // Update player
        database.playerDao().updatePurchaseStatus(
            playerId = playerId,
            isPurchased = true,
            teamName = teamName,
            price = price
        )

        // Update team stats
        val updatedTeam = when (player.role) {
            Role.P -> team.copy(
                remainingCredits = team.remainingCredits - price,
                purchasedCountP = team.purchasedCountP + 1,
                spentCreditsP = team.spentCreditsP + price
            )
            Role.D -> team.copy(
                remainingCredits = team.remainingCredits - price,
                purchasedCountD = team.purchasedCountD + 1,
                spentCreditsD = team.spentCreditsD + price
            )
            Role.C -> team.copy(
                remainingCredits = team.remainingCredits - price,
                purchasedCountC = team.purchasedCountC + 1,
                spentCreditsC = team.spentCreditsC + price
            )
            Role.A -> team.copy(
                remainingCredits = team.remainingCredits - price,
                purchasedCountA = team.purchasedCountA + 1,
                spentCreditsA = team.spentCreditsA + price
            )
        }
        database.teamDao().updateTeam(updatedTeam)

        // Log auction event
        database.auctionEventDao().insertEvent(
            AuctionEventEntity(
                playerId = playerId,
                playerName = player.name,
                playerRole = player.role,
                playerTeam = player.team,
                buyerTeamName = teamName,
                price = price,
                quotationAtTime = player.quotation
            )
        )
        return@withContext AssignmentResult.Success
    }

    suspend fun undoLastPurchase(): Boolean = withContext(Dispatchers.IO) {
        val lastEvent = database.auctionEventDao().getLastEvent() ?: return@withContext false
        val player = database.playerDao().getPlayerById(lastEvent.playerId) ?: return@withContext false
        val team = database.teamDao().getTeamByName(lastEvent.buyerTeamName) ?: return@withContext false

        // Reset player status
        database.playerDao().updatePurchaseStatus(player.id, false, null, null)

        // Refund team
        val updatedTeam = when (player.role) {
            Role.P -> team.copy(
                remainingCredits = team.remainingCredits + lastEvent.price,
                purchasedCountP = (team.purchasedCountP - 1).coerceAtLeast(0),
                spentCreditsP = (team.spentCreditsP - lastEvent.price).coerceAtLeast(0)
            )
            Role.D -> team.copy(
                remainingCredits = team.remainingCredits + lastEvent.price,
                purchasedCountD = (team.purchasedCountD - 1).coerceAtLeast(0),
                spentCreditsD = (team.spentCreditsD - lastEvent.price).coerceAtLeast(0)
            )
            Role.C -> team.copy(
                remainingCredits = team.remainingCredits + lastEvent.price,
                purchasedCountC = (team.purchasedCountC - 1).coerceAtLeast(0),
                spentCreditsC = (team.spentCreditsC - lastEvent.price).coerceAtLeast(0)
            )
            Role.A -> team.copy(
                remainingCredits = team.remainingCredits + lastEvent.price,
                purchasedCountA = (team.purchasedCountA - 1).coerceAtLeast(0),
                spentCreditsA = (team.spentCreditsA - lastEvent.price).coerceAtLeast(0)
            )
        }
        database.teamDao().updateTeam(updatedTeam)

        // Delete event
        database.auctionEventDao().deleteLastEvent()
        return@withContext true
    }

    suspend fun resetAuction() = withContext(Dispatchers.IO) {
        database.playerDao().resetAllPurchases()
        database.auctionEventDao().clearAll()
        val teams = database.teamDao().getAllTeams()
        val resetTeams = teams.map {
            it.copy(
                remainingCredits = it.initialCredits,
                purchasedCountP = 0,
                purchasedCountD = 0,
                purchasedCountC = 0,
                purchasedCountA = 0,
                spentCreditsP = 0,
                spentCreditsD = 0,
                spentCreditsC = 0,
                spentCreditsA = 0
            )
        }
        database.teamDao().insertTeams(resetTeams)
    }

    suspend fun updateTeamName(oldName: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val trimmedNew = newName.trim()
        if (trimmedNew.isBlank() || trimmedNew == oldName) return@withContext false

        val existingTeam = database.teamDao().getTeamByName(oldName) ?: return@withContext false
        val checkNameCollision = database.teamDao().getTeamByName(trimmedNew)
        if (checkNameCollision != null && trimmedNew != oldName) {
            return@withContext false // Name already taken
        }

        // Delete old entry and create updated entry with new name
        database.teamDao().deleteTeamByName(oldName)
        val updatedTeam = existingTeam.copy(name = trimmedNew)
        database.teamDao().insertTeam(updatedTeam)

        // Update players assigned to this team
        database.playerDao().updateTeamNameInPlayers(oldName, trimmedNew)

        // Update historical events
        database.auctionEventDao().updateTeamNameInEvents(oldName, trimmedNew)
        return@withContext true
    }

    suspend fun saveLeagueConfig(config: LeagueConfig) = withContext(Dispatchers.IO) {
        database.leagueDao().saveLeagueConfig(config)

        // Dynamically adjust teams and their credits based on new config
        val currentTeams = database.teamDao().getAllTeams()
        val userTeam = currentTeams.firstOrNull { it.isUserTeam }
            ?: TeamEntity("La Mia Rosa (Utente)", true, config.initialCredits, config.initialCredits)

        val updatedTeams = mutableListOf<TeamEntity>()

        // Update existing teams with new initial credits and recalculate remaining credits
        for (team in currentTeams) {
            val totalSpent = team.spentCreditsP + team.spentCreditsD + team.spentCreditsC + team.spentCreditsA
            val remaining = (config.initialCredits - totalSpent).coerceAtLeast(0)
            updatedTeams.add(
                team.copy(
                    initialCredits = config.initialCredits,
                    remainingCredits = remaining
                )
            )
        }

        // Adjust number of teams if needed
        if (updatedTeams.size < config.numTeams) {
            val needed = config.numTeams - updatedTeams.size
            for (i in 1..needed) {
                val teamNumber = updatedTeams.size + 1
                updatedTeams.add(
                    TeamEntity(
                        name = "Squadra $teamNumber",
                        isUserTeam = false,
                        initialCredits = config.initialCredits,
                        remainingCredits = config.initialCredits
                    )
                )
            }
        } else if (updatedTeams.size > config.numTeams) {
            // Trim excess opponent teams that have no purchases
            val user = updatedTeams.filter { it.isUserTeam }
            val opponents = updatedTeams.filter { !it.isUserTeam }
            val opponentsToKeep = opponents.take(config.numTeams - user.size)
            val toRemove = opponents.drop(config.numTeams - user.size)

            for (rem in toRemove) {
                database.teamDao().deleteTeamByName(rem.name)
            }
            updatedTeams.clear()
            updatedTeams.addAll(user + opponentsToKeep)
        }

        database.teamDao().insertTeams(updatedTeams)
    }

    suspend fun updateTeams(teams: List<TeamEntity>) = withContext(Dispatchers.IO) {
        database.teamDao().insertTeams(teams)
    }

    suspend fun parseAndApplyIndisponibiliFromText(text: String): Int = withContext(Dispatchers.IO) {
        val indisponibiliList = InjuryParserService.parseInjuriesFromText(text)
        val allPlayers = database.playerDao().getAllPlayers()
        var updatedCount = 0

        // Reset current injuries first so that only players reported in the provided text are classified as injured/unavailable
        database.playerDao().resetAllInjuries()

        for (indisponibile in indisponibiliList) {
            val match = allPlayers.firstOrNull { player ->
                isPlayerMatching(
                    playerName = player.name,
                    playerTeam = player.team,
                    indName = indisponibile.playerName,
                    indTeam = indisponibile.team
                )
            }

            if (match != null) {
                val statusType = when {
                    indisponibile.injuryDescription.contains("squalificat", ignoreCase = true) -> "Squalificato"
                    indisponibile.expectedReturnDate.contains("dubbio", ignoreCase = true) || 
                    indisponibile.injuryDescription.contains("dubbio", ignoreCase = true) -> "In dubbio"
                    else -> "Infortunato"
                }
                database.playerDao().updateInjuryStatus(
                    playerId = match.id,
                    status = statusType,
                    notes = indisponibile.injuryDescription,
                    returnDate = indisponibile.expectedReturnDate
                )
                updatedCount++
            }
        }
        return@withContext updatedCount
    }

    suspend fun fetchAndApplyIndisponibili(): Int = withContext(Dispatchers.IO) {
        return@withContext parseAndApplyIndisponibiliFromText(InjuryParserService.DEFAULT_INJURIES_TEXT)
    }

    private fun isPlayerMatching(playerName: String, playerTeam: String, indName: String, indTeam: String): Boolean {
        val pNorm = normalizeName(playerName)
        val iNorm = normalizeName(indName)
        if (pNorm == iNorm || pNorm.contains(iNorm) || iNorm.contains(pNorm)) return true

        val pWords = pNorm.split(" ").filter { it.length >= 3 }
        val iWords = iNorm.split(" ").filter { it.length >= 3 }

        val common = pWords.intersect(iWords.toSet())
        if (common.isNotEmpty()) {
            val teamMatches = playerTeam.isBlank() || indTeam.isBlank() ||
                    playerTeam.equals("Serie A", ignoreCase = true) || indTeam.equals("Serie A", ignoreCase = true) ||
                    playerTeam.equals(indTeam, ignoreCase = true) ||
                    indTeam.contains(playerTeam, ignoreCase = true) || playerTeam.contains(indTeam, ignoreCase = true)
            if (teamMatches) return true
        }

        // Check surname match
        val pSurname = pWords.lastOrNull() ?: ""
        val iSurname = iWords.lastOrNull() ?: ""
        if (pSurname.isNotBlank() && pSurname == iSurname) {
            return true
        }

        return false
    }

    suspend fun fetchAndApplyFullListoneOnline(): Int = withContext(Dispatchers.IO) {
        val rawList = FantacalcioWebService.fetchFullListoneOnline()
        if (rawList.isNotEmpty()) {
            val playerEntities = rawList.map { item ->
                val safeId = "online_${item.playerName.lowercase().replace(Regex("[^a-z0-9]"), "_")}_${UUID.randomUUID().toString().take(4)}"
                val starterProb = (60 + (item.fvm / 8)).coerceIn(20, 95)
                val expPts = when (item.role) {
                    Role.P -> 5.5 + (item.fvm / 40.0)
                    Role.D -> 5.8 + (item.fvm / 35.0)
                    Role.C -> 6.0 + (item.fvm / 30.0)
                    Role.A -> 6.5 + (item.fvm / 25.0)
                }.coerceIn(4.0, 9.5)

                PlayerEntity(
                    id = safeId,
                    name = item.playerName,
                    team = item.team,
                    role = item.role,
                    mantraRole = "",
                    quotation = item.quotation,
                    fvm = item.fvm,
                    starterProb2026_27 = starterProb,
                    expectedFantasyPoints = expPts,
                    expectedMinutes = (starterProb * 0.9).toInt().coerceIn(20, 90),
                    riskLevel = if (starterProb < 65) RiskLevel.ALTO else if (starterProb < 82) RiskLevel.MEDIO else RiskLevel.BASSO,
                    confidenceLevel = if (item.fvm >= 50) ConfidenceLevel.ALTA else if (item.fvm >= 15) ConfidenceLevel.MEDIA else ConfidenceLevel.BASSA,
                    isPenaltyTaker = false,
                    penaltyOrder = 0,
                    isFreeKickTaker = false,
                    isCornerTaker = false,
                    ballottaggioRival = null,
                    ballottaggioShare = 100,
                    stats2023_24Json = "",
                    stats2024_25Json = "",
                    stats2025_26Json = "",
                    status = "Disponibile",
                    injuryNotes = "",
                    expectedReturnDate = ""
                )
            }

            val enrichedList = KaggleHistoricalStatsService.enrichList(playerEntities)
            database.playerDao().clearAll()
            database.playerDao().insertPlayers(enrichedList)
            // Auto-apply current indisponibili on the new list
            fetchAndApplyIndisponibili()
            return@withContext enrichedList.size
        }
        return@withContext 0
    }

    suspend fun fetchAndApplyQuotazioniOnline(): Int = withContext(Dispatchers.IO) {
        return@withContext fetchAndApplyFullListoneOnline()
    }

    private fun normalizeName(name: String): String {
        return name.lowercase()
            .replace("'", "")
            .replace("-", " ")
            .replace(".", "")
            .trim()
    }

    suspend fun setWatchlistTag(playerId: String, tag: WatchlistTag, notes: String = "", customMaxBid: Int? = null) = withContext(Dispatchers.IO) {
        database.watchlistDao().setWatchlistTag(WatchlistEntity(playerId, tag, notes, customMaxBid))
    }

    suspend fun removeWatchlistTag(playerId: String) = withContext(Dispatchers.IO) {
        database.watchlistDao().removeWatchlistTag(playerId)
    }

    suspend fun importCsvStream(inputStream: InputStream): CsvImportResult = withContext(Dispatchers.IO) {
        val result = CsvImporter.parseCsv(inputStream)
        if (result is CsvImportResult.Success) {
            // Replace whole list with the new player dataset enriched with FBref stats
            database.playerDao().clearAll()
            database.playerDao().insertPlayers(result.players)
            // Apply current indisponibili if matching
            fetchAndApplyIndisponibili()
        }
        return@withContext result
    }

    suspend fun importListoneText(text: String): CsvImportResult = withContext(Dispatchers.IO) {
        val result = CsvImporter.parseCsvText(text)
        if (result is CsvImportResult.Success) {
            database.playerDao().clearAll()
            database.playerDao().insertPlayers(result.players)
            fetchAndApplyIndisponibili()
        }
        return@withContext result
    }

    suspend fun resetListoneToDefault(): Int = withContext(Dispatchers.IO) {
        val defaultPlayers = com.example.data.model.PreloadedPlayersData.defaultPlayers
        val enriched = KaggleHistoricalStatsService.enrichList(defaultPlayers)
        database.playerDao().clearAll()
        database.playerDao().insertPlayers(enriched)
        fetchAndApplyIndisponibili()
        return@withContext enriched.size
    }

    suspend fun evaluatePlayerLive(
        player: PlayerEntity
    ): QuantitativeEvaluation = withContext(Dispatchers.Default) {
        val config = database.leagueDao().getLeagueConfig() ?: LeagueConfig()
        val userTeam = database.teamDao().getUserTeam() ?: TeamEntity("Utente", true, 500, 500)
        val allTeams = database.teamDao().getAllTeams()
        val availablePlayers = database.playerDao().getAllAvailablePlayers()
        val events = database.auctionEventDao().getAllEvents()

        QuantitativeEngine.evaluatePlayer(
            player = player,
            userTeam = userTeam,
            allTeams = allTeams,
            availablePlayers = availablePlayers,
            events = events,
            config = config
        )
    }

    suspend fun runMonteCarloSimulation(
        player: PlayerEntity,
        bidPrice: Int,
        isDeepMode: Boolean = false
    ): MonteCarloSimulation.SimulationResult = withContext(Dispatchers.Default) {
        val config = database.leagueDao().getLeagueConfig() ?: LeagueConfig()
        val userTeam = database.teamDao().getUserTeam() ?: TeamEntity("Utente", true, 500, 500)
        val allTeams = database.teamDao().getAllTeams()
        val availablePlayers = database.playerDao().getAllAvailablePlayers()

        MonteCarloSimulation.runSimulation(
            player = player,
            bidPrice = bidPrice,
            userTeam = userTeam,
            allTeams = allTeams,
            availablePlayers = availablePlayers,
            config = config,
            isDeepMode = isDeepMode
        )
    }
}
