package com.messark.hawkerrush

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
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
                var showLoading by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    delay(1000)
                    showLoading = false
                }

                LaunchedEffect(Unit) {
                    viewModel.hapticEvents.collect {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
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

                    AnimatedVisibility(
                        visible = showLoading,
                        exit = fadeOut(animationSpec = tween(durationMillis = 500))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {}
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.loading_screen),
                                contentDescription = "Loading Screen",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }
            }
        }
    }
}
