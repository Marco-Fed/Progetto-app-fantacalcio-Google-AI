package com.example.ui.screens

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlayerEntity
import com.example.data.model.Role
import com.example.data.model.WatchlistTag
import com.example.ui.theme.*
import com.example.ui.viewmodel.AuctionUiState
import com.example.ui.viewmodel.AuctionViewModel
import com.example.ui.viewmodel.ListoneSortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListoneScreen(
    viewModel: AuctionViewModel,
    uiState: AuctionUiState,
    onNavigateToLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showTeamFilterDialog by remember { mutableStateOf(false) }

    // Filter players based on state
    val filteredPlayers = remember(
        uiState.players,
        uiState.listoneSearchQuery,
        uiState.listoneRoleFilter,
        uiState.listoneTeamFilter,
        uiState.listoneWatchlistOnly,
        uiState.listoneAvailableOnly,
        uiState.listoneSortOrder,
        uiState.watchlist
    ) {
        val watchlistPlayerIds = uiState.watchlist.map { it.playerId }.toSet()
        val query = uiState.listoneSearchQuery.trim().lowercase()

        var list = uiState.players.asSequence()

        if (query.isNotBlank()) {
            list = list.filter {
                it.name.lowercase().contains(query) ||
                it.team.lowercase().contains(query)
            }
        }

        if (uiState.listoneRoleFilter != null) {
            list = list.filter { it.role == uiState.listoneRoleFilter }
        }

        if (!uiState.listoneTeamFilter.isNullOrBlank()) {
            list = list.filter { it.team.equals(uiState.listoneTeamFilter, ignoreCase = true) }
        }

        if (uiState.listoneWatchlistOnly) {
            list = list.filter { it.id in watchlistPlayerIds }
        }

        if (uiState.listoneAvailableOnly) {
            list = list.filter { !it.isPurchased }
        }

        val sorted = when (uiState.listoneSortOrder) {
            ListoneSortOrder.FANTA_VALORE -> list.sortedByDescending { it.expectedFantasyPoints * (it.starterProb2026_27 / 100.0) }
            ListoneSortOrder.FVM -> list.sortedByDescending { it.fvm }
            ListoneSortOrder.QUOTAZIONE -> list.sortedByDescending { it.quotation }
            ListoneSortOrder.ALPHABETICAL -> list.sortedBy { it.name.lowercase() }
            ListoneSortOrder.STARTER_PROB -> list.sortedByDescending { it.starterProb2026_27 }
        }

        sorted.toList()
    }

    val availableTeams = remember(uiState.players) {
        uiState.players.map { it.team }.distinct().filter { it.isNotBlank() }.sorted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Listone Calciatori",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            text = "${filteredPlayers.size} giocatori trovati",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    // Refresh Indisponibili Action
                    IconButton(
                        onClick = { viewModel.fetchAndApplyIndisponibili() },
                        modifier = Modifier.testTag("refresh_indisponibili_button")
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

                    // Sort menu button
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("sort_menu_button")
                        ) {
                            Icon(
                                Icons.Default.Sort,
                                contentDescription = "Ordina",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(DarkSurfaceElevated)
                        ) {
                            ListoneSortOrder.values().forEach { sortOption ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = sortOption.displayName,
                                            color = if (uiState.listoneSortOrder == sortOption) EmeraldPrimary else TextPrimary,
                                            fontWeight = if (uiState.listoneSortOrder == sortOption) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.setListoneSortOrder(sortOption)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (uiState.listoneSortOrder == sortOption) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldPrimary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurfaceElevated)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input Field
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = uiState.listoneSearchQuery,
                    onValueChange = { viewModel.setListoneSearchQuery(it) },
                    placeholder = { Text("Cerca nel listone...", color = TextMuted, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EmeraldPrimary) },
                    trailingIcon = {
                        if (uiState.listoneSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setListoneSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Cancella", tint = TextSecondary)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("listone_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    singleLine = true
                )
            }

            // Role Filters & Toggles Row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "Tutti i Ruoli" chip
                item {
                    FilterChip(
                        selected = uiState.listoneRoleFilter == null,
                        onClick = { viewModel.setListoneRoleFilter(null) },
                        label = { Text("Tutti", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = EmeraldPrimary
                        )
                    )
                }

                // Individual Roles chips: P, D, C, A
                items(Role.values()) { role ->
                    val isSelected = uiState.listoneRoleFilter == role
                    val roleColor = when (role) {
                        Role.P -> RoleColorP
                        Role.D -> RoleColorD
                        Role.C -> RoleColorC
                        Role.A -> RoleColorA
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel.setListoneRoleFilter(if (isSelected) null else role)
                        },
                        label = {
                            Text(
                                text = "${role.code} - ${role.displayName}",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = roleColor.copy(alpha = 0.25f),
                            selectedLabelColor = roleColor
                        )
                    )
                }

                // Team Filter Chip
                item {
                    FilterChip(
                        selected = uiState.listoneTeamFilter != null,
                        onClick = { showTeamFilterDialog = true },
                        label = {
                            Text(
                                text = uiState.listoneTeamFilter ?: "Squadra: Tutte",
                                fontSize = 12.sp
                            )
                        },
                        trailingIcon = {
                            if (uiState.listoneTeamFilter != null) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Rimuovi filtro squadra",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.setListoneTeamFilter(null) }
                                )
                            } else {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldAccent.copy(alpha = 0.2f),
                            selectedLabelColor = GoldAccent
                        )
                    )
                }

                // Watchlist only chip
                item {
                    FilterChip(
                        selected = uiState.listoneWatchlistOnly,
                        onClick = { viewModel.setListoneWatchlistOnly(!uiState.listoneWatchlistOnly) },
                        label = { Text("Solo Preferiti ⭐", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldAccent.copy(alpha = 0.25f),
                            selectedLabelColor = GoldAccent
                        )
                    )
                }

                // Available only chip
                item {
                    FilterChip(
                        selected = uiState.listoneAvailableOnly,
                        onClick = { viewModel.setListoneAvailableOnly(!uiState.listoneAvailableOnly) },
                        label = { Text("Solo Liberi", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary.copy(alpha = 0.25f),
                            selectedLabelColor = EmeraldPrimary
                        )
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Players List
            if (filteredPlayers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FilterListOff,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Nessun giocatore corrisponde ai filtri",
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPlayers, key = { it.id }) { player ->
                        val isWatchlisted = uiState.watchlist.any { it.playerId == player.id }
                        ListonePlayerCard(
                            player = player,
                            isWatchlisted = isWatchlisted,
                            onPlayerClick = {
                                viewModel.selectPlayer(player)
                                onNavigateToLive()
                            },
                            onToggleWatchlist = {
                                viewModel.toggleWatchlist(player.id, WatchlistTag.TARGET)
                            }
                        )
                    }
                }
            }
        }
    }

    // Team Filter Selection Dialog
    if (showTeamFilterDialog) {
        AlertDialog(
            onDismissRequest = { showTeamFilterDialog = false },
            title = { Text("Filtra per Squadra", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Surface(
                            color = if (uiState.listoneTeamFilter == null) EmeraldPrimary.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setListoneTeamFilter(null)
                                    showTeamFilterDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp)
                        ) {
                            Text("Tutte le squadre", fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                        HorizontalDivider(color = DarkSurfaceVariant)
                    }
                    items(availableTeams) { team ->
                        val isSelected = uiState.listoneTeamFilter == team
                        Surface(
                            color = if (isSelected) EmeraldPrimary.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setListoneTeamFilter(team)
                                    showTeamFilterDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = team,
                                color = if (isSelected) EmeraldPrimary else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        HorizontalDivider(color = DarkSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTeamFilterDialog = false }) {
                    Text("Chiudi", color = EmeraldPrimary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun ListonePlayerCard(
    player: PlayerEntity,
    isWatchlisted: Boolean,
    onPlayerClick: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    val roleColor = when (player.role) {
        Role.P -> RoleColorP
        Role.D -> RoleColorD
        Role.C -> RoleColorC
        Role.A -> RoleColorA
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayerClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (player.isPurchased) DarkSurfaceVariant.copy(alpha = 0.6f) else DarkSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Role Badge & Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = roleColor,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = player.role.code,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = player.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (player.isPurchased) TextMuted else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = player.team,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "• Tit. ${player.starterProb2026_27}%",
                                fontSize = 11.sp,
                                color = if (player.starterProb2026_27 >= 80) EmeraldPrimary else GoldAccent
                            )
                        }
                    }
                }

                // Right: Metrics & Star Button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "FVM: ${player.fvm}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = GoldAccent
                        )
                        Text(
                            text = "Qt: ${player.quotation} cr",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    IconButton(
                        onClick = onToggleWatchlist,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isWatchlisted) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Preferiti",
                            tint = if (isWatchlisted) GoldAccent else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Status Banner: Disponibile / Infortunato / Acquistato
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (player.isInjured) {
                    Surface(
                        color = DecisionPass.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("🔴 INFORTUNATO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DecisionPass)
                            if (player.expectedReturnDate.isNotBlank()) {
                                Spacer(Modifier.width(6.dp))
                                Text("• ${player.expectedReturnDate}", fontSize = 10.sp, color = TextPrimary)
                            }
                        }
                    }
                } else {
                    Surface(
                        color = EmeraldPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "🟢 DISPONIBILE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (player.isPurchased) {
                    Surface(
                        color = DarkSurfaceElevated,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Acquistato da ${player.purchasedByTeam ?: ""} (${player.purchasePrice ?: 0} cr)",
                            fontSize = 10.sp,
                            color = GoldAccent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
