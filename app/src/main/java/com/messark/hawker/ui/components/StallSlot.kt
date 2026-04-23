package com.messark.hawker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messark.hawker.model.Stall
import com.messark.hawker.registry.StallRegistry

@Composable
fun StallSlot(
    stall: Stall,
    isSelected: Boolean,
    canAfford: Boolean,
    onClick: () -> Unit,
    stallsSheet: ImageBitmap,
    modifier: Modifier = Modifier
) {
    val spriteRect = StallRegistry.get(stall.stallType).spriteRect

    Box(
        modifier = modifier
            .aspectRatio(0.7f) // Slightly taller to accommodate text below
            .background(if (isSelected) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f))
            .border(1.dp, if (isSelected) Color.White else Color.Gray)
            .clickable { onClick() }
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.size(width = 29.dp, height = 39.dp)) {
                drawImage(
                    image = stallsSheet,
                    srcOffset = spriteRect.topLeft,
                    srcSize = spriteRect.size,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    colorFilter = if (!canAfford) {
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                    } else null
                )
            }
            Text(
                text = stall.name,
                color = Color.Black,
                fontSize = 12.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
            Text(
                text = "\$${stall.cost}",
                style = TextStyle(
                    color = if (canAfford) Color(0xFF00DD00) else Color.Red,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(
                        color = if (canAfford) Color(0xFF004400) else Color(0xFF440000),
                        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                        blurRadius = 2f
                    )
                ),
                maxLines = 1
            )
        }
    }
}
