package com.messark.hawkerrush.ui.components

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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messark.hawkerrush.model.Stall
import com.messark.hawkerrush.model.StallType
import com.messark.hawkerrush.ui.constants.SpriteConstants

@Composable
fun StallSlot(
    stall: Stall,
    isSelected: Boolean,
    onClick: () -> Unit,
    stallsSheet: ImageBitmap
) {
    val spriteRect = SpriteConstants.STALL_RECTS[stall.stallType] ?: SpriteConstants.STALL_RECTS[StallType.CHICKEN_RICE]!!

    Box(
        modifier = Modifier
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
            Canvas(modifier = Modifier.size(width = 24.dp, height = 32.dp)) {
                drawImage(
                    image = stallsSheet,
                    srcOffset = spriteRect.topLeft,
                    srcSize = spriteRect.size,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
            }
            Text(
                text = stall.name,
                color = Color.White,
                fontSize = 8.sp,
                maxLines = 1
            )
            Text(
                text = "$${stall.cost}",
                color = Color.Yellow,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}
