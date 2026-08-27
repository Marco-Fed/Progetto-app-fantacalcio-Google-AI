package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.data.remote.KaggleHistoricalStatsService
import com.example.engine.MonteCarloSimulation
import com.example.ui.theme.*
import com.example.ui.viewmodel.AuctionUiState
import com.example.ui.viewmodel.AuctionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveAuctionScreen(
    viewModel: AuctionViewModel,
    uiState: AuctionUiState,
    modifier: Modifier = Modifier
) {
    var showAssignDialog by remember { mutableStateOf(false) }
    var showMonteCarloDialog by remember { mutableStateOf(false) }
    var customBidInput by remember { mutableStateOf("") }

    val userTeam = uiState.userTeam
    val config = uiState.leagueConfig

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Asta Live • Serie A",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        userTeam?.let {
                            Text(
                                text = "Crediti: ${it.remainingCredits}/${it.initialCredits} | Max Offerta: ${it.maxAffordableBid(config)}",
                                fontSize = 12.sp,
                                color = GoldAccent
                            )
                        }
                    }
                },
                actions = {
                    // Update Indisponibili from fantacalcio.it button (REQUEST 2)
                    IconButton(
                        onClick = { viewModel.fetchAndApplyIndisponibili() },
                        modifier = Modifier.testTag("refresh_indisponibili_live_button")
                    ) {
                        if (uiState.isSyncingIndisponibili) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = EmeraldPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.MedicalServices,
                                contentDescription = "Aggiorna Indisponibili",
                                tint = GoldAccent
                            )
                        }
                    }

                    // Undo purchase action
                    IconButton(
                        onClick = { viewModel.undoLastPurchase() },
                        modifier = Modifier.testTag("undo_button")
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = "Annulla Ultimo Acquisto",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurfaceElevated
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // User Team Quick Summary Card
            item {
                UserTeamSummaryCard(userTeam = userTeam, config = config)
            }

            // Quick Search Autocomplete Bar
            item {
                QuickSearchBar(
                    searchQuery = uiState.searchQuery,
                    searchResults = uiState.searchResults,
                    onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    onPlayerSelected = { viewModel.selectPlayer(it) }
                )
            }

            // "Chi Devo Chiamare?" Role-based 10-15 candidates dropdown section (REQUEST 4)
            item {
                ChiDevoChiamareSection(
                    selectedRole = uiState.selectedRoleForCall,
                    candidates = uiState.callCandidates,
                    onRoleSelected = { viewModel.setRoleForCall(it) },
                    onPlayerSelected = { viewModel.selectPlayer(it) }
                )
            }

            // Active Evaluation / Called Player Card
            item {
                uiState.selectedPlayer?.let { player ->
                    CalledPlayerCard(
                        player = player,
                        evaluation = uiState.selectedEvaluation,
                        isEvaluating = uiState.isEvaluating,
                        onAssignClick = {
                            customBidInput = (uiState.selectedEvaluation?.optimalPriceMin ?: player.quotation).toString()
                            showAssignDialog = true
                        },
                        onMonteCarloClick = {
                            viewModel.runMonteCarlo(player, uiState.selectedEvaluation?.optimalPriceMin ?: player.quotation)
                            showMonteCarloDialog = true
                        },
                        onToggleWatchlist = { tag ->
                            viewModel.toggleWatchlist(player.id, tag)
                        },
                        onAlternativeSelected = { altPlayer ->
                            viewModel.selectPlayer(altPlayer)
                        },
                        isWatchlisted = uiState.watchlist.any { it.playerId == player.id }
                    )
                } ?: run {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Nessun giocatore selezionato",
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Cerca un calciatore o selezionalo da 'Chi devo chiamare' o dal 'Listone'",
                                fontSize = 12.sp,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Recent Auction Events Log
            if (uiState.auctionEvents.isNotEmpty()) {
                item {
                    Text(
                        text = "Ultime Assegnazioni Asta",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }

                items(uiState.auctionEvents.take(5)) { event ->
                    AuctionEventRow(event = event)
                }
            }
        }
    }

    // Duplicate Player Assignment Alert (REQUEST 8)
    if (uiState.duplicateAlertMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicateAlert() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = DecisionPass)
                    Spacer(Modifier.width(8.dp))
                    Text("Giocatore Già Assegnato", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Text(uiState.duplicateAlertMessage ?: "", color = TextPrimary, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissDuplicateAlert() },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Ho Capito")
                }
            },
            containerColor = DarkSurface
        )
    }

    // Assign Player Modal Dialog
    if (showAssignDialog && uiState.selectedPlayer != null) {
        AssignPlayerDialog(
            player = uiState.selectedPlayer!!,
            teams = uiState.teams,
            initialBid = uiState.selectedEvaluation?.optimalPriceMin ?: uiState.selectedPlayer!!.quotation,
            config = config,
            onDismiss = { showAssignDialog = false },
            onConfirm = { teamName, price ->
                viewModel.assignPlayer(uiState.selectedPlayer!!.id, teamName, price)
                showAssignDialog = false
            }
        )
    }

    // Monte Carlo Results Dialog
    if (showMonteCarloDialog && uiState.selectedPlayer != null) {
        MonteCarloResultDialog(
            player = uiState.selectedPlayer!!,
            result = uiState.monteCarloResult,
            isLoading = uiState.isRunningSimulation,
            onDismiss = { showMonteCarloDialog = false }
        )
    }
}

