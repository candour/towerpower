package com.messark.hawkerrush

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import android.view.animation.OvershootInterpolator
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.messark.hawkerrush.model.AppScreen
import com.messark.hawkerrush.model.HighScore
import com.messark.hawkerrush.ui.components.GameBoard
import com.messark.hawkerrush.ui.components.GameControlPanel
import com.messark.hawkerrush.ui.constants.LayoutConstants
import com.messark.hawkerrush.utils.SettingsRepository
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
                val logoVisible by viewModel.logoVisible.collectAsState()
                val settingsRepository = remember { SettingsRepository(application) }
                val settings by settingsRepository.settingsFlow.collectAsState(initial = com.messark.hawkerrush.model.Settings())
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
                    val currentScreen = gameState.currentScreen
                    val screenGroup = if (currentScreen == AppScreen.GAME) "GAME" else "MENU"

                    AnimatedContent(
                        targetState = screenGroup,
                        transitionSpec = {
                            if (targetState == "GAME") {
                                slideInVertically { it } + fadeIn() togetherWith
                                        slideOutVertically { -it } + fadeOut()
                            } else {
                                slideInVertically { -it } + fadeIn() togetherWith
                                        slideOutVertically { it } + fadeOut()
                            }
                        },
                        label = "ScreenTransition"
                    ) { group ->
                        when (group) {
                            "MENU" -> {
                                MainMenu(
                                    isMainMenu = currentScreen == AppScreen.MAIN_MENU,
                                    logoVisible = logoVisible,
                                    onLogoAnimationFinished = { viewModel.hideLogo() },
                                    onNewGame = { viewModel.resetGame() },
                                    onResumeGame = { viewModel.resumeGame() },
                                    hasSavedGame = viewModel.hasSavedGame(),
                                    highScores = settings.highScores
                                )
                            }
                            "GAME" -> {
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
    logoVisible: Boolean,
    onLogoAnimationFinished: () -> Unit,
    onNewGame: () -> Unit,
    onResumeGame: () -> Unit,
    hasSavedGame: Boolean,
    highScores: List<HighScore>
) {
    var showOverwriteWarning by remember { mutableStateOf(false) }

    var targetBias by remember { mutableStateOf(BiasAlignment(0f, 0f)) }

    LaunchedEffect(isMainMenu) {
        if (isMainMenu) {
            val directions = listOf(
                BiasAlignment(-3f, 0f),  // Left
                BiasAlignment(3f, 0f),   // Right
                BiasAlignment(0f, -3f),  // Top
                BiasAlignment(0f, 3f),   // Bottom
                BiasAlignment(-3f, -3f), // Top-Left
                BiasAlignment(3f, -3f),  // Top-Right
                BiasAlignment(-3f, 3f),  // Bottom-Left
                BiasAlignment(3f, 3f)    // Bottom-Right
            )
            targetBias = directions.random()
        } else {
            targetBias = BiasAlignment(0f, 0f)
        }
    }

    val logoHorizontalBias by animateFloatAsState(
        targetValue = targetBias.horizontalBias,
        animationSpec = tween(durationMillis = 1000),
        label = "LogoHorizontalAnimation",
        finishedListener = { if (isMainMenu) onLogoAnimationFinished() }
    )
    val logoVerticalBias by animateFloatAsState(
        targetValue = targetBias.verticalBias,
        animationSpec = tween(durationMillis = 1000),
        label = "LogoVerticalAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Background Image
        Image(
            painter = painterResource(id = R.drawable.hawkersepia),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Top Image (hawkercolour)
        AnimatedVisibility(
            visible = isMainMenu,
            enter = fadeIn(animationSpec = tween(1000)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.hawkercolour),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(0.9f),
                contentScale = ContentScale.FillWidth
            )
        }

        // Logo
        if (logoVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = BiasAlignment(logoHorizontalBias, logoVerticalBias)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.loading_screen),
                    contentDescription = "Hawker Rush Logo",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
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
                modifier = Modifier.padding(top = 120.dp)
            ) {
                if (hasSavedGame) {
                    Button(
                        onClick = onResumeGame,
                        modifier = Modifier.width(200.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        Text("Resume Game", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    }
                }

                Button(
                    onClick = {
                        if (hasSavedGame) {
                            showOverwriteWarning = true
                        } else {
                            onNewGame()
                        }
                    },
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

                if (highScores.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Top 5 High Scores", color = Color.Yellow, style = MaterialTheme.typography.titleLarge)
                    highScores.forEach { entry ->
                        Row(
                            modifier = Modifier.width(300.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Wave ${entry.wave}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Text(text = "${entry.score}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Text(text = entry.date.split("T")[0], color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showOverwriteWarning) {
        AlertDialog(
            onDismissRequest = { showOverwriteWarning = false },
            title = { Text("Overwrite Save?") },
            text = { Text("Starting a new game will overwrite your current progress. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    showOverwriteWarning = false
                    onNewGame()
                }) {
                    Text("Overwrite")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun GameOverOverlay(
    score: Int,
    wave: Int,
    onNewGame: () -> Unit,
    onMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(enabled = true, onClick = {}), // Prevent clicks through to board
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color.DarkGray,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            modifier = Modifier.padding(32.dp).widthIn(max = 400.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "GAME OVER",
                    color = Color.Red,
                    style = MaterialTheme.typography.headlineLarge
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Final Score", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    Text(text = "$score", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Wave Reached", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    Text(text = "$wave", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onNewGame,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("New Game", color = Color.White)
                }

                Button(
                    onClick = onMainMenu,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text("Main Menu", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun BossWaveOverlay(show: Boolean) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(animationSpec = tween(300)) + scaleIn(animationSpec = tween(300, easing = OvershootInterpolator().let { { t: Float -> it.getInterpolation(t) } })),
        exit = fadeOut(animationSpec = tween(500)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "BossWavePulsing")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "Scale"
            )
            val color by infiniteTransition.animateColor(
                initialValue = Color.Red,
                targetValue = Color.Yellow,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "Color"
            )

            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(4.dp, color),
                modifier = Modifier
                    .padding(32.dp)
                    .scale(scale)
            ) {
                Text(
                    text = "BOSS WAVE",
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
                )
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
                    visualEffects = gameState.visualEffects,
                    selectedBoardStall = gameState.selectedBoardStall,
                    onCellClick = { coord -> viewModel.onCellClick(coord) },
                    modifier = Modifier.weight(LayoutConstants.BOARD_HEIGHT_FRACTION)
                )
                GameControlPanel(
                    gold = gameState.gold,
                    health = gameState.health,
                    score = gameState.score,
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

            val showBossWave = gameState.isBossWave && (System.currentTimeMillis() - gameState.bossWaveTriggerTimeMs < 2000)
            BossWaveOverlay(show = showBossWave)

            if (gameState.health <= 0) {
                GameOverOverlay(
                    score = gameState.score,
                    wave = gameState.currentWave,
                    onNewGame = { viewModel.resetGame() },
                    onMainMenu = { viewModel.navigateTo(AppScreen.MAIN_MENU) }
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
