package com.messark.hawker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messark.hawker.MainActivity
import com.messark.hawker.model.Stall
import com.messark.hawker.model.StallType
import com.messark.hawker.SpriteButton
import com.messark.hawker.ui.constants.SpriteConstants

@Composable
fun StallConsole(
    stall: Stall,
    baseStall: Stall,
    onSell: () -> Unit,
    onUpgrade: () -> Unit,
    onCycleTarget: () -> Unit,
    onTriggerHaptic: () -> Unit,
    canAffordUpgrade: Boolean,
    upgradeCost: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stall.name.uppercase(), color = Color.White, fontSize = 14.sp)
                if (stall.upgrades.isEmpty()) {
                    Text(text = "No upgrades", color = Color.Gray, fontSize = 10.sp)
                } else {
                    val upgradeText = stall.upgrades.entries.joinToString(", ") { (key, value) ->
                        val benefit = stall.getUpgradeBenefit(key, value)
                        if (benefit.isNotEmpty()) "$key: $value ($benefit)" else "$key: $value"
                    }
                    Text(text = upgradeText, color = Color.Gray, fontSize = 10.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Target: ${stall.targetMode.name}", color = Color.Cyan, fontSize = 12.sp)
                Text(text = "Targets: ${stall.uniqueTargetIds.size} | Kills: ${stall.kills}", color = Color.Green, fontSize = 10.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                SpriteButton(
                    normalRect = SpriteConstants.BTN_SELL_RECT,
                    onClick = onSell,
                    onTriggerHaptic = onTriggerHaptic,
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                )
                Text(
                    text = "SELL \$${(stall.totalInvestment * 0.5f).toInt()}",
                    color = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                SpriteButton(
                    normalRect = SpriteConstants.BTN_UPGRADE_RECT,
                    onClick = onUpgrade,
                    onTriggerHaptic = onTriggerHaptic,
                    enabled = canAffordUpgrade,
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                )
                Text(
                    text = buildAnnotatedString {
                        append("UPGRADE ")
                        withStyle(style = SpanStyle(color = if (canAffordUpgrade) Color.Yellow else Color.Red)) {
                            append("\$$upgradeCost")
                        }
                    },
                    fontSize = 9.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                SpriteButton(
                    normalRect = SpriteConstants.BTN_TARGET_RECT,
                    onClick = onCycleTarget,
                    onTriggerHaptic = onTriggerHaptic,
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                )
                Text(
                    text = "TARGET",
                    color = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}