@Composable
fun UserTeamSummaryCard(
    userTeam: TeamEntity?,
    config: LeagueConfig
) {
    if (userTeam == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = userTeam.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Budget Minimo di Chiusura: ${userTeam.minimumCompletionBudget(config)} crediti",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Surface(
                    color = EmeraldContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${userTeam.remainingCredits} cr",
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Role Slots Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoleSlotIndicator(
                    role = Role.P,
                    filled = userTeam.purchasedCountP,
                    total = config.slotsP,
                    color = RoleColorP,
                    modifier = Modifier.weight(1f)
                )
                RoleSlotIndicator(
                    role = Role.D,
                    filled = userTeam.purchasedCountD,
                    total = config.slotsD,
                    color = RoleColorD,
                    modifier = Modifier.weight(1f)
                )
                RoleSlotIndicator(
                    role = Role.C,
                    filled = userTeam.purchasedCountC,
                    total = config.slotsC,
                    color = RoleColorC,
                    modifier = Modifier.weight(1f)
                )
                RoleSlotIndicator(
                    role = Role.A,
                    filled = userTeam.purchasedCountA,
                    total = config.slotsA,
                    color = RoleColorA,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun RoleSlotIndicator(
    role: Role,
    filled: Int,
    total: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = role.code,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = color
            )
            Text(
                text = "$filled/$total",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (filled >= total) TextMuted else TextPrimary
            )
        }
    }
}

