package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.remote.InjuryParserService
import com.example.data.remote.KaggleHistoricalStatsService
import com.example.data.repository.AssignmentResult
import com.example.data.repository.AuctionRepository
import com.example.data.repository.CsvImportResult
import com.example.engine.MonteCarloSimulation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.InputStream

enum class ListoneSortOrder(val displayName: String) {
    FANTA_VALORE("FantaValore (Decrescente)"),
    FVM("FVM (Decrescente)"),
    QUOTAZIONE("Quotazione (Decrescente)"),
    ALPHABETICAL("Alfabetico (A-Z)"),
    STARTER_PROB("Titolarità % (Decrescente)")
}

data class AuctionUiState(
    val isLoading: Boolean = true,
    val players: List<PlayerEntity> = emptyList(),
    val availablePlayers: List<PlayerEntity> = emptyList(),
    val teams: List<TeamEntity> = emptyList(),
    val userTeam: TeamEntity? = null,
    val leagueConfig: LeagueConfig = LeagueConfig(),
    val auctionEvents: List<AuctionEventEntity> = emptyList(),
    val watchlist: List<WatchlistEntity> = emptyList(),
    
    // Live Selected Player & Evaluation
    val searchQuery: String = "",
    val searchResults: List<PlayerEntity> = emptyList(),
    val selectedPlayer: PlayerEntity? = null,
    val selectedEvaluation: QuantitativeEvaluation? = null,
    val isEvaluating: Boolean = false,
    
    // Monte Carlo State
    val monteCarloResult: MonteCarloSimulation.SimulationResult? = null,
    val isRunningSimulation: Boolean = false,
    
    // "Chi Devo Chiamare?" Role-based candidates (10-15 candidates per role)
    val selectedRoleForCall: Role = Role.A,
    val callCandidates: List<PlayerEntity> = emptyList(),
    
    // Listone Screen filters
    val listoneRoleFilter: Role? = null,
    val listoneTeamFilter: String? = null,
    val listoneWatchlistOnly: Boolean = false,
    val listoneAvailableOnly: Boolean = false,
    val listoneSearchQuery: String = "",
    val listoneSortOrder: ListoneSortOrder = ListoneSortOrder.FANTA_VALORE,
    
    // Sync states
    val isSyncingIndisponibili: Boolean = false,
    val isSyncingQuotazioni: Boolean = false,
    val injuriesInputText: String = InjuryParserService.DEFAULT_INJURIES_TEXT,
    
    // UI notifications & Duplicate alerts
    val duplicateAlertMessage: String? = null,
    val userMessage: String? = null,
    val isError: Boolean = false
)

class AuctionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AuctionRepository

    private val _uiState = MutableStateFlow(AuctionUiState())
    val uiState: StateFlow<AuctionUiState> = _uiState.asStateFlow()

    init {
        KaggleHistoricalStatsService.ensureInitialized(application)
        val db = AppDatabase.getInstance(application)
        repository = AuctionRepository(db)

        viewModelScope.launch {
            repository.initializeDefaultDataIfEmpty()
            observeData()
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            val mainFlow = combine(
                repository.allPlayers,
                repository.allTeams,
                repository.userTeam,
                repository.auctionEvents,
                repository.leagueConfig
            ) { players, teams, userTeam, events, config ->
                Tuples5(players, teams, userTeam, events, config ?: LeagueConfig())
            }

            combine(mainFlow, repository.watchlist) { (players, teams, userTeam, events, config), watchlist ->
                val available = players.filter { !it.isPurchased }
                val effectiveUserTeam = userTeam ?: teams.firstOrNull { it.isUserTeam }

                val currentRole = _uiState.value.selectedRoleForCall
                val candidates = computeCallCandidates(available, currentRole)

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        players = players,
                        availablePlayers = available,
                        teams = teams,
                        userTeam = effectiveUserTeam,
                        leagueConfig = config,
                        auctionEvents = events,
                        watchlist = watchlist,
                        callCandidates = candidates
                    )
                }

                // If a player is selected, re-evaluate them with new state
                _uiState.value.selectedPlayer?.let { sel ->
                    val updatedSel = players.firstOrNull { it.id == sel.id } ?: sel
                    evaluatePlayer(updatedSel)
                }
            }.collect()
        }
    }

    private fun computeCallCandidates(available: List<PlayerEntity>, role: Role): List<PlayerEntity> {
        return available.filter { it.role == role }
            .sortedWith(
                compareByDescending<PlayerEntity> { it.expectedFantasyPoints * (it.starterProb2026_27 / 100.0) }
                    .thenByDescending { it.fvm }
                    .thenByDescending { it.quotation }
            )
            .take(15)
    }

    private data class Tuples5<A, B, C, D, E>(
        val a: A,
        val b: B,
        val c: C,
        val d: D,
        val e: E
    )

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            val clean = query.trim().lowercase()
            val filtered = if (clean.isBlank()) {
                emptyList()
            } else {
                current.players.filter {
                    it.name.lowercase().contains(clean) ||
                    it.team.lowercase().contains(clean) ||
                    it.role.name.lowercase() == clean
                }.take(10)
            }
            current.copy(searchQuery = query, searchResults = filtered)
        }
    }

    fun selectPlayer(player: PlayerEntity) {
        _uiState.update { it.copy(selectedPlayer = player, searchQuery = player.name, searchResults = emptyList()) }
        evaluatePlayer(player)
    }

    fun clearSelectedPlayer() {
        _uiState.update { it.copy(selectedPlayer = null, selectedEvaluation = null, monteCarloResult = null) }
    }

    fun setRoleForCall(role: Role) {
        val candidates = computeCallCandidates(_uiState.value.availablePlayers, role)
        _uiState.update { it.copy(selectedRoleForCall = role, callCandidates = candidates) }
    }

    private fun evaluatePlayer(player: PlayerEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isEvaluating = true) }
            val evaluation = repository.evaluatePlayerLive(player)
            _uiState.update { it.copy(selectedEvaluation = evaluation, isEvaluating = false) }
        }
    }

    fun runMonteCarlo(player: PlayerEntity, bidPrice: Int, isDeepMode: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunningSimulation = true) }
            val result = repository.runMonteCarloSimulation(player, bidPrice, isDeepMode)
            _uiState.update { it.copy(monteCarloResult = result, isRunningSimulation = false) }
        }
    }

    fun assignPlayer(playerId: String, teamName: String, price: Int) {
        viewModelScope.launch {
            val result = repository.assignPlayer(playerId, teamName, price)
            when (result) {
                is AssignmentResult.Success -> {
                    _uiState.update {
                        it.copy(
                            userMessage = "Giocatore assegnato a $teamName per $price crediti!",
                            isError = false,
                            duplicateAlertMessage = null
                        )
                    }
                }
                is AssignmentResult.AlreadyAssigned -> {
                    val message = "ATTENZIONE: ${result.playerName} è già stato acquistato dalla squadra '${result.teamName}'! Impossibile assegnarlo nuovamente."
                    _uiState.update {
                        it.copy(
                            duplicateAlertMessage = message,
                            userMessage = message,
                            isError = true
                        )
                    }
                }
                is AssignmentResult.InsufficientCredits -> {
                    _uiState.update {
                        it.copy(
                            userMessage = "Crediti insufficienti per $teamName: offerta $price crediti > max rilancio sostenibile (${result.maxBid} crediti).",
                            isError = true
                        )
                    }
                }
                is AssignmentResult.RoleSlotsFull -> {
                    _uiState.update {
                        it.copy(
                            userMessage = "Slot completati per il ruolo ${result.role.displayName} nella squadra ${result.teamName}!",
                            isError = true
                        )
                    }
                }
                is AssignmentResult.Error -> {
                    _uiState.update {
                        it.copy(
                            userMessage = result.message,
                            isError = true
                        )
                    }
                }
            }
        }
    }

    fun dismissDuplicateAlert() {
        _uiState.update { it.copy(duplicateAlertMessage = null) }
    }

    fun undoLastPurchase() {
        viewModelScope.launch {
            val success = repository.undoLastPurchase()
            if (success) {
                _uiState.update { it.copy(userMessage = "Ultimo acquisto annullato con successo.", isError = false) }
            } else {
                _uiState.update { it.copy(userMessage = "Nessun acquisto precedente da annullare.", isError = true) }
            }
        }
    }

    fun resetAuction() {
        viewModelScope.launch {
            repository.resetAuction()
            _uiState.update { it.copy(userMessage = "Asta resettata: tutti i crediti e slot ripristinati.", isError = false) }
        }
    }

    fun saveLeagueConfig(config: LeagueConfig) {
        viewModelScope.launch {
            repository.saveLeagueConfig(config)
            _uiState.update { it.copy(userMessage = "Parametri di lega aggiornati e crediti ricalcolati!", isError = false) }
        }
    }

    fun updateTeamName(oldName: String, newName: String) {
        viewModelScope.launch {
            val success = repository.updateTeamName(oldName, newName)
            if (success) {
                _uiState.update { it.copy(userMessage = "Nome squadra aggiornato in '$newName'", isError = false) }
            } else {
                _uiState.update { it.copy(userMessage = "Impossibile aggiornare nome: già esistente o non valido.", isError = true) }
            }
        }
    }

    fun updateInjuriesInputText(newText: String) {
        _uiState.update { it.copy(injuriesInputText = newText) }
    }

    fun resetInjuriesInputTextToDefault() {
        _uiState.update { it.copy(injuriesInputText = InjuryParserService.DEFAULT_INJURIES_TEXT) }
    }

    fun parseAndApplyIndisponibili(customText: String? = null) {
        val textToParse = customText ?: _uiState.value.injuriesInputText
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingIndisponibili = true) }
            try {
                val count = repository.parseAndApplyIndisponibiliFromText(textToParse)
                _uiState.update {
                    it.copy(
                        isSyncingIndisponibili = false,
                        userMessage = "Infortuni & Squalificati analizzati dall'LLM: $count giocatori aggiornati.",
                        isError = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSyncingIndisponibili = false,
                        userMessage = "Errore durante l'analisi del testo infortuni: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    fun fetchAndApplyIndisponibili() {
        parseAndApplyIndisponibili(_uiState.value.injuriesInputText)
    }

    fun fetchAndApplyQuotazioniOnline() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingQuotazioni = true) }
            try {
                val count = repository.fetchAndApplyFullListoneOnline()
                _uiState.update {
                    it.copy(
                        isSyncingQuotazioni = false,
                        userMessage = "Listone completo Fantacalcio.it aggiornato e motore di pricing rigirato ($count giocatori caricati).",
                        isError = false
                    )
                }
                _uiState.value.selectedPlayer?.let { sel ->
                    evaluatePlayer(sel)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSyncingQuotazioni = false,
                        userMessage = "Errore durante lo scarico del listone online: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    fun toggleWatchlist(playerId: String, tag: WatchlistTag = WatchlistTag.TARGET) {
        viewModelScope.launch {
            val currentList = _uiState.value.watchlist
            val entry = currentList.firstOrNull { it.playerId == playerId }
            if (entry != null) {
                repository.removeWatchlistTag(playerId)
                _uiState.update { it.copy(userMessage = "Rimosso dai preferiti", isError = false) }
            } else {
                repository.setWatchlistTag(playerId, tag)
                _uiState.update { it.copy(userMessage = "Aggiunto ai preferiti (${tag.name})", isError = false) }
            }
        }
    }

    fun importCsv(inputStream: InputStream) {
        importListoneStream(inputStream)
    }

    fun importListoneStream(inputStream: InputStream) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.importCsvStream(inputStream)
            when (result) {
                is CsvImportResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userMessage = "Listone caricato con successo da ${result.sourceFormat} (${result.players.size} calciatori). Statistiche storiche e motore di pricing rigenerati!",
                            isError = false
                        )
                    }
                    _uiState.value.selectedPlayer?.let { sel ->
                        val updatedSel = _uiState.value.players.firstOrNull { it.name.equals(sel.name, ignoreCase = true) }
                        if (updatedSel != null) {
                            selectPlayer(updatedSel)
                        } else {
                            clearSelectedPlayer()
                        }
                    }
                }
                is CsvImportResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userMessage = "Errore import listone: ${result.message}",
                            isError = true
                        )
                    }
                }
            }
        }
    }

    fun importCsvText(csvText: String) {
        importListoneText(csvText)
    }

    fun importListoneText(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.importListoneText(text)
            when (result) {
                is CsvImportResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userMessage = "Listone importato con successo (${result.players.size} calciatori). Statistiche storiche e pricing aggiornati!",
                            isError = false
                        )
                    }
                }
                is CsvImportResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userMessage = "Errore import testo: ${result.message}",
                            isError = true
                        )
                    }
                }
            }
        }
    }

    fun resetListoneToDefault() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val count = repository.resetListoneToDefault()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userMessage = "Listone ripristinato con successo ai valori predefiniti ($count calciatori con statistiche storiche aggiornate).",
                        isError = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userMessage = "Errore durante il ripristino del listone: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    // Listone filter updates
    fun setListoneRoleFilter(role: Role?) {
        _uiState.update { it.copy(listoneRoleFilter = role) }
    }

    fun setListoneTeamFilter(team: String?) {
        _uiState.update { it.copy(listoneTeamFilter = team) }
    }

    fun setListoneWatchlistOnly(enabled: Boolean) {
        _uiState.update { it.copy(listoneWatchlistOnly = enabled) }
    }

    fun setListoneAvailableOnly(enabled: Boolean) {
        _uiState.update { it.copy(listoneAvailableOnly = enabled) }
    }

    fun setListoneSearchQuery(query: String) {
        _uiState.update { it.copy(listoneSearchQuery = query) }
    }

    fun setListoneSortOrder(sortOrder: ListoneSortOrder) {
        _uiState.update { it.copy(listoneSortOrder = sortOrder) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
