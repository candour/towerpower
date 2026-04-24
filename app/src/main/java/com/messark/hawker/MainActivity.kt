package com.messark.hawker

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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.messark.hawker.model.AppScreen
import com.messark.hawker.model.HighScore
import com.messark.hawker.model.Settings
import com.messark.hawker.ui.components.GameBoard
import com.messark.hawker.ui.components.GameControlPanel
import com.messark.hawker.ui.components.TutorialOverlay
import com.messark.hawker.ui.constants.LayoutConstants
import com.messark.hawker.ui.constants.SpriteConstants
import com.messark.hawker.utils.SettingsRepository
import com.messark.hawker.ui.theme.HawkerRushTheme
import kotlinx.coroutines.delay

@Composable
fun SpriteButton(
    normalRect: androidx.compose.ui.unit.IntRect,
    pressedRect: androidx.compose.ui.unit.IntRect? = null,
    onClick: () -> Unit,
    onTriggerHaptic: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val context = LocalContext.current
    val disabledColorFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }
    val bitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.buttons).asImageBitmap()
    }

    val currentRect = if (enabled && isPressed && pressedRect != null) pressedRect else normalRect

    Box(
        modifier = modifier
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    onTriggerHaptic()
                    onClick()
                }
            )
    ) {
        // Using a Canvas to draw the specific part of the bitmap
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawImage(
                image = bitmap,
                srcOffset = IntOffset(currentRect.left, currentRect.top),
                srcSize = IntSize(currentRect.width, currentRect.height),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                colorFilter = if (!enabled) disabledColorFilter else null
            )
        }
    }
}

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
                val settings by settingsRepository.settingsFlow.collectAsState(initial = com.messark.hawker.model.Settings())
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
                        targetState = currentScreen,
                        transitionSpec = {
                            if (targetState == AppScreen.GAME) {
                                slideInVertically { it } + fadeIn() togetherWith
                                        slideOutVertically { -it } + fadeOut()
                            } else if (initialState == AppScreen.GAME) {
                                slideInVertically { -it } + fadeIn() togetherWith
                                        slideOutVertically { it } + fadeOut()
                            } else {
                                fadeIn() togetherWith fadeOut()
                            }
                        },
                        label = "ScreenTransition"
                    ) { screen ->
                        when (screen) {
                            AppScreen.LOADING, AppScreen.MAIN_MENU -> {
                                MainMenu(
                                    isMainMenu = screen == AppScreen.MAIN_MENU,
                                    logoVisible = logoVisible,
                                    onLogoAnimationFinished = { viewModel.hideLogo() },
                                    onNewGame = { viewModel.resetGame() },
                                    onResumeGame = { viewModel.resumeGame() },
                                    onOptions = { viewModel.navigateTo(AppScreen.OPTIONS) },
                                    onTriggerHaptic = { viewModel.triggerHaptic() },
                                    hasSavedGame = viewModel.hasSavedGame(),
                                    highScores = settings.highScores
                                )
                            }
                            AppScreen.OPTIONS -> {
                                OptionsScreen(
                                    hapticEnabled = settings.hapticEnabled,
                                    showTutorials = settings.showTutorials,
                                    onHapticToggle = { viewModel.updateHapticSetting(it) },
                                    onTutorialsToggle = { viewModel.updateTutorialsSetting(it) },
                                    onSave = { viewModel.navigateTo(AppScreen.MAIN_MENU) },
                                    onTriggerHaptic = { viewModel.triggerHaptic() }
                                )
                            }
                            AppScreen.GAME -> {
                                GameScreen(
                                    gameState = gameState,
                                    availableStalls = availableStalls,
                                    viewModel = viewModel,
                                    showTutorialsSetting = settings.showTutorials
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
fun OptionsScreen(
    hapticEnabled: Boolean,
    showTutorials: Boolean,
    onHapticToggle: (Boolean) -> Unit,
    onTutorialsToggle: (Boolean) -> Unit,
    onSave: () -> Unit,
    onTriggerHaptic: () -> Unit
) {
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

        Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(32.dp).widthIn(max = 400.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "OPTIONS",
                    color = Color.Yellow,
                    style = MaterialTheme.typography.headlineLarge
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Haptic Feedback",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = hapticEnabled,
                        onCheckedChange = {
                            onHapticToggle(it)
                            onTriggerHaptic()
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show Tutorials",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = showTutorials,
                        onCheckedChange = {
                            onTutorialsToggle(it)
                            onTriggerHaptic()
                        }
                    )
                }

                SpriteButton(
                    normalRect = SpriteConstants.BTN_SAVE_RECT,
                    pressedRect = null,
                    onClick = {
                        onTriggerHaptic()
                        onSave()
                    },
                    onTriggerHaptic = onTriggerHaptic,
                    modifier = Modifier
                        .width(210.dp)
                        .height(57.dp)
                )
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
    onOptions: () -> Unit,
    onTriggerHaptic: () -> Unit,
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
                    SpriteButton(
                        normalRect = SpriteConstants.BTN_RESUME_RECT,
                        pressedRect = SpriteConstants.BTN_RESUME_CLICK_RECT,
                        onClick = onResumeGame,
                        onTriggerHaptic = onTriggerHaptic,
                        modifier = Modifier.width(210.dp).height(57.dp)
                    )
                }

                SpriteButton(
                    normalRect = SpriteConstants.BTN_NEWGAME_RECT,
                    pressedRect = SpriteConstants.BTN_NEWGAME_CLICK_RECT,
                    onClick = {
                        if (hasSavedGame) {
                            showOverwriteWarning = true
                        } else {
                            onNewGame()
                        }
                    },
                    onTriggerHaptic = onTriggerHaptic,
                    modifier = Modifier.width(210.dp).height(57.dp)
                )

                SpriteButton(
                    normalRect = SpriteConstants.BTN_OPTIONS_RECT,
                    pressedRect = SpriteConstants.BTN_OPTIONS_CLICK_RECT,
                    onClick = onOptions,
                    onTriggerHaptic = onTriggerHaptic,
                    modifier = Modifier.width(210.dp).height(57.dp)
                )

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
                SpriteButton(
                    normalRect = SpriteConstants.BTN_NEWGAME_RECT,
                    pressedRect = SpriteConstants.BTN_NEWGAME_CLICK_RECT,
                    onClick = {
                        showOverwriteWarning = false
                        onNewGame()
                    },
                    onTriggerHaptic = onTriggerHaptic,
                    modifier = Modifier.width(100.dp).height(27.dp)
                )
            },
            dismissButton = {
                SpriteButton(
                    normalRect = SpriteConstants.BTN_CANCEL_RECT,
                    pressedRect = SpriteConstants.BTN_CANCEL_CLICK_RECT,
                    onClick = { showOverwriteWarning = false },
                    onTriggerHaptic = onTriggerHaptic,
                    modifier = Modifier.width(100.dp).height(27.dp)
                )
            }
        )
    }
}

