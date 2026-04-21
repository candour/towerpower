package com.messark.hawkerrush.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messark.hawkerrush.MainActivity
import com.messark.hawkerrush.SpriteButton
import com.messark.hawkerrush.R
import com.messark.hawkerrush.model.Stall
import com.messark.hawkerrush.model.StallType
import com.messark.hawkerrush.ui.constants.SpriteConstants

@Composable
fun GameControlPanel(
    gold: Int,
    health: Int,
    score: Int,
    currentWave: Int,
    availableStalls: List<Stall>,
    selectedStall: Stall?,
    selectedBoardStall: Stall?,
    onStallSelected: (Stall) -> Unit,
    onSellStall: () -> Unit,
    onUpgradeStall: () -> Unit,
    onCycleTargetMode: () -> Unit,
    onStartWave: () -> Unit,
    onShowStallTutorial: (StallType) -> Unit,
    onTriggerHaptic: () -> Unit,
    waveActive: Boolean,
    modifier: Modifier = Modifier
) {
    val spriteSheet = ImageBitmap.imageResource(id = R.drawable.sprite_sheet)
    val stallsSheet = ImageBitmap.imageResource(id = R.drawable.stalls)
    val context = LocalContext.current
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: Exception) {
        "1.0"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(text = "🪑 Tables: $health", color = Color.Red, fontSize = 16.sp)
            Text(text = "💰 Budget: $gold", color = Color.Yellow, fontSize = 16.sp)
        }

        if (selectedBoardStall != null) {
            val baseStall = availableStalls.find { it.stallType == selectedBoardStall.stallType } ?: selectedBoardStall
            val upgradeCost = selectedBoardStall.getUpgradeCost()

            StallConsole(
                stall = selectedBoardStall,
                baseStall = baseStall,
                onSell = onSellStall,
                onUpgrade = onUpgradeStall,
                onCycleTarget = onCycleTargetMode,
                onTriggerHaptic = onTriggerHaptic,
                canAffordUpgrade = gold >= upgradeCost,
                upgradeCost = upgradeCost,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                if (selectedStall != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = selectedStall.description, color = Color.White, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .border(1.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                                .clickable {
                                    onShowStallTutorial(selectedStall.stallType)
                                    onTriggerHaptic()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "?", color = Color.White, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                } else if (score > 0 || currentWave > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentWave > 0) {
                            Text(text = "WAVE: $currentWave", color = Color.White, fontSize = 12.sp)
                        }
                        if (score > 0 && currentWave > 0) {
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        if (score > 0) {
                            Text(text = "SCORE: $score", color = Color.White, fontSize = 12.sp)
                        }
                    }
                } else {
                    Text(text = "STALL SHOP", color = Color.White, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "v$versionName", color = Color.Gray, fontSize = 10.sp)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.weight(1f).fillMaxWidth(0.95f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(2.dp)
            ) {
                items(availableStalls) { stall ->
                    StallSlot(
                        stall = stall,
                        isSelected = selectedStall?.id == stall.id,
                        canAfford = gold >= stall.cost,
                        onClick = { onStallSelected(stall) },
                        stallsSheet = stallsSheet
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
            SpriteButton(
                normalRect = SpriteConstants.BTN_START_RECT,
                onClick = onStartWave,
                onTriggerHaptic = onTriggerHaptic,
                enabled = !waveActive,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = if (waveActive) "LUNCH RUSH..." else "START LUNCH RUSH",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}
