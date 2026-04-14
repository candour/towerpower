package com.messark.hawkerrush.ui.components

import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import com.messark.hawkerrush.R
import com.messark.hawkerrush.model.*
import com.messark.hawkerrush.ui.constants.SpriteConstants
import java.util.Comparator

@Composable
fun GameBoard(
    hexes: Map<AxialCoordinate, HexTile>,
    enemies: List<Enemy>,
    projectiles: List<Projectile>,
    puddles: List<StickyPuddle>,
    selectedBoardStall: AxialCoordinate?,
    onCellClick: (AxialCoordinate) -> Unit,
    modifier: Modifier = Modifier
) {
    val spriteSheet = ImageBitmap.imageResource(id = R.drawable.sprite_sheet)

    val hexWidth = 47.dp // Reduced from 48.dp to bring hexes closer
    val hexHeight = hexWidth * 91f / 101f
    
    // Vertical spacing multiplier for pointy-top hexes. 
    // Reduced from 0.74f to 0.69f to reduce the vertical gap by approx 6 pixels.
    val rowSpacingFactor = 0.69f

    val minR = hexes.keys.minOfOrNull { it.r } ?: 0
    val maxR = hexes.keys.maxOfOrNull { it.r } ?: 0
    val minQ = hexes.keys.minOfOrNull { it.q } ?: 0
    val maxQ = hexes.keys.maxOfOrNull { it.q } ?: 0

    // Calculate dimensions based on the grid
    // For pointy-top:
    // x = (q + r/2) * width
    // y = r * height * 0.75

    // We use the grid extents to calculate board size
    // In an axial system for an offset-grid-like layout (which generateRandomVerticalMap creates),
    // the max column offset is what determines the width.
    val gridWidth = if (hexes.isEmpty()) 0 else {
        hexes.keys.maxOf { it.q + (it.r / 2) } - hexes.keys.minOf { it.q + (it.r / 2) } + 1
    }
    val gridHeight = if (hexes.isEmpty()) 0 else (maxR - minR + 1)

    val boardWidth = hexWidth * (gridWidth.toFloat() + 0.5f) + 40.dp // extra 0.5 for staggering, 40 for border
    val boardHeight = hexHeight * (gridHeight.toFloat() * rowSpacingFactor + 0.25f) + 40.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1B5E20))
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(boardWidth, boardHeight)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val wPx = hexWidth.toPx()
                        val hPx = hexHeight.toPx()
                        val borderPx = 20.dp.toPx()

                        val r = Math.round((offset.y - borderPx - hPx / 2f) / (hPx * rowSpacingFactor)).toInt()
                        val q = Math.round((offset.x - borderPx - wPx / 2f) / wPx - r / 2f).toInt()

                        onCellClick(AxialCoordinate(q, r))
                    }
                }
        ) {
            val wPx = hexWidth.toPx()
            val hPx = hexHeight.toPx()
            val borderPx = 20.dp.toPx()

            fun toScreenPrecise(q: Float, r: Float): Offset {
                val x = (q + r / 2f) * wPx + borderPx + wPx / 2f
                val y = r * (hPx * rowSpacingFactor) + borderPx + hPx / 2f
                return Offset(x, y)
            }

            fun toScreen(q: Int, r: Int): Offset = toScreenPrecise(q.toFloat(), r.toFloat())

            fun createHexPath(center: Offset, width: Float, height: Float): Path {
                // Increased bleed to cover overlaps correctly and hide the green background
                val bleed = 3.5f
                val w = width + bleed
                val h = height + bleed
                return Path().apply {
                    moveTo(center.x, center.y - h / 2f)
                    lineTo(center.x + w / 2f, center.y - h / 4f)
                    lineTo(center.x + w / 2f, center.y + h / 4f)
                    lineTo(center.x, center.y + h / 2f)
                    lineTo(center.x - w / 2f, center.y + h / 4f)
                    lineTo(center.x - w / 2f, center.y - h / 4f)
                    close()
                }
            }

            fun drawSprite(
                srcRect: androidx.compose.ui.unit.IntRect,
                destCenter: Offset,
                destSize: Size,
                anchor: Offset = Offset(0.5f, 0.5f),
                clipHex: Boolean = false
            ) {
                val topLeft = Offset(
                    destCenter.x - destSize.width * anchor.x,
                    destCenter.y - destSize.height * anchor.y
                )

                val drawBlock: DrawScope.() -> Unit = {
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            isFilterBitmap = true
                        }
                        val androidSrc = Rect(srcRect.left, srcRect.top, srcRect.right, srcRect.bottom)
                        val androidDst = RectF(topLeft.x, topLeft.y, topLeft.x + destSize.width, topLeft.y + destSize.height)
                        canvas.nativeCanvas.drawBitmap(spriteSheet.asAndroidBitmap(), androidSrc, androidDst, paint)
                    }
                }

                if (clipHex) {
                    clipPath(createHexPath(destCenter, wPx, hPx)) {
                        drawBlock()
                    }
                } else {
                    drawBlock()
                }
            }

            val drawables = mutableListOf<DrawableEntity>()

            hexes.forEach { (coord, tile) ->
                val screenPos = toScreen(coord.q, coord.r)

                // 1. Foundation: Draw floor under everything to ensure transparency blends with ground
                if (!tile.type.name.startsWith("EDGE_")) {
                    drawables.add(DrawableEntity(
                        q = coord.q.toFloat(),
                        r = coord.r.toFloat(),
                        zOrder = 0,
                        draw = {
                            val floorSrc = SpriteConstants.FLOOR_RECTS[tile.floorVariant % SpriteConstants.FLOOR_RECTS.size]
                            // Increased size overlap to ensure floor tiles cover all seams
                            drawSprite(
                                srcRect = floorSrc,
                                destCenter = screenPos,
                                destSize = Size(wPx + 3.0f, hPx + 3.0f),
                                clipHex = true
                            )
                        }
                    ))
                }

                // 2. Tile Content (Edges, Pillars, Tables, Start Decoration)
                if (tile.type != TileType.FLOOR) {
                    if (tile.type == TileType.START) {
                        drawables.add(DrawableEntity(
                            q = coord.q.toFloat(),
                            r = coord.r.toFloat(),
                            zOrder = 1,
                            draw = {
                                val hexPath = createHexPath(screenPos, wPx, hPx)
                                drawPath(
                                    path = hexPath,
                                    color = Color.Green.copy(alpha = 0.3f)
                                )
                            }
                        ))
                    }

                    val srcRect = when (tile.type) {
                        TileType.PILLAR -> SpriteConstants.PILLAR_RECT
                        TileType.GOAL_TABLE -> SpriteConstants.GOAL_TABLE_RECT
                        TileType.EDGE_NW -> SpriteConstants.EDGE_NW_RECT
                        TileType.EDGE_NE -> SpriteConstants.EDGE_NE_RECT
                        TileType.EDGE_SW -> SpriteConstants.EDGE_SW_RECT
                        TileType.EDGE_SE -> SpriteConstants.EDGE_SE_RECT
                        TileType.EDGE_TOP -> SpriteConstants.EDGE_TOP_RECT
                        TileType.START -> null
                        TileType.FLOOR -> null
                        TileType.END -> null
                    }

                    srcRect?.let { rect ->
                        val isEdge = tile.type.name.startsWith("EDGE_")
                        drawables.add(DrawableEntity(
                            q = coord.q.toFloat(),
                            r = coord.r.toFloat(),
                            zOrder = if (isEdge) 1 else 2,
                            draw = {
                                val scale = wPx / 101f
                                val dSize = Size(rect.width * scale, rect.height * scale)
                                val anchor = when (tile.type) {
                                    TileType.PILLAR -> Offset(0.5f, 0.8f)
                                    TileType.GOAL_TABLE -> Offset(0.5f, 0.75f)
                                    else -> Offset(0.5f, 0.5f)
                                }
                                drawSprite(
                                    srcRect = rect,
                                    destCenter = screenPos,
                                    destSize = dSize,
                                    anchor = anchor,
                                    clipHex = isEdge
                                )
                            }
                        ))
                    }
                }

                // 3. Stalls
                tile.stall?.let { stall ->
                    val stallSrcRect = SpriteConstants.STALL_RECTS[stall.stallType] ?: SpriteConstants.STALL_RECTS[StallType.CHICKEN_RICE]!!
                    drawables.add(DrawableEntity(
                        q = coord.q.toFloat(),
                        r = coord.r.toFloat(),
                        zOrder = 3,
                        draw = {
                            val scale = wPx / 101f
                            val size = 65f * scale
                            drawSprite(
                                srcRect = stallSrcRect,
                                destCenter = screenPos,
                                destSize = Size(size, size)
                            )

                            if (selectedBoardStall == coord) {
                                val hexPath = createHexPath(screenPos, wPx, hPx)
                                drawPath(
                                    path = hexPath,
                                    color = Color.White.copy(alpha = 0.3f)
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.15f),
                                    radius = stall.range * wPx,
                                    center = screenPos
                                )
                            }
                        }
                    ))
                }
            }

            puddles.forEach { puddle ->
                val screenPos = toScreenPrecise(puddle.position.q, puddle.position.r)
                drawables.add(DrawableEntity(
                    q = puddle.position.q,
                    r = puddle.position.r,
                    zOrder = 1,
                    draw = {
                        val scale = wPx / 101f
                        drawSprite(
                            srcRect = SpriteConstants.FX_PUDDLE_RECT,
                            destCenter = screenPos,
                            destSize = Size(64f * scale, 62f * scale),
                            clipHex = true
                        )
                    }
                ))
            }

            enemies.forEach { enemy ->
                val screenPos = toScreenPrecise(enemy.position.q, enemy.position.r)
                val enemySrcRect = when (enemy.type) {
                    EnemyType.SALARYMAN -> SpriteConstants.ENEMY_SALARYMAN_RECT
                    EnemyType.TOURIST -> SpriteConstants.ENEMY_TOURIST_RECT
                    EnemyType.AUNTIE -> SpriteConstants.ENEMY_AUNTIE_RECT
                    EnemyType.DELIVERY_RIDER -> SpriteConstants.ENEMY_RIDER_RECT
                }
                drawables.add(DrawableEntity(
                    q = enemy.position.q,
                    r = enemy.position.r,
                    zOrder = 4,
                    draw = {
                        val scale = wPx / 101f
                        val dSize = Size(enemySrcRect.width * scale, enemySrcRect.height * scale)
                        drawSprite(
                            srcRect = enemySrcRect,
                            destCenter = screenPos,
                            destSize = dSize,
                            anchor = Offset(0.5f, 1.0f) // Anchor feet to hex center
                        )

                        val barWidth = wPx * 0.8f
                        val barHeight = 4.dp.toPx()
                        val healthPercent = enemy.health.toFloat() / enemy.maxHealth
                        drawRect(Color.Black, Offset(screenPos.x - barWidth / 2, screenPos.y - hPx / 2 - 10f), Size(barWidth, barHeight))
                        drawRect(Color.Red, Offset(screenPos.x - barWidth / 2, screenPos.y - hPx / 2 - 10f), Size(barWidth * healthPercent, barHeight))
                    }
                ))
            }

            projectiles.forEach { projectile ->
                val currentScreenPos = toScreenPrecise(projectile.position.q, projectile.position.r)
                val lastScreenPos = projectile.lastPosition?.let {
                    toScreenPrecise(it.q, it.r)
                } ?: currentScreenPos

                drawables.add(DrawableEntity(
                    q = projectile.position.q,
                    r = projectile.position.r,
                    zOrder = 5,
                    draw = {
                        val radius = 4.dp.toPx()
                        // Draw 4 sub-frames between last position and current position for smoothness
                        val steps = 4
                        for (i in 0..steps) {
                            val fraction = i.toFloat() / steps
                            val lerpPos = Offset(
                                x = lastScreenPos.x + (currentScreenPos.x - lastScreenPos.x) * fraction,
                                y = lastScreenPos.y + (currentScreenPos.y - lastScreenPos.y) * fraction
                            )
                            drawCircle(
                                color = projectile.color.copy(alpha = 0.4f + 0.6f * fraction),
                                radius = radius * (0.6f + 0.4f * fraction),
                                center = lerpPos
                            )
                        }
                    }
                ))
            }

            val sortedDrawables = drawables.sortedWith(object : Comparator<DrawableEntity> {
                override fun compare(a: DrawableEntity, b: DrawableEntity): Int {
                    val aGroup = if (a.zOrder == 0) 0 else if (a.zOrder == 1) 1 else 2
                    val bGroup = if (b.zOrder == 0) 0 else if (b.zOrder == 1) 1 else 2
                    
                    if (aGroup != bGroup) return aGroup.compareTo(bGroup)
                    if (aGroup == 2) {
                        val rComp = a.r.compareTo(b.r)
                        if (rComp != 0) return rComp
                    }
                    val zComp = a.zOrder.compareTo(b.zOrder)
                    if (zComp != 0) return zComp
                    return a.q.compareTo(b.q)
                }
            })
            sortedDrawables.forEach { it.draw(this) }
        }
    }
}

data class DrawableEntity(
    val q: Float,
    val r: Float,
    val zOrder: Int,
    val draw: DrawScope.() -> Unit
)
