package com.messark.hawker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import com.messark.hawker.registry.StallRegistry

@Composable
fun StallConsole(
    stall: Stall,
    baseStall: Stall,
    gold: Int,
    onSell: () -> Unit,
    onUpgrade: () -> Unit,
    onCycleTarget: () -> Unit,
    onStartWave: () -> Unit,
    onTriggerHaptic: () -> Unit,
    waveActive: Boolean,
    modifier: Modifier = Modifier
) {
    val upgradeCost = stall.getUpgradeCost()
    val canAffordUpgrade = gold >= upgradeCost
    val stallsSheet = ImageBitmap.imageResource(id = R.drawable.stalls)
    val backgroundImage = ImageBitmap.imageResource(id = R.drawable.control_panel_selected)

    BoxWithConstraints(modifier = modifier.fillMaxWidth().aspectRatio(1080f/720f)) {
        val width = maxWidth
        val height = maxHeight

        // Background Image
        Image(
            bitmap = backgroundImage,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // BUDGET
        OutlinedText(
            text = "$gold",
            fillColor = Color(0xFF00FF00), // Bright Green
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (width * 0.16f), y = (height * 0.15f))
        )

        // STALL ICON
        val stallDef = StallRegistry.get(stall.stallType)
        val spriteRect = stallDef.spriteRect
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-width * 0.02f), y = (height * 0.08f)) // Moved down to yellow box
                .width(width * 0.3f * 0.8f) // Reduced by 20%
                .height(height * 0.38f * 0.8f) // Reduced by 20%
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(
                    image = stallsSheet,
                    srcOffset = androidx.compose.ui.unit.IntOffset(spriteRect.left, spriteRect.top),
                    srcSize = androidx.compose.ui.unit.IntSize(spriteRect.width, spriteRect.height),
                    dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                )
            }
        }

        // STALL NAME (Now in the yellow box area)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (width * 0.34f), y = (height * 0.08f))
                .width(width * 0.40f) // Slightly wider
                .height(height * 0.22f),
            contentAlignment = Alignment.Center
        ) {
            OutlinedText(
                text = stall.name.uppercase(),
                fillColor = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 16.sp // Tighter line height
            )
        }

        // STATS BOX
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (width * 0.08f), y = (height * 0.42f))
                .width(width * 0.32f)
        ) {
            val hungerWord = if (stall.stallType.isUtility) "Effect" else "Feed"

            StatLine(label = hungerWord, value = "${stall.damage}")
            StatLine(label = "Range", value = String.format("%.1f", stall.range))
            StatLine(label = "Rate", value = String.format("%.1fs", stall.fireRateMs / 1000f))
            if (stall.aoeRadius > 0) {
                StatLine(label = "Area", value = String.format("%.1f", stall.aoeRadius))
            }
            // Reduced spacing
            Spacer(modifier = Modifier.height(2.dp))

            // Upgrade details inside STATS box
            if (stall.upgrades.isNotEmpty()) {
                val displayUpgrades = if (stall.stallType == StallType.TRAY_RETURN_UNCLE) {
                    stall.upgrades.filterKeys { it != "Rate" && it != "Duration" }
                } else {
                    stall.upgrades
                }
                displayUpgrades.entries.forEach { (key, value) ->
                    val benefit = stall.getUpgradeBenefit(key, value)
                    val label = when(key) {
                        "Damage" -> "Feed"
                        else -> key
                    }
                    val valueText = if (benefit.isNotEmpty()) "Lvl $value ($benefit)" else "Lvl $value"
                    StatLine(
                        label = label,
                        value = valueText,
                        labelColor = Color.Blue,
                        valueColor = Color.Blue
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }

            val (statLabel, statValue) = when (stall.stallType) {
                StallType.TEH_TARIK -> "Targets Slowed" to stall.uniqueTargetIds.size
                StallType.ICE_KACHANG -> "Targets Frozen" to stall.uniqueTargetIds.size
                StallType.TRAY_RETURN_UNCLE -> "People Cleaned" to stall.uniqueTargetIds.size
                else -> "People Fed" to stall.kills
            }
            StatLine(label = statLabel, value = "$statValue", valueColor = Color(0xFF00AA00))
        }

        // BUTTONS (Transparent Clickables)

        // SELL BUTTON
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (width * 0.43f), y = (height * 0.31f))
                .width(width * 0.46f)
                .height(height * 0.13f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onTriggerHaptic()
                    onSell()
                }
        ) {
            OutlinedText(
                text = "$${(stall.totalInvestment * 0.5f).toInt()}",
                fillColor = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
            )
        }

        // UPGRADE BUTTON
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (width * 0.43f), y = (height * 0.46f))
                .width(width * 0.46f)
                .height(height * 0.13f)
                .clickable(
                    enabled = canAffordUpgrade,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onTriggerHaptic()
                    onUpgrade()
                }
        ) {
            OutlinedText(
                text = "$$upgradeCost",
                fillColor = if (canAffordUpgrade) Color(0xFF00FF00) else Color.Red,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
            )
        }

        // TARGET BUTTON
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (width * 0.43f), y = (height * 0.61f))
                .width(width * 0.46f)
                .height(height * 0.13f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onTriggerHaptic()
                    onCycleTarget()
                }
        ) {
            OutlinedText(
                text = stall.targetMode.name,
                fillColor = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .offset(y = (-6).dp) // Moved up by half height (text is approx 11-12sp/dp)
            )
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

@Composable
fun StatLine(
    label: String,
    value: String,
    labelColor: Color = Color.Black,
    valueColor: Color = Color.Black,
    labelOffset: androidx.compose.ui.unit.Dp = 12.dp // Approx 2 characters
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            color = labelColor,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            modifier = Modifier.padding(start = labelOffset)
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
