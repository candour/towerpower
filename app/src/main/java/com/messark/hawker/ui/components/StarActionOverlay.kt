package com.messark.hawker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StarActionOverlay(
    kitchelinStars: Int,
    health: Int,
    onChooseBudgetBonus: () -> Unit,
    onChooseFreeUpgrade: () -> Unit,
    onRestoreHealth: () -> Unit,
    onDismiss: () -> Unit,
    onTriggerHaptic: () -> Unit
) {
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.Yellow,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "KITCHELIN STAR ACTION",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }

                Text(
                    text = "You have $kitchelinStars Kitchelin Star${if (kitchelinStars > 1) "s" else ""}. Spend one to activate a bonus!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                StarActionButton(
                    title = "Budget Bonus",
                    description = "Earn a 100% bonus on all gold from customers at the end of the next round. (Can stack!)",
                    onClick = {
                        onTriggerHaptic()
                        onChooseBudgetBonus()
                    }
                )

                StarActionButton(
                    title = "Free Specific Upgrade",
                    description = "Your next specific stall upgrade will be free and won't cause the stall to shut down.",
                    onClick = {
                        onTriggerHaptic()
                        onChooseFreeUpgrade()
                    }
                )

                StarActionButton(
                    title = "Restore Health",
                    description = "Replace one of the customers at the end table with an empty chair (+1 health).",
                    enabled = health < 10,
                    onClick = {
                        onTriggerHaptic()
                        onRestoreHealth()
                    }
                )

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
fun StarActionButton(
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        color = if (enabled) Color(0xFFFFD700) else Color.Gray, // Gold or Gray
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = description,
                color = Color.Black.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
