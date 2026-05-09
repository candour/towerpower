package com.messark.hawker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messark.hawker.model.Stall
import com.messark.hawker.model.StallType
import com.messark.hawker.utils.StallUpgradeManager

@Composable
fun UpgradeOverlay(
    stall: Stall,
    gold: Int,
    kitchelinStars: Int,
    freeUpgradeCount: Int,
    onUpgradeRandom: () -> Unit,
    onUpgradeSpecific: (String) -> Unit,
    onDismiss: () -> Unit,
    onTriggerHaptic: () -> Unit
) {
    val baseUpgradeCost = stall.getUpgradeCost()
    val specificUpgradeCost = if (freeUpgradeCount > 0) 0 else baseUpgradeCost * 2

    val availableStats = StallUpgradeManager.getAvailableUpgradeStats(stall)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(onClick = {
                onTriggerHaptic()
                onDismiss()
            }),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 400.dp)
                .clickable(enabled = false) {} // Prevent clicks through to background
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "UPGRADE ${stall.name.uppercase()}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                if (freeUpgradeCount > 0) {
                    Text(
                        text = "Free specific upgrade active ($freeUpgradeCount remaining)! This upgrade will cost $0 and will not cause a shutdown.",
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Warning: Specific upgrades cost 2x and will shut down this stall for the next wave!",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    if (kitchelinStars > 0) {
                        Text(
                            text = "Tip: Click the stars in the top-left to use a star for a free upgrade action.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Random Upgrade Button
                UpgradeButton(
                    label = "Random Upgrade",
                    cost = baseUpgradeCost,
                    enabled = gold >= baseUpgradeCost,
                    onClick = {
                        onTriggerHaptic()
                        onUpgradeRandom()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Specific Upgrades",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                // Specific Upgrade Buttons
                availableStats.forEach { stat ->
                    UpgradeButton(
                        label = stat,
                        cost = specificUpgradeCost,
                        enabled = gold >= specificUpgradeCost,
                        onClick = {
                            onTriggerHaptic()
                            onUpgradeSpecific(stat)
                        }
                    )
                }

                Text(
                    text = "Tap anywhere outside to cancel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun UpgradeButton(
    label: String,
    cost: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (enabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = Color.White, fontWeight = FontWeight.Bold)
            Text(text = "$$cost", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
