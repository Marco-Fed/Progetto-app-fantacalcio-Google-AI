package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.*
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AuctionViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Live : Screen("live", "Asta Live", Icons.Default.Gavel)
    object PreAuction : Screen("pre_auction", "Pre-Asta", Icons.Default.TrendingUp)
    object Rosters : Screen("rosters", "Rose", Icons.Default.Groups)
    object Listone : Screen("listone", "Listone", Icons.Default.FormatListBulleted)
    object Setup : Screen("setup", "Impostazioni", Icons.Default.Settings)
}

@Composable
fun MainAppNavigation(
    viewModel: AuctionViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    val screens = listOf(
        Screen.Live,
        Screen.PreAuction,
        Screen.Rosters,
        Screen.Listone,
        Screen.Setup
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurfaceElevated,
                tonalElevation = 8.dp
            ) {
                screens.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                screen.icon,
                                contentDescription = screen.title,
                                tint = if (selected) EmeraldPrimary else TextSecondary
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                color = if (selected) EmeraldPrimary else TextSecondary
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = EmeraldPrimary.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Live.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Live.route) {
                LiveAuctionScreen(viewModel = viewModel, uiState = uiState)
            }
            composable(Screen.PreAuction.route) {
                PreAuctionScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onNavigateToLive = { navController.navigate(Screen.Live.route) }
                )
            }
            composable(Screen.Rosters.route) {
                RostersScreen(viewModel = viewModel, uiState = uiState)
            }
            composable(Screen.Listone.route) {
                ListoneScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onNavigateToLive = { navController.navigate(Screen.Live.route) }
                )
            }
            composable(Screen.Setup.route) {
                SetupScreen(viewModel = viewModel, uiState = uiState)
            }
        }
    }
}
