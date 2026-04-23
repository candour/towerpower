package com.messark.hawker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messark.hawker.R
import com.messark.hawker.model.Stall
import com.messark.hawker.model.StallType

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
    val stallsSheet = ImageBitmap.imageResource(id = R.drawable.stalls)

    if (selectedBoardStall != null) {
        val baseStall = availableStalls.find { it.stallType == selectedBoardStall.stallType } ?: selectedBoardStall

        StallConsole(
            stall = selectedBoardStall,
            baseStall = baseStall,
            gold = gold,
            onSell = onSellStall,
            onUpgrade = onUpgradeStall,
            onCycleTarget = onCycleTargetMode,
            onStartWave = onStartWave,
            onTriggerHaptic = onTriggerHaptic,
            waveActive = waveActive,
            modifier = modifier.fillMaxSize()
        )
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxWidth().aspectRatio(1080f/720f)) {
            val width = maxWidth
            val height = maxHeight
            val backgroundImage = ImageBitmap.imageResource(id = R.drawable.control_panel_unselected)

            // Background Image
            Image(
                bitmap = backgroundImage,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            // BUDGET (Top Left)
            OutlinedText(
                text = "$gold",
                fillColor = Color(0xFF00FF00), // Bright Green
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (width * 0.16f), y = (height * 0.15f))
            )

            // BLUE TITLE BAR (Top Right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (width * 0.34f), y = (height * 0.08f))
                    .width(width * 0.58f)
                    .height(height * 0.08f),
                contentAlignment = Alignment.Center
            ) {
                OutlinedText(
                    text = if (selectedStall != null) selectedStall.name.uppercase() else if (currentWave > 0) "WAVE $currentWave" else "STALL SHOP",
                    fillColor = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // CREAM BOX (Below Blue Bar)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (width * 0.34f), y = (height * 0.18f))
                    .width(width * 0.58f)
                    .height(height * 0.12f),
                contentAlignment = Alignment.Center
            ) {
                if (selectedStall != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = selectedStall.description,
                            color = Color.Black,
                            fontSize = 10.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .border(1.dp, Color.Black, androidx.compose.foundation.shape.CircleShape)
                                .clickable {
                                    onShowStallTutorial(selectedStall.stallType)
                                    onTriggerHaptic()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "?", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (score > 0) {
                    Text(text = "SCORE: $score", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // LARGE CENTRAL CREAM BOX (Stall Selector)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (width * 0.08f), y = (height * 0.33f))
                    .width(width * 0.84f)
                    .height(height * 0.40f)
                    .padding(8.dp)
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    items(availableStalls) { stall ->
                        StallSlot(
                            stall = stall,
                            isSelected = selectedStall?.id == stall.id,
                            canAfford = gold >= stall.cost,
                            onClick = { onStallSelected(stall) },
                            stallsSheet = stallsSheet,
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                }
            }

            // START LUNCH RUSH BUTTON (Bottom)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(height * 0.22f)
                    .clickable(
                        enabled = !waveActive,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onTriggerHaptic()
                        onStartWave()
                    }
            ) {
                if (waveActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}
