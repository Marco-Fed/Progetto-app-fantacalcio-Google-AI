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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AuctionUiState
import com.example.ui.viewmodel.AuctionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RostersScreen(
    viewModel: AuctionViewModel,
    uiState: AuctionUiState,
    modifier: Modifier = Modifier
) {
    var selectedTeam by remember { mutableStateOf<TeamEntity?>(null) }
    var teamToEdit by remember { mutableStateOf<TeamEntity?>(null) }
    val config = uiState.leagueConfig

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Rose e Concorrenti Lega",
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
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.teams) { team ->
                val isExpanded = selectedTeam?.name == team.name

                // Get all purchased players for this team
                val teamPlayers = uiState.players.filter { it.isPurchased && it.purchasedByTeam == team.name }

                // Map event call order for chronological ordering within role
                val eventIndexMap = remember(uiState.auctionEvents) {
                    uiState.auctionEvents.mapIndexed { idx, ev -> ev.playerId to idx }.toMap()
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedTeam = if (isExpanded) null else team
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (team.isUserTeam) DarkSurfaceElevated else DarkSurface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = if (team.isUserTeam) androidx.compose.foundation.BorderStroke(1.5.dp, EmeraldPrimary) else null
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = team.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = if (team.isUserTeam) EmeraldPrimary else Color.White
                                    )
                                    if (team.isUserTeam) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            color = EmeraldContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "LA TUA ROSA",
                                                color = EmeraldPrimary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    // REQUEST 7: Editable team name icon button
                                    IconButton(
                                        onClick = { teamToEdit = team },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Modifica nome squadra",
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Max Offerta: ${team.maxAffordableBid(config)} cr • Slot liberi: ${team.totalRemainingSlots(config)}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${team.remainingCredits}/${team.initialCredits} cr",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = GoldAccent
                                )
                                Text(
                                    text = "Spesi: ${team.totalSpent} cr",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Role Breakdown Indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TeamRolePill("P", "${team.purchasedCountP}/${config.slotsP}", RoleColorP, Modifier.weight(1f))
                            TeamRolePill("D", "${team.purchasedCountD}/${config.slotsD}", RoleColorD, Modifier.weight(1f))
                            TeamRolePill("C", "${team.purchasedCountC}/${config.slotsC}", RoleColorC, Modifier.weight(1f))
                            TeamRolePill("A", "${team.purchasedCountA}/${config.slotsA}", RoleColorA, Modifier.weight(1f))
                        }

                        // REQUEST 9: EXPANDED ROSTER - SORTED FIRST BY ROLE (P, D, C, A) THEN BY CALL ORDER
                        if (isExpanded) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = DarkSurfaceVariant)
                            Spacer(Modifier.height(8.dp))

                            if (teamPlayers.isNotEmpty()) {
                                Text(
                                    text = "Giocatori in Rosa (${teamPlayers.size}) ordinati per Ruolo e Chiamata:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                                Spacer(Modifier.height(6.dp))

                                val rolesOrder = listOf(Role.P, Role.D, Role.C, Role.A)

                                rolesOrder.forEach { role ->
                                    // Players in this role, sorted by call order (earliest event first)
                                    val playersInRole = teamPlayers
                                        .filter { it.role == role }
                                        .sortedBy { eventIndexMap[it.id] ?: Int.MAX_VALUE }

                                    if (playersInRole.isNotEmpty()) {
                                        val roleColor = when (role) {
                                            Role.P -> RoleColorP
                                            Role.D -> RoleColorD
                                            Role.C -> RoleColorC
                                            Role.A -> RoleColorA
                                        }

                                        Surface(
                                            color = DarkSurfaceElevated,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(bottom = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "${role.displayName} (${playersInRole.size})",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = roleColor
                                                    )
                                                }

                                                playersInRole.forEachIndexed { idx, player ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 3.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = "#${idx + 1}",
                                                                fontSize = 10.sp,
                                                                color = TextMuted,
                                                                modifier = Modifier.width(20.dp)
                                                            )
                                                            Text(
                                                                text = "${player.name} (${player.team})",
                                                                fontSize = 12.sp,
                                                                color = TextPrimary,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                        Text(
                                                            text = "${player.purchasePrice ?: 0} cr",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = GoldAccent
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Nessun calciatore acquistato finora.",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // REQUEST 7: Edit Team Name Modal Dialog
    if (teamToEdit != null) {
        var newTeamName by remember(teamToEdit) { mutableStateOf(teamToEdit!!.name) }
        var nameError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { teamToEdit = null },
            title = {
                Text("Modifica Nome Squadra", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Inserisci il nuovo nome per '${teamToEdit?.name}':",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = newTeamName,
                        onValueChange = {
                            newTeamName = it
                            nameError = null
                        },
                        label = { Text("Nome Squadra") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_team_name_input"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = DarkSurfaceVariant
                        ),
                        singleLine = true
                    )
                    nameError?.let {
                        Text(it, color = DecisionPass, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newTeamName.trim()
                        if (trimmed.isBlank()) {
                            nameError = "Il nome non può essere vuoto"
                            return@Button
                        }
                        if (trimmed != teamToEdit!!.name && uiState.teams.any { it.name.equals(trimmed, ignoreCase = true) }) {
                            nameError = "Esiste già una squadra con questo nome"
                            return@Button
                        }
                        viewModel.updateTeamName(teamToEdit!!.name, trimmed)
                        teamToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier.testTag("save_team_name_button")
                ) {
                    Text("Salva")
                }
            },
            dismissButton = {
                TextButton(onClick = { teamToEdit = null }) {
                    Text("Annulla", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun TeamRolePill(
    roleCode: String,
    statusText: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(roleCode, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = color)
            Text(statusText, fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}
