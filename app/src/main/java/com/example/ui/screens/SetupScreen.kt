package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AuctionUiState
import com.example.ui.viewmodel.AuctionViewModel
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: AuctionViewModel,
    uiState: AuctionUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var showResetListoneDialog by remember { mutableStateOf(false) }
    var showCsvPasteDialog by remember { mutableStateOf(false) }
    var showLegendDialog by remember { mutableStateOf(false) }
    var showInjuriesEditorDialog by remember { mutableStateOf(false) }

    var initialCreditsText by remember(uiState.leagueConfig) { mutableStateOf(uiState.leagueConfig.initialCredits.toString()) }
    var numTeamsText by remember(uiState.leagueConfig) { mutableStateOf(uiState.leagueConfig.numTeams.toString()) }
    var defenseModEnabled by remember(uiState.leagueConfig) { mutableStateOf(uiState.leagueConfig.defenseModifierEnabled) }

    // File picker for Excel (.xlsx) or CSV on device
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    viewModel.importListoneStream(inputStream)
                }
            } catch (e: Exception) {
                // Handled in VM
            }
        }
    }

    // File picker for User Injury Text file on device
    val injuryFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val textContent = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (textContent.isNotBlank()) {
                    viewModel.updateInjuriesInputText(textContent)
                    viewModel.parseAndApplyIndisponibili(textContent)
                }
            } catch (e: Exception) {
                // Handled in VM
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Impostazioni & Dati Asta",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // PROMINENT "COME VENGONO CALCOLATI I VALORI?" CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLegendDialog = true },
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldAccent)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = GoldContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = GoldAccent)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Come Vengono Calcolati i Valori?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = GoldAccent
                                )
                                Text(
                                    text = "Legenda completa di metriche, formule e logiche decisionali.",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = GoldAccent)
                    }
                }
            }

            // EXCEL & CSV IMPORT & PASTE + RESET LISTONE
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Caricamento & Gestione Listone",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = EmeraldPrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Excel .xlsx / CSV",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Carica il file Excel (.xlsx), CSV o incolla il testo del listone (con colonne Ruolo, Nome, Squadra, Qt, FVM). A ogni importazione il sistema associa in automatico le statistiche storiche avanzate (FBref/Kaggle 2024-25 & 2025-26) con matching intelligente anti-omonimia, aggiorna gli infortuni e ricalibra istantaneamente il motore di pricing.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Spacer(Modifier.height(14.dp))

                        // Device File Picker & Paste Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { filePickerLauncher.launch("*/*") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_excel_csv_file_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("📁 Carica Excel / CSV", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            OutlinedButton(
                                onClick = { showCsvPasteDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("paste_csv_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldPrimary)
                                Spacer(Modifier.width(6.dp))
                                Text("📋 Incolla da Excel/Testo", fontSize = 13.sp)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Reset Listone to Default Button
                        OutlinedButton(
                            onClick = { showResetListoneDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_listone_to_default_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAccent),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = GoldAccent)
                            Spacer(Modifier.width(6.dp))
                            Text("🔄 Ripristina Listone Predefinito (516 Calciatori)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // INJURIES & UNAVAILABLE PLAYERS VIA LLM INPUT CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🏥 Infortuni & Squalifiche (Analisi LLM)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Il modello LLM analizza il testo fornito dall'utente (o caricato da file .txt) per estrarre diagnosi, tempi di recupero e squalifiche per ciascuna squadra di Serie A.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Spacer(Modifier.height(14.dp))

                        // File picker & Paste/Edit Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { injuryFilePickerLauncher.launch("*/*") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_injury_file_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = GoldAccent)
                                Spacer(Modifier.width(6.dp))
                                Text("📁 Carica File Testo", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { showInjuriesEditorDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("edit_injuries_text_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp), tint = GoldAccent)
                                Spacer(Modifier.width(6.dp))
                                Text("✏️ Modifica / Incolla", fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Primary Trigger: Parse & Apply via LLM
                        Button(
                            onClick = { viewModel.parseAndApplyIndisponibili() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("trigger_llm_injuries_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                        ) {
                            if (uiState.isSyncingIndisponibili) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Analisi LLM Infortuni in corso...", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                                Spacer(Modifier.width(8.dp))
                                Text("🤖 Analizza con LLM & Aggiorna", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // LEAGUE CONFIGURATION CARD (DYNAMIC PARAMETERS - REQUEST 6)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Parametri della Lega (Dinamici)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Modifica crediti o regole: i budget di tutte le squadre si ricalcoleranno dinamicamente.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = initialCreditsText,
                                onValueChange = { initialCreditsText = it },
                                label = { Text("Budget Iniziale (cr)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldPrimary,
                                    unfocusedBorderColor = DarkSurfaceVariant
                                ),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = numTeamsText,
                                onValueChange = { numTeamsText = it },
                                label = { Text("Numero Squadre") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldPrimary,
                                    unfocusedBorderColor = DarkSurfaceVariant
                                ),
                                singleLine = true
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Modificatore Difesa", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text("Premia difensori con media voto elevata", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = defenseModEnabled,
                                onCheckedChange = { defenseModEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrimary)
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val credits = initialCreditsText.toIntOrNull() ?: 500
                                val teams = numTeamsText.toIntOrNull() ?: 8
                                viewModel.saveLeagueConfig(
                                    uiState.leagueConfig.copy(
                                        initialCredits = credits,
                                        numTeams = teams,
                                        defenseModifierEnabled = defenseModEnabled
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_config_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Salva & Ricalcola Parametri Lega")
                        }
                    }
                }
            }

            // RESET AUCTION CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Reset e Azzeramento",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Azzera tutti gli acquisti, ripristina i crediti iniziali e svuota la cronologia dell'asta.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = { showResetDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_auction_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DecisionPass)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Resetta Completamente l'Asta")
                        }
                    }
                }
            }
        }
    }

    // RESET LISTONE CONFIRMATION MODAL
    if (showResetListoneDialog) {
        AlertDialog(
            onDismissRequest = { showResetListoneDialog = false },
            title = { Text("Ripristinare il Listone Predefinito?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    "Il listone verrà reimpostato ai 516 calciatori ufficiali di Serie A con le statistiche storiche avanzate (FBref/Kaggle 2024-25 e 2025-26) e le quotazioni predefinite.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetListoneToDefault()
                        showResetListoneDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                ) {
                    Text("Ripristina Listone", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetListoneDialog = false }) {
                    Text("Annulla", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // CSV / EXCEL PASTE MODAL
    if (showCsvPasteDialog) {
        CsvPasteImportDialog(
            onDismiss = { showCsvPasteDialog = false },
            onImport = { text ->
                viewModel.importListoneText(text)
                showCsvPasteDialog = false
            }
        )
    }

    // INJURIES TEXT EDITOR MODAL
    if (showInjuriesEditorDialog) {
        InjuriesTextEditorDialog(
            initialText = uiState.injuriesInputText,
            onDismiss = { showInjuriesEditorDialog = false },
            onSaveAndAnalyze = { newText ->
                viewModel.updateInjuriesInputText(newText)
                viewModel.parseAndApplyIndisponibili(newText)
                showInjuriesEditorDialog = false
            },
            onResetToDefault = {
                viewModel.resetInjuriesInputTextToDefault()
            }
        )
    }

    // RESET CONFIRMATION MODAL
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Sei sicuro di voler resettare l'asta?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("Tutti i crediti spesi, i giocatori assegnati e lo storico degli eventi verranno azzerati.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAuction()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DecisionPass)
                ) {
                    Text("Conferma Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Annulla", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // COMPREHENSIVE LEGENDA MODAL
    if (showLegendDialog) {
        HowValuesAreCalculatedDialog(onDismiss = { showLegendDialog = false })
    }
}

@Composable
fun HowValuesAreCalculatedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Come Vengono Calcolati i Valori?", fontWeight = FontWeight.Bold, color = GoldAccent, fontSize = 18.sp)
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    LegendaItem(
                        title = "1. FantaValore & Valore Teorico (0-100)",
                        description = "Indice quantitativo pesato su FantaMedia attesa, bonus piazzati/rigori, e affidabilità titolare. Scala da 10 a 99 punti."
                    )
                }
                item {
                    LegendaItem(
                        title = "2. Prezzo Ottimale & Massimo Consigliato",
                        description = "Intervallo di prezzo che massimizza il rendimento per credito investito. Il Prezzo Massimo rappresenta il limite oltre il quale passare e virare su alternative è matematicamente superiore."
                    )
                }
                item {
                    LegendaItem(
                        title = "3. Titolarità Prevista 2026-27 vs Storica (3 Anni)",
                        description = "Previsione statistica per la stagione corrente basata su continuità tattica, gerarchie e ballottaggi. Affiancata dallo storico delle stagioni 2025-26, 2024-25 e 2023-24 (ponderate 50% / 35% / 15%)."
                    )
                }
                item {
                    LegendaItem(
                        title = "4. Valore Marginale & Replacement Value",
                        description = "Il Valore Marginale misura il guadagno netto rispetto al 'Replacement Player' (la migliore alternativa accessibile nel ruolo). Nelle fasi EARLY ha peso moderato, mentre in MID e LATE guida le scelte ad alta efficienza."
                    )
                }
                item {
                    LegendaItem(
                        title = "5. Indice di Scarsità & Fasi Ruolo (EARLY, MID, LATE)",
                        description = "Misura il rapporto tra titolari di qualità rimasti e slot vuoti tra tutte le squadre della lega. Quando la scarsità sale, il motore aumenta la protezione sui titolari residui."
                    )
                }
                item {
                    LegendaItem(
                        title = "6. Simulazione Monte Carlo (Live & Deep)",
                        description = "Algoritmo che simula 100+ completamenti d'asta futuri valutando la probabilità di vittoria e il punteggio totale finale se si acquista il giocatore o se si passa."
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)) {
                Text("Ho Capito")
            }
        },
        containerColor = DarkSurface
    )
}

@Composable
fun LegendaItem(title: String, description: String) {
    Surface(
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EmeraldPrimary)
            Spacer(Modifier.height(4.dp))
            Text(description, fontSize = 12.sp, color = TextPrimary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvPasteImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var csvText by remember {
        mutableStateOf(
            "R;Nome;Squadra;Qt.A;FVM\n" +
            "A;Lautaro Martinez;Inter;38;367\n" +
            "A;Malen Donyell;Roma;35;414\n" +
            "C;Paz Nico;Como;22;247\n" +
            "D;Dimarco Federico;Inter;23;253\n" +
            "P;Svilar Mile;Roma;15;75"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Incolla Listone da Excel o CSV", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Incolla qui le righe (tabulari da Excel o separate da punto e virgola/virgola):", fontSize = 12.sp, color = TextSecondary)
                OutlinedTextField(
                    value = csvText,
                    onValueChange = { csvText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("csv_input_field"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = DarkSurfaceVariant
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(csvText) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("confirm_csv_import_button")
            ) {
                Text("Carica Giocatori")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InjuriesTextEditorDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSaveAndAnalyze: (String) -> Unit,
    onResetToDefault: () -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Situazione Infortuni & Squalifiche",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Modifica o incolla il testo con le squadre e gli infortunati/squalificati. L'LLM estrarrà automaticamente i singoli giocatori e le diagnosi.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .testTag("injuries_text_input_field"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            onResetToDefault()
                            text = com.example.data.remote.InjuryParserService.DEFAULT_INJURIES_TEXT
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = GoldAccent)
                        Spacer(Modifier.width(4.dp))
                        Text("Ripristina Predefinito", fontSize = 12.sp, color = GoldAccent)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveAndAnalyze(text) },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                modifier = Modifier.testTag("confirm_injuries_analyze_button")
            ) {
                Text("Analizza con LLM & Aggiorna", color = Color.Black, fontWeight = FontWeight.Bold)
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

