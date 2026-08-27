package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AuctionUiState
import com.example.ui.viewmodel.AuctionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreAuctionScreen(
    viewModel: AuctionViewModel,
    uiState: AuctionUiState,
    onNavigateToLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Budget Strategico", "Top Target", "Scommesse", "Portieri & Coppie")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Strategia Pre-Asta",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
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
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkSurface,
                contentColor = EmeraldPrimary,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) EmeraldPrimary else TextSecondary
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> StrategicBudgetTab(config = uiState.leagueConfig)
                1 -> TopTargetsTab(players = uiState.availablePlayers, onSelect = {
                    viewModel.selectPlayer(it)
                    onNavigateToLive()
                })
                2 -> SleepersTab(players = uiState.availablePlayers, onSelect = {
                    viewModel.selectPlayer(it)
                    onNavigateToLive()
                })
                3 -> GoalkeepersAndPairsTab(players = uiState.availablePlayers, onSelect = {
                    viewModel.selectPlayer(it)
                    onNavigateToLive()
                })
            }
        }
    }
}

@Composable
fun StrategicBudgetTab(config: LeagueConfig) {
    val total = config.initialCredits
    val pBudget = (total * 0.08).toInt()
    val dBudget = (total * 0.20).toInt()
    val cBudget = (total * 0.27).toInt()
    val aBudget = (total * 0.45).toInt()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ripartizione Budget Consigliata ($total crediti)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Basata sulla distribuzione ottimale del valore atteso della rosa.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(Modifier.height(16.dp))

                    BudgetBarRow("Portieri (P)", "8%", "$pBudget cr", "1 top + 2 coperture", RoleColorP)
                    Spacer(Modifier.height(10.dp))
                    BudgetBarRow("Difensori (D)", "20%", "$dBudget cr", "2 semi-top + titolari da modificatore", RoleColorD)
                    Spacer(Modifier.height(10.dp))
                    BudgetBarRow("Centrocampisti (C)", "27%", "$cBudget cr", "2 rigoristi/piazzati + 3 titolari", RoleColorC)
                    Spacer(Modifier.height(10.dp))
                    BudgetBarRow("Attaccanti (A)", "45%", "$aBudget cr", "1 primo slot + 2 secondi slot", RoleColorA)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "3 Regole Aumenta-Valore",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GoldAccent
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("1. Non forzare il marginal value in EARLY: acquista certezze a prezzo equo.", fontSize = 12.sp, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("2. In MID e LATE tieni sempre conto della scarsità e della necessità dei concorrenti.", fontSize = 12.sp, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("3. Non sforare mai il budget minimo per completare tutti gli slot a 1 credito.", fontSize = 12.sp, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun BudgetBarRow(roleName: String, percentage: String, amount: String, note: String, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = color,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.size(10.dp)
                ) {}
                Spacer(Modifier.width(8.dp))
                Text(roleName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
            }
            Text("$percentage • $amount", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GoldAccent)
        }
        Spacer(Modifier.height(2.dp))
        Text(note, fontSize = 11.sp, color = TextMuted, modifier = Modifier.padding(start = 18.dp))
    }
}

@Composable
fun TopTargetsTab(players: List<PlayerEntity>, onSelect: (PlayerEntity) -> Unit) {
    val topTargets = players.filter { it.expectedFantasyPoints >= 7.3 && it.starterProb2026_27 >= 85 }
        .sortedByDescending { it.expectedFantasyPoints }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(topTargets) { player ->
            PlayerPreAuctionCard(player = player, badge = "TOP TARGET", badgeColor = EmeraldPrimary, onSelect = onSelect)
        }
    }
}

@Composable
fun SleepersTab(players: List<PlayerEntity>, onSelect: (PlayerEntity) -> Unit) {
    val sleepers = players.filter { it.fvm <= 40 && it.starterProb2026_27 >= 75 && it.expectedFantasyPoints >= 6.3 }
        .sortedByDescending { it.expectedFantasyPoints / it.fvm.coerceAtLeast(1) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(sleepers) { player ->
            PlayerPreAuctionCard(player = player, badge = "SCOMMESSA VALORE", badgeColor = GoldAccent, onSelect = onSelect)
        }
    }
}

@Composable
fun GoalkeepersAndPairsTab(players: List<PlayerEntity>, onSelect: (PlayerEntity) -> Unit) {
    val goalkeepers = players.filter { it.role == Role.P }.sortedByDescending { it.fvm }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Griglia Portieri & Clean Sheet Attesi",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
        }
        items(goalkeepers) { gk ->
            PlayerPreAuctionCard(player = gk, badge = "PORTIERE", badgeColor = RoleColorP, onSelect = onSelect)
        }
    }
}

@Composable
fun PlayerPreAuctionCard(
    player: PlayerEntity,
    badge: String,
    badgeColor: Color,
    onSelect: (PlayerEntity) -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(player) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = player.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = badgeColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = badge,
                                color = badgeColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = "${player.team} • Titolarità: ${player.starterProb2026_27}% • FM Attesa: ${String.format("%.2f", player.expectedFantasyPoints)}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "FVM: ${player.fvm}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = GoldAccent
                )
                Text(
                    text = "Qt: ${player.quotation}",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}
