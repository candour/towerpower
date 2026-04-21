package com.messark.hawkerrush.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messark.hawkerrush.R
import com.messark.hawkerrush.SpriteButton
import com.messark.hawkerrush.model.TutorialData
import com.messark.hawkerrush.model.TutorialType
import com.messark.hawkerrush.ui.constants.SpriteConstants
import kotlinx.coroutines.delay

@Composable
fun TutorialOverlay(
    tutorialData: TutorialData,
    showTutorialsSetting: Boolean,
    onToggleTutorialsSetting: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onTriggerHaptic: () -> Unit
) {
    val context = LocalContext.current
    val enemyBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.enemies).asImageBitmap()
    }

    // Animation state for the sprite
    var frameIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            frameIndex = (frameIndex + 1) % SpriteConstants.ENEMY_SPRITE_FRAMES
        }
    }

    val themeColor = when (tutorialData.type) {
        TutorialType.ENEMY -> Color(0xFFFF5252) // Light Red/Coral
        TutorialType.STALL -> Color(0xFF4CAF50) // Green
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = true, onClick = {}), // Block clicks to game
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize(0.9f)
                .border(4.dp, themeColor, RoundedCornerShape(16.dp)),
            color = Color(0xFF212121),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top: Picture
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f)
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (tutorialData.enemyType != null) {
                        val rowIndex = SpriteConstants.ENEMY_ROW_INDICES[tutorialData.enemyType] ?: 0
                        val srcRect = IntRect(
                            left = frameIndex * SpriteConstants.ENEMY_SPRITE_WIDTH,
                            top = rowIndex * SpriteConstants.ENEMY_SPRITE_HEIGHT,
                            right = (frameIndex + 1) * SpriteConstants.ENEMY_SPRITE_WIDTH,
                            bottom = (rowIndex + 1) * SpriteConstants.ENEMY_SPRITE_HEIGHT
                        )

                        Canvas(modifier = Modifier.fillMaxSize(0.8f)) {
                            val scale = size.height / SpriteConstants.ENEMY_SPRITE_HEIGHT
                            val drawWidth = SpriteConstants.ENEMY_SPRITE_WIDTH * scale
                            val drawHeight = size.height

                            drawIntoCanvas { canvas ->
                                val paint = android.graphics.Paint().apply {
                                    isFilterBitmap = false // Keep it pixelated/sharp if desired, or true for smooth
                                }
                                val androidSrc = android.graphics.Rect(srcRect.left, srcRect.top, srcRect.right, srcRect.bottom)
                                val androidDst = android.graphics.RectF(
                                    (size.width - drawWidth) / 2f,
                                    0f,
                                    (size.width + drawWidth) / 2f,
                                    drawHeight
                                )
                                canvas.nativeCanvas.drawBitmap(enemyBitmap.asAndroidBitmap(), androidSrc, androidDst, paint)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Middle: Text
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.4f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tutorialData.title.uppercase(),
                        color = themeColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = tutorialData.description,
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // Bottom: Controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onToggleTutorialsSetting(!showTutorialsSetting) }
                    ) {
                        Checkbox(
                            checked = showTutorialsSetting,
                            onCheckedChange = {
                                onToggleTutorialsSetting(it)
                                onTriggerHaptic()
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = themeColor,
                                uncheckedColor = Color.Gray,
                                checkmarkColor = Color.White
                            )
                        )
                        Text(
                            text = "Show tutorials",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(contentAlignment = Alignment.Center) {
                        SpriteButton(
                            normalRect = SpriteConstants.BTN_RESUME_RECT,
                            pressedRect = SpriteConstants.BTN_RESUME_CLICK_RECT,
                            onClick = onDismiss,
                            onTriggerHaptic = onTriggerHaptic,
                            modifier = Modifier
                                .width(200.dp)
                                .height(54.dp)
                        )
                        Text(
                            text = "OKAY!",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }
            }
        }
    }
}