@Composable
fun GameOverOverlay(
    score: Int,
    wave: Int,
    onNewGame: () -> Unit,
    onMainMenu: () -> Unit,
    onTriggerHaptic: () -> Unit
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

                SpriteButton(
                    normalRect = SpriteConstants.BTN_NEWGAME_RECT,
                    pressedRect = SpriteConstants.BTN_NEWGAME_CLICK_RECT,
                    onClick = onNewGame,
                    onTriggerHaptic = onTriggerHaptic,
                    modifier = Modifier.fillMaxWidth().height(95.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                SpriteButton(
                    normalRect = SpriteConstants.BTN_MAINMENU_RECT,
                    pressedRect = null,
                    onClick = {
                        onTriggerHaptic()
                        onMainMenu()
                    },
                    onTriggerHaptic = onTriggerHaptic,
                    modifier = Modifier.fillMaxWidth().height(95.dp)
                )
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
    gameState: com.messark.hawker.model.GameState,
    availableStalls: List<com.messark.hawker.model.Stall>,
    viewModel: MainViewModel,
    showTutorialsSetting: Boolean
) {
    var showExitDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        val isScreenOnAlreadySet = window?.let {
            (it.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        } ?: false

        if (!isScreenOnAlreadySet) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            if (!isScreenOnAlreadySet) {
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

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
                    gold = gameState.gold,
                    onCellClick = { coord -> viewModel.onCellClick(coord) },
                    modifier = Modifier.weight(LayoutConstants.BOARD_HEIGHT_FRACTION)
                )
                GameControlPanel(
                    gold = gameState.gold,
                    health = gameState.health,
                    score = gameState.score,
                    currentWave = gameState.currentWave,
                    availableStalls = availableStalls,
                    selectedStall = gameState.selectedStallType,
                    selectedBoardStall = gameState.selectedBoardStall?.let { gameState.hexes[it]?.stall },
                    onStallSelected = { stall -> viewModel.selectStall(stall) },
                    onSellStall = { viewModel.sellStall() },
                    onUpgradeStall = { viewModel.upgradeStall() },
                    onCycleTargetMode = { viewModel.cycleTargetMode() },
                    onStartWave = { viewModel.startWave() },
                    onShowStallTutorial = { viewModel.showStallTutorial(it) },
                    onTriggerHaptic = { viewModel.triggerHaptic() },
                    waveActive = gameState.waveActive,
                    modifier = Modifier.weight(LayoutConstants.CONTROL_PANEL_HEIGHT_FRACTION)
                )
            }

            val showBossWave = gameState.isBossWave && (System.currentTimeMillis() - gameState.bossWaveTriggerTimeMs < 2000)
            BossWaveOverlay(show = showBossWave)

            gameState.activeTutorial?.let { tutorial ->
                TutorialOverlay(
                    tutorialData = tutorial,
                    showTutorialsSetting = showTutorialsSetting,
                    onToggleTutorialsSetting = { viewModel.updateTutorialsSetting(it) },
                    onDismiss = { viewModel.dismissTutorial() },
                    onTriggerHaptic = { viewModel.triggerHaptic() }
                )
            }

            if (gameState.health <= 0) {
                GameOverOverlay(
                    score = gameState.score,
                    wave = gameState.currentWave,
                    onNewGame = { viewModel.resetGame() },
                    onMainMenu = { viewModel.navigateTo(AppScreen.MAIN_MENU) },
                    onTriggerHaptic = { viewModel.triggerHaptic() }
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
                SpriteButton(
                    normalRect = SpriteConstants.BTN_MAINMENU_RECT,
                    pressedRect = null,
                    onClick = {
                        showExitDialog = false
                        viewModel.navigateTo(AppScreen.MAIN_MENU)
                    },
                    onTriggerHaptic = { viewModel.triggerHaptic() },
                    modifier = Modifier.width(120.dp).height(33.dp)
                )
            },
            dismissButton = {
                SpriteButton(
                    normalRect = SpriteConstants.BTN_RESUME_RECT,
                    pressedRect = SpriteConstants.BTN_RESUME_CLICK_RECT,
                    onClick = { showExitDialog = false },
                    onTriggerHaptic = { viewModel.triggerHaptic() },
                    modifier = Modifier.width(120.dp).height(33.dp)
                )
            }
        )
    }
}
