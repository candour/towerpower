package com.messark.hawkerrush.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
import com.messark.hawkerrush.registry.EnemyRegistry
import com.messark.hawkerrush.registry.StallRegistry
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
    val stallBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.stalls).asImageBitmap()
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
                        val enemyDef = EnemyRegistry.get(tutorialData.enemyType)
                        val rowIndex = enemyDef.spriteRow
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
                    } else if (tutorialData.stallType != null) {
                        val srcRect = StallRegistry.get(tutorialData.stallType).spriteRect
                        Canvas(modifier = Modifier.fillMaxSize(0.8f)) {
                            val scale = Math.min(size.width / srcRect.width, size.height / srcRect.height)
                            val drawWidth = srcRect.width * scale
                            val drawHeight = srcRect.height * scale

                            drawIntoCanvas { canvas ->
                                val paint = android.graphics.Paint().apply {
                                    isFilterBitmap = false
                                }
                                val androidSrc = android.graphics.Rect(srcRect.left, srcRect.top, srcRect.right, srcRect.bottom)
                                val androidDst = android.graphics.RectF(
                                    (size.width - drawWidth) / 2f,
                                    (size.height - drawHeight) / 2f,
                                    (size.width + drawWidth) / 2f,
                                    (size.height + drawHeight) / 2f
                                )
                                canvas.nativeCanvas.drawBitmap(stallBitmap.asAndroidBitmap(), androidSrc, androidDst, paint)
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

                    tutorialData.signatureMove?.let { move ->
                        Text(
                            text = move.uppercase(),
                            color = Color.Yellow,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = tutorialData.description,
                            color = Color.White,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        if (tutorialData.type == TutorialType.ENEMY) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    onTriggerHaptic()
                                    onToggleTutorialsSetting(!showTutorialsSetting)
                                }
                            ) {
                                Checkbox(
                                    checked = showTutorialsSetting,
                                    onCheckedChange = null, // Handled by Row clickable
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
                        }
                    }
                }

                // Bottom: Controls
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                    }
                }
            }
        }
    }
}