@Composable
fun QuickSearchBar(
    searchQuery: String,
    searchResults: List<PlayerEntity>,
    onQueryChanged: (String) -> Unit,
    onPlayerSelected: (PlayerEntity) -> Unit
) {
    Column {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChanged,
            placeholder = { Text("Cerca giocatore o squadra...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EmeraldPrimary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Cancella", tint = TextSecondary)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_player_input"),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldPrimary,
                unfocusedBorderColor = DarkSurfaceVariant,
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface
            ),
            singleLine = true
        )

        if (searchResults.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    searchResults.forEach { player ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayerSelected(player) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = when (player.role) {
                                        Role.P -> RoleColorP
                                        Role.D -> RoleColorD
                                        Role.C -> RoleColorC
                                        Role.A -> RoleColorA
                                    }.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = player.role.code,
                                        color = when (player.role) {
                                            Role.P -> RoleColorP
                                            Role.D -> RoleColorD
                                            Role.C -> RoleColorC
                                            Role.A -> RoleColorA
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = player.name,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${player.team} • Tit. ${player.starterProb2026_27}%",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Text(
                                text = "Qt: ${player.quotation} | FVM: ${player.fvm}",
                                fontSize = 12.sp,
                                color = GoldAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider(color = DarkSurfaceVariant, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

/**
 * REQUEST 4: "Chi Devo Chiamare" section
 * First select role (P, D, C, A), then propose 10/15 possibilities to select via dropdown menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChiDevoChiamareSection(
    selectedRole: Role,
    candidates: List<PlayerEntity>,
    onRoleSelected: (Role) -> Unit,
    onPlayerSelected: (PlayerEntity) -> Unit
) {
    var expandedDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🎯 Chi Devo Chiamare?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
                Text(
                    text = "${candidates.size} suggeriti per ${selectedRole.displayName}",
                    fontSize = 11.sp,
                    color = GoldAccent
                )
            }

            Spacer(Modifier.height(10.dp))

            // Step 1: Select Role Tabs (P, D, C, A)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Role.values().forEach { role ->
                    val isSelected = selectedRole == role
                    val roleColor = when (role) {
                        Role.P -> RoleColorP
                        Role.D -> RoleColorD
                        Role.C -> RoleColorC
                        Role.A -> RoleColorA
                    }
                    Surface(
                        color = if (isSelected) roleColor else DarkSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onRoleSelected(role) }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = role.code,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = if (isSelected) Color.White else roleColor
                            )
                            Text(
                                text = role.displayName.take(4),
                                fontSize = 10.sp,
                                color = if (isSelected) Color.White.copy(alpha = 0.9f) else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Step 2: Dropdown Menu with 10-15 ranked possibilities
            ExposedDropdownMenuBox(
                expanded = expandedDropdown,
                onExpandedChange = { expandedDropdown = !expandedDropdown },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = "Seleziona tra i migliori ${candidates.size} ${selectedRole.displayName}...",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedContainerColor = DarkSurfaceElevated,
                        unfocusedContainerColor = DarkSurfaceElevated,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false },
                    modifier = Modifier.background(DarkSurfaceElevated)
                ) {
                    if (candidates.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Nessun giocatore libero per questo ruolo", color = TextMuted) },
                            onClick = { expandedDropdown = false }
                        )
                    } else {
                        candidates.forEachIndexed { index, player ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = EmeraldPrimary.copy(alpha = 0.2f),
                                                shape = CircleShape,
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "#${index + 1}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = EmeraldPrimary
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = player.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "${player.team} • Tit. ${player.starterProb2026_27}% • FVM: ${player.fvm}",
                                                    fontSize = 11.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                        }

                                        if (player.isInjured) {
                                            Text(
                                                text = "🔴 Infortunato",
                                                fontSize = 10.sp,
                                                color = DecisionPass,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onPlayerSelected(player)
                                    expandedDropdown = false
                                }
                            )
                            HorizontalDivider(color = DarkSurfaceVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Called Player Card featuring:
 * - REQUEST 2: Mandatory initial status (Disponibile vs Infortunato) + Expected Return Date (only if injured).
 * - REQUEST 5: Clickable comparable alternatives navigating to that player.
 */
@Composable
fun CalledPlayerCard(
    player: PlayerEntity,
    evaluation: QuantitativeEvaluation?,
    isEvaluating: Boolean,
    onAssignClick: () -> Unit,
    onMonteCarloClick: () -> Unit,
    onToggleWatchlist: (WatchlistTag) -> Unit,
    onAlternativeSelected: (PlayerEntity) -> Unit,
    isWatchlisted: Boolean
) {
    val stats24 = KaggleHistoricalStatsService.parseStats(player.stats2024_25Json)
    val stats25 = KaggleHistoricalStatsService.parseStats(player.stats2025_26Json)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Role Badge, Name, Team, Watchlist Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = when (player.role) {
                            Role.P -> RoleColorP
                            Role.D -> RoleColorD
                            Role.C -> RoleColorC
                            Role.A -> RoleColorA
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = player.role.code,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = player.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            text = "${player.team} • Quotazione: ${player.quotation} | FVM: ${player.fvm}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = { onToggleWatchlist(WatchlistTag.TARGET) },
                    modifier = Modifier.testTag("watchlist_button")
                ) {
                    Icon(
                        if (isWatchlisted) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Watchlist",
                        tint = if (isWatchlisted) GoldAccent else TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // REQUEST 2: MANDATORY INITIAL STATUS & RETURN DATE BANNER
            Surface(
                color = if (player.isInjured) DecisionPass.copy(alpha = 0.15f) else EmeraldPrimary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (player.isInjured) DecisionPass.copy(alpha = 0.5f) else EmeraldPrimary.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (player.isInjured) "🔴 INFORTUNATO" else "🟢 DISPONIBILE",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = if (player.isInjured) DecisionPass else EmeraldPrimary
                            )
                        }
                        Text(
                            text = "Fonte: fantacalcio.it",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }

                    if (player.isInjured) {
                        Spacer(Modifier.height(4.dp))
                        if (player.injuryNotes.isNotBlank()) {
                            Text(
                                text = "Infortunio: ${player.injuryNotes}",
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }
                        if (player.expectedReturnDate.isNotBlank()) {
                            Text(
                                text = "Data di Rientro Prevista: ${player.expectedReturnDate}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = GoldAccent
                            )
                        }
                    } else {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Nessun infortunio segnalato. Idoneo e disponibile per l'asta.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // DECISION BANNER (CRITICAL)
            evaluation?.let { eval ->
                Surface(
                    color = Color(eval.decision.colorHex).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(eval.decision.colorHex)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = eval.decision.emoji,
                                    fontSize = 18.sp
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = eval.decision.label,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = Color(eval.decision.colorHex)
                                )
                            }
                            Text(
                                text = "Fase ${player.role.displayName}: ${eval.roleAuctionPhase.label} • Win Prob MC: ${(eval.winProbabilityMonteCarlo * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Price Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        label = "Prezzo Ottimale",
                        value = "${eval.optimalPriceMin}–${eval.optimalPriceMax} cr",
                        highlightColor = EmeraldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "Prezzo Massimo",
                        value = "${eval.maximumBid} cr",
                        highlightColor = GoldAccent,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "Prezzo Previsto",
                        value = "${eval.expectedAuctionPriceMin}–${eval.expectedAuctionPriceMax} cr",
                        highlightColor = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(14.dp))
            }

            // TITOLARITÀ PREVISTA 2026-27 & STORICA
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TITOLARITÀ PREVISTA 2026-27:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            text = "${player.starterProb2026_27}%",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = if (player.starterProb2026_27 >= 80) EmeraldPrimary else GoldAccent
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (player.starterProb2026_27 / 100f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (player.starterProb2026_27 >= 80) EmeraldPrimary else GoldAccent,
                        trackColor = DarkSurfaceElevated,
                    )

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "PERCENTUALI TITOLARITÀ & STATISTICHE STORICHE (FBref/Kaggle):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    
                    val hasAnyStats = (stats25 != null && stats25.appearances > 0) ||
                                     (stats24 != null && stats24.appearances > 0)

                    if (hasAnyStats) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Stagione 2025-26
                            if (stats25 != null && stats25.appearances > 0) {
                                Surface(
                                    color = DarkSurfaceElevated,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Stagione 2025-26 • ${stats25.team} (${stats25.competition.ifBlank { "Top 5 EU" }})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color.White
                                            )
                                            Surface(
                                                color = (if (stats25.starterPercentage >= 75) EmeraldPrimary else GoldAccent).copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${stats25.starterPercentage}% Titolarità",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 11.sp,
                                                    color = if (stats25.starterPercentage >= 75) EmeraldPrimary else GoldAccent,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = "Presenze: ${stats25.starterAppearances} titolari su ${stats25.appearances} tot (${stats25.minutes} min)",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        val statsDetail = if (player.role == Role.P) {
                                            "Clean Sheet: ${stats25.cleanSheets} • Parate: ${stats25.saves} • Gol Subiti: ${stats25.goalsAgainst} • G/R: ${stats25.yellowCards}/${stats25.redCards}"
                                        } else {
                                            "Gol: ${stats25.goals} (xG: ${stats25.expectedGoals}) • Assist: ${stats25.assists} (xAG: ${stats25.expectedAssists}) • Rigori: ${stats25.penaltiesScored} • G/R: ${stats25.yellowCards}/${stats25.redCards}"
                                        }
                                        Text(
                                            text = statsDetail,
                                            fontSize = 11.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "• Stagione 2025-26: Non presente nei campionati europei top 5",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            // Stagione 2024-25
                            if (stats24 != null && stats24.appearances > 0) {
                                Surface(
                                    color = DarkSurfaceElevated,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Stagione 2024-25 • ${stats24.team} (${stats24.competition.ifBlank { "Top 5 EU" }})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color.White
                                            )
                                            Surface(
                                                color = (if (stats24.starterPercentage >= 75) EmeraldPrimary else GoldAccent).copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${stats24.starterPercentage}% Titolarità",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 11.sp,
                                                    color = if (stats24.starterPercentage >= 75) EmeraldPrimary else GoldAccent,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = "Presenze: ${stats24.starterAppearances} titolari su ${stats24.appearances} tot (${stats24.minutes} min)",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        val statsDetail24 = if (player.role == Role.P) {
                                            "Clean Sheet: ${stats24.cleanSheets} • Parate: ${stats24.saves} • Gol Subiti: ${stats24.goalsAgainst} • G/R: ${stats24.yellowCards}/${stats24.redCards}"
                                        } else {
                                            "Gol: ${stats24.goals} (xG: ${stats24.expectedGoals}) • Assist: ${stats24.assists} (xAG: ${stats24.expectedAssists}) • Rigori: ${stats24.penaltiesScored} • G/R: ${stats24.yellowCards}/${stats24.redCards}"
                                        }
                                        Text(
                                            text = statsDetail24,
                                            fontSize = 11.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "• Stagione 2024-25: Non presente nei campionati europei top 5",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Dati storici non disponibili (valutazione basata su FVM, Quotazione e indicatori 2026/27)",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    if (player.ballottaggioRival != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "⚠️ Ballottaggio con ${player.ballottaggioRival} (stima quota titolare: ${player.ballottaggioShare}%)",
                            fontSize = 11.sp,
                            color = GoldAccent
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // "PERCHÉ COMPRARLO" & "PERCHÉ NON PAGARE OLTRE"
            evaluation?.let { eval ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "✅ PERCHÉ COMPRARLO",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = EmeraldPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            eval.reasons.buyReasons.forEach { reason ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text("• ", color = EmeraldPrimary, fontSize = 12.sp)
                                    Text(reason, fontSize = 12.sp, color = TextPrimary)
                                }
                            }
                        }
                    }

                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "🛑 PERCHÉ NON PAGARE OLTRE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = DecisionPass
                            )
                            Spacer(Modifier.height(4.dp))
                            eval.reasons.cautionReasons.forEach { reason ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text("• ", color = DecisionPass, fontSize = 12.sp)
                                    Text(reason, fontSize = 12.sp, color = TextPrimary)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // REQUEST 5: CLICKABLE COMPARABLE ALTERNATIVES
                Text(
                    text = "Alternative Comparabili nel Ruolo (Clicca per selezionare)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))

                if (eval.alternatives.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        eval.alternatives.forEach { alt ->
                            Surface(
                                color = DarkSurfaceElevated,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAlternativeSelected(alt.player) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.TouchApp,
                                            contentDescription = "Seleziona",
                                            tint = EmeraldPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "${alt.player.name} (${alt.player.team})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Tit. ${alt.starterProb}% • FM: ${String.format("%.2f", alt.expectedFantasyPoints)}",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Stima: ${alt.estimatedPrice} cr",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = GoldAccent
                                        )
                                        Text(
                                            text = "Δ ${if (alt.valueDifference >= 0) "+" else ""}${alt.valueDifference} pts",
                                            fontSize = 10.sp,
                                            color = if (alt.valueDifference >= 0) EmeraldPrimary else DecisionPass
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Non ci sono alternative comparabili disponibili in questo momento.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Spacer(Modifier.height(16.dp))
            }

            // ACTION BUTTONS: "Assegna Giocatore" & "Testa Monte Carlo"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onMonteCarloClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("monte_carlo_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = GoldAccent
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent)
                ) {
                    Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Simula MC", fontSize = 12.sp)
                }

                Button(
                    onClick = onAssignClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("assign_player_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Assegna", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MetricBox(
    label: String,
    value: String,
    highlightColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = highlightColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AuctionEventRow(event: AuctionEventEntity) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = when (event.playerRole) {
                        Role.P -> RoleColorP
                        Role.D -> RoleColorD
                        Role.C -> RoleColorC
                        Role.A -> RoleColorA
                    }.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = event.playerRole.code,
                        color = when (event.playerRole) {
                            Role.P -> RoleColorP
                            Role.D -> RoleColorD
                            Role.C -> RoleColorC
                            Role.A -> RoleColorA
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = event.playerName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "${event.playerTeam} ➔ ${event.buyerTeamName}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
            Text(
                text = "${event.price} cr",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GoldAccent
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignPlayerDialog(
    player: PlayerEntity,
    teams: List<TeamEntity>,
    initialBid: Int,
    config: LeagueConfig,
    onDismiss: () -> Unit,
    onConfirm: (teamName: String, price: Int) -> Unit
) {
    var selectedTeamName by remember { mutableStateOf(teams.firstOrNull()?.name ?: "") }
    var priceText by remember { mutableStateOf(initialBid.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val selectedTeam = teams.firstOrNull { it.name == selectedTeamName }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Assegna ${player.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Text(
                    text = "${player.role.displayName} • ${player.team}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Team Selector Dropdown / Row
                Text(
                    text = "Squadra acquirente:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = TextPrimary
                )

                var expandedTeams by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedTeams,
                    onExpandedChange = { expandedTeams = !expandedTeams }
                ) {
                    OutlinedTextField(
                        value = selectedTeamName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTeams) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = DarkSurfaceVariant,
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = expandedTeams,
                        onDismissRequest = { expandedTeams = false },
                        modifier = Modifier.background(DarkSurfaceElevated)
                    ) {
                        teams.forEach { team ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = team.name,
                                            fontWeight = if (team.isUserTeam) FontWeight.Bold else FontWeight.Normal,
                                            color = if (team.isUserTeam) EmeraldPrimary else TextPrimary
                                        )
                                        Text(
                                            text = "${team.remainingCredits} cr (max ${team.maxAffordableBid(config)})",
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                    }
                                },
                                onClick = {
                                    selectedTeamName = team.name
                                    expandedTeams = false
                                }
                            )
                        }
                    }
                }

                // Price Input
                OutlinedTextField(
                    value = priceText,
                    onValueChange = {
                        priceText = it
                        errorMessage = null
                    },
                    label = { Text("Prezzo d'Asta Finale (crediti)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = DarkSurfaceVariant
                    ),
                    singleLine = true
                )

                // Validation info
                selectedTeam?.let { team ->
                    val maxBid = team.maxAffordableBid(config)
                    val slotsLeft = team.remainingSlotsForRole(player.role, config)

                    Text(
                        text = "Budget residuo: ${team.remainingCredits} cr • Max offerta consentita: $maxBid cr • Slot ${player.role.code} rimasti: $slotsLeft",
                        fontSize = 11.sp,
                        color = if (slotsLeft <= 0) DecisionPass else TextSecondary
                    )
                }

                errorMessage?.let {
                    Text(it, color = DecisionPass, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceText.toIntOrNull()
                    if (price == null || price <= 0) {
                        errorMessage = "Inserisci un prezzo valido (>= 1 credito)"
                        return@Button
                    }
                    val team = teams.firstOrNull { it.name == selectedTeamName }
                    if (team == null) {
                        errorMessage = "Seleziona una squadra"
                        return@Button
                    }
                    if (price > team.remainingCredits) {
                        errorMessage = "Crediti insufficienti (${team.remainingCredits} cr disponibili)"
                        return@Button
                    }
                    if (team.remainingSlotsForRole(player.role, config) <= 0) {
                        errorMessage = "Slot completati per il ruolo ${player.role.displayName}"
                        return@Button
                    }
                    onConfirm(selectedTeamName, price)
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Conferma Acquisto")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla", color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )
}

@Composable
fun MonteCarloResultDialog(
    player: PlayerEntity,
    result: MonteCarloSimulation.SimulationResult?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Simulazione Monte Carlo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = GoldAccent
                )
                Text(
                    text = "Valutazione probabilistica per ${player.name}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        },
        text = {
            if (isLoading || result == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = GoldAccent)
                        Spacer(Modifier.height(12.dp))
                        Text("Simulazione di 100+ aste in corso...", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Probabilità Vittoria con Acquisto:", fontSize = 12.sp, color = TextSecondary)
                                Text(
                                    text = "${(result.winRateWithPlayer * 100).toInt()}%",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = EmeraldPrimary
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Simulazioni eseguite:", fontSize = 12.sp, color = TextSecondary)
                                Text(
                                    text = "${result.simulatedRounds} round (${result.executionTimeMs} ms)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            val delta = result.avgFinalRosterPointsWithPlayer - result.avgFinalRosterPointsWithoutPlayer
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Vantaggio Netto Atteso (Δ Punti):", fontSize = 12.sp, color = TextSecondary)
                                Text(
                                    text = "${if (delta >= 0) "+" else ""}${String.format("%.2f", delta)} pts",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = if (delta >= 0) EmeraldPrimary else DecisionPass
                                )
                            }
                        }
                    }

                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Punteggio Totale Rosa Atteso:", fontSize = 12.sp, color = TextSecondary)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Acquistando ${player.name}:", fontSize = 12.sp, color = TextPrimary)
                                Text("${String.format("%.1f", result.avgFinalRosterPointsWithPlayer)} pts", fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Passando su ${player.name}:", fontSize = 12.sp, color = TextPrimary)
                                Text("${String.format("%.1f", result.avgFinalRosterPointsWithoutPlayer)} pts", fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)) {
                Text("Chiudi", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkSurface
    )
}
