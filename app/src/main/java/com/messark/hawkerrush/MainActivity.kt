package com.messark.hawkerrush

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.messark.hawkerrush.model.AppScreen
import com.messark.hawkerrush.ui.components.GameBoard
import com.messark.hawkerrush.ui.components.GameControlPanel
import com.messark.hawkerrush.ui.constants.LayoutConstants
import com.messark.hawkerrush.ui.theme.HawkerRushTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HawkerRushTheme {
                val gameState by viewModel.gameState.collectAsState()
                val availableStalls by viewModel.availableStalls.collectAsState()
                val haptic = LocalHapticFeedback.current

                LaunchedEffect(Unit) {
                    delay(1000)
                    viewModel.navigateTo(AppScreen.MAIN_MENU)
                }

                LaunchedEffect(Unit) {
                    viewModel.hapticEvents.collect {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = gameState.currentScreen,
                        transitionSpec = {
                            if (targetState == AppScreen.GAME) {
                                slideInVertically { it } + fadeIn() togetherWith
                                        slideOutVertically { -it } + fadeOut()
                            } else {
                                fadeIn(animationSpec = tween(500)) togetherWith
                                        fadeOut(animationSpec = tween(500))
                            }
                        },
                        label = "ScreenTransition"
                    ) { screen ->
                        when (screen) {
                            AppScreen.LOADING, AppScreen.MAIN_MENU -> {
                                MainMenu(
                                    isMainMenu = screen == AppScreen.MAIN_MENU,
                                    onNewGame = { viewModel.resetGame() }
                                )
                            }
                            AppScreen.GAME -> {
                                GameScreen(
                                    gameState = gameState,
                                    availableStalls = availableStalls,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainMenu(
    isMainMenu: Boolean,
    onNewGame: () -> Unit
) {
    val logoBias by animateFloatAsState(
        targetValue = if (isMainMenu) -0.7f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "LogoAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Logo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = BiasAlignment(0f, logoBias)
        ) {
            Image(
                painter = painterResource(id = R.drawable.loading_screen),
                contentDescription = "Hawker Rush Logo",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }

        // Buttons
        AnimatedVisibility(
            visible = isMainMenu,
            enter = fadeIn(animationSpec = tween(500, delayMillis = 500)) + slideInVertically { it / 2 },
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 100.dp)
            ) {
                Button(
                    onClick = onNewGame,
                    modifier = Modifier.width(200.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("New Game", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                }
                Button(
                    onClick = { /* Nothing for now */ },
                    modifier = Modifier.width(200.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("Options", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

@Composable
fun GameScreen(
    gameState: com.messark.hawkerrush.model.GameState,
    availableStalls: List<com.messark.hawkerrush.model.Stall>,
    viewModel: MainViewModel
) {
    var showExitDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LayoutConstants.BOARD_BORDER_SIZE)
            ) {
                GameBoard(
                    hexes = gameState.hexes,
                    enemies = gameState.enemies,
                    projectiles = gameState.projectiles,
                    puddles = gameState.puddles,
                    selectedBoardStall = gameState.selectedBoardStall,
                    onCellClick = { coord -> viewModel.onCellClick(coord) },
                    modifier = Modifier.weight(LayoutConstants.BOARD_HEIGHT_FRACTION)
                )
                GameControlPanel(
                    gold = gameState.gold,
                    health = gameState.health,
                    availableStalls = availableStalls,
                    selectedStall = gameState.selectedStallType,
                    selectedBoardStall = gameState.selectedBoardStall?.let { gameState.hexes[it]?.stall },
                    onStallSelected = { stall -> viewModel.selectStall(stall) },
                    onSellStall = { viewModel.sellStall() },
                    onUpgradeStall = { viewModel.upgradeStall() },
                    onCycleTargetMode = { viewModel.cycleTargetMode() },
                    onStartWave = { viewModel.startWave() },
                    waveActive = gameState.waveActive,
                    modifier = Modifier.weight(LayoutConstants.CONTROL_PANEL_HEIGHT_FRACTION)
                )
            }

            // Close Button
            IconButton(
                onClick = { showExitDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Pause") },
            text = { Text("What would you like to do?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    viewModel.navigateTo(AppScreen.MAIN_MENU)
                }) {
                    Text("Main Menu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Return to Game")
                }
            }
        )
    }
}
