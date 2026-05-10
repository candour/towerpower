package com.messark.hawker.ui.components

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.messark.hawker.R
import com.messark.hawker.model.*
import com.messark.hawker.registry.EnemyRegistry
import com.messark.hawker.registry.StallRegistry
import com.messark.hawker.ui.constants.SpriteConstants
import com.messark.hawker.utils.GridUtils
import kotlin.math.*

private class RenderingContext(
    val wPx: Float,
    val hPx: Float,
    val rowSpacingFactor: Float,
    val borderPx: Float,
    val now: Long,
    val spriteSheet: ImageBitmap,
    val stallsSheet: ImageBitmap,
    val enemiesSheet: ImageBitmap,
    val endTableSheet: ImageBitmap,
    val upgradePaint: android.graphics.Paint,
    val spritePaint: android.graphics.Paint,
    val hexPath: Path = Path()
) {
    fun toScreen(coord: AxialCoordinate): Offset =
        GridUtils.toScreenPrecise(coord.q.toFloat(), coord.r.toFloat(), wPx, hPx, rowSpacingFactor, borderPx)

    fun toScreenPrecise(q: Float, r: Float): Offset =
        GridUtils.toScreenPrecise(q, r, wPx, hPx, rowSpacingFactor, borderPx)

    fun resetHexPath(center: Offset, scale: Float = 1.0f): Path {
        val bleed = 3.5f
        val w = (wPx + bleed) * scale
        val h = (hPx + bleed) * scale
        hexPath.reset()
        hexPath.moveTo(center.x, center.y - h / 2f)
        hexPath.lineTo(center.x + w / 2f, center.y - h / 4f)
        hexPath.lineTo(center.x + w / 2f, center.y + h / 4f)
        hexPath.lineTo(center.x, center.y + h / 2f)
        hexPath.lineTo(center.x - w / 2f, center.y + h / 4f)
        hexPath.lineTo(center.x - w / 2f, center.y - h / 4f)
        hexPath.close()
        return hexPath
    }

    fun drawSprite(
        drawScope: DrawScope,
        srcRect: IntRect,
        destCenter: Offset,
        destSize: Size,
        anchor: Offset = Offset(0.5f, 0.5f),
        clipHex: Boolean = false,
        bitmap: ImageBitmap = spriteSheet,
        flipHorizontal: Boolean = false,
        alpha: Float = 1f
    ) {
        val topLeft = Offset(
            destCenter.x - destSize.width * anchor.x,
            destCenter.y - destSize.height * anchor.y
        )

        val drawBlock: DrawScope.() -> Unit = {
            drawIntoCanvas { canvas ->
                spritePaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
                val androidSrc = Rect(srcRect.left, srcRect.top, srcRect.right, srcRect.bottom)
                val androidDst = RectF(topLeft.x, topLeft.y, topLeft.x + destSize.width, topLeft.y + destSize.height)

                if (flipHorizontal) {
                    canvas.save()
                    canvas.scale(-1f, 1f, destCenter.x, destCenter.y)
                }
                canvas.nativeCanvas.drawBitmap(bitmap.asAndroidBitmap(), androidSrc, androidDst, spritePaint)
                if (flipHorizontal) {
                    canvas.restore()
                }
            }
        }

        if (clipHex) {
            drawScope.clipPath(resetHexPath(destCenter)) {
                drawBlock()
            }
        } else {
            drawScope.drawBlock()
        }
    }
}

private enum class WorldItemType { PILLAR, GOAL_TABLE, STALL, ENEMY }

private class WorldItem {
    var r: Float = 0f
    var type: WorldItemType = WorldItemType.PILLAR
    var coord: AxialCoordinate? = null
    var tile: HexTile? = null
    var enemy: Enemy? = null
    var screenPos: Offset = Offset.Zero

    fun set(r: Float, type: WorldItemType, coord: AxialCoordinate?, tile: HexTile?, enemy: Enemy?, screenPos: Offset) {
        this.r = r
        this.type = type
        this.coord = coord
        this.tile = tile
        this.enemy = enemy
        this.screenPos = screenPos
    }
}

@Composable
fun GameBoard(
    hexes: Map<AxialCoordinate, HexTile>,
    enemies: List<Enemy>,
    projectiles: List<Projectile>,
    puddles: List<StickyPuddle>,
    visualEffects: List<VisualEffect>,
    selectedBoardStall: AxialCoordinate?,
    isRemovePillarModeActive: Boolean = false,
    gold: Int,
    health: Int,
    onCellClick: (AxialCoordinate) -> Unit,
    modifier: Modifier = Modifier
) {
    val spriteSheet = ImageBitmap.imageResource(id = R.drawable.sprite_sheet)
    val stallsSheet = ImageBitmap.imageResource(id = R.drawable.stalls)
    val enemiesSheet = ImageBitmap.imageResource(id = R.drawable.enemies)
    val endTableSheet = ImageBitmap.imageResource(id = R.drawable.end_table)
    val upgradePaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
    }
    val spritePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
    }

    val worldItemPool = remember { List(512) { WorldItem() } }
    val activeWorldItems = remember { mutableListOf<WorldItem>() }
    val reusedPath = remember { Path() }

    val hexWidth = 47.dp
    val hexHeight = hexWidth * 91f / 101f
    val rowSpacingFactor = 0.69f

    val minR = hexes.keys.minOfOrNull { it.r } ?: 0
    val maxR = hexes.keys.maxOfOrNull { it.r } ?: 0

    val gridWidth = if (hexes.isEmpty()) 0 else {
        hexes.keys.maxOf { it.q + (it.r / 2) } - hexes.keys.minOf { it.q + (it.r / 2) } + 1
    }
    val gridHeight = if (hexes.isEmpty()) 0 else (maxR - minR + 1)

    val boardWidth = hexWidth * (gridWidth.toFloat() + 0.5f) + 40.dp
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
                        val w = hexWidth.toPx()
                        val h = hexHeight.toPx()
                        val b = 20.dp.toPx()

                        val fr = (offset.y - b - h / 2f) / (h * rowSpacingFactor)
                        val fq = (offset.x - b - w / 2f) / w - fr / 2f

                        val coord = GridUtils.hexRound(fq, fr)
                        if (hexes.containsKey(coord)) {
                            onCellClick(coord)
                        }
                    }
                }
        ) {
            val ctx = RenderingContext(
                wPx = hexWidth.toPx(), hPx = hexHeight.toPx(),
                rowSpacingFactor = rowSpacingFactor, borderPx = 20.dp.toPx(),
                now = System.currentTimeMillis(),
                spriteSheet, stallsSheet, enemiesSheet, endTableSheet, upgradePaint, spritePaint,
                hexPath = reusedPath
            )

            // 1. Background Layer (Direct Draw)
            hexes.forEach { (coord, tile) ->
                val screenPos = ctx.toScreen(coord)
                if (!tile.type.name.startsWith("EDGE_")) {
                    val floorSrc = SpriteConstants.FLOOR_RECTS[tile.floorVariant % SpriteConstants.FLOOR_RECTS.size]
                    ctx.drawSprite(this, floorSrc, screenPos, Size(ctx.wPx + 3.0f, ctx.hPx + 3.0f), clipHex = true)
                } else {
                    val edgeSrc = when (tile.type) {
                        TileType.EDGE_NW -> SpriteConstants.EDGE_NW_RECT
                        TileType.EDGE_NE -> SpriteConstants.EDGE_NE_RECT
                        TileType.EDGE_SW -> SpriteConstants.EDGE_SW_RECT
                        TileType.EDGE_SE -> SpriteConstants.EDGE_SE_RECT
                        TileType.EDGE_TOP -> SpriteConstants.EDGE_TOP_RECT
                        else -> null
                    }
                    edgeSrc?.let { rect ->
                        val scale = ctx.wPx / 101f
                        ctx.drawSprite(this, rect, screenPos, Size(rect.width * scale, rect.height * scale), clipHex = true)
                    }
                }
            }

            // 2. Decal Layer (Direct Draw)
            hexes.forEach { (coord, tile) ->
                val screenPos = ctx.toScreen(coord)
                if (tile.type == TileType.START) {
                    drawPath(path = ctx.resetHexPath(screenPos), color = Color.Green.copy(alpha = 0.3f))
                }
                if (tile.type == TileType.DRAIN) {
                    val s = ctx.wPx * 0.3f
                    drawRect(Color.DarkGray, Offset(screenPos.x - s / 2f, screenPos.y - s / 2f), Size(s, s))
                    for (i in 1..3) {
                        val x = screenPos.x - s / 2f + i * (s / 4f)
                        drawLine(Color.Black, Offset(x, screenPos.y - s / 2f), Offset(x, screenPos.y + s / 2f), 1.dp.toPx())
                    }
                }
            }
            puddles.forEach { puddle ->
                val sc = ctx.wPx / 101f
                ctx.drawSprite(this, SpriteConstants.FX_PUDDLE_RECT, ctx.toScreenPrecise(puddle.position.q, puddle.position.r), Size(64f * sc, 62f * sc), clipHex = true)
            }
            visualEffects.forEach { effect ->
                if (effect.type == VisualEffectType.GAS_CLOUD) {
                    val pos = ctx.toScreenPrecise(effect.position.q, effect.position.r)
                    val p = ((ctx.now - effect.startTimeMs).toFloat() / effect.durationMs).coerceIn(0f, 1f)
                    val a = 1f - p
                    val rand = kotlin.random.Random(effect.id.hashCode().toLong())
                    for (i in 0 until 8) {
                        val off = Offset((rand.nextFloat() - 0.5f) * ctx.wPx * 1.5f + (rand.nextFloat() - 0.5f) * ctx.wPx * 0.3f * p, (rand.nextFloat() - 0.5f) * ctx.wPx * 1.5f + (rand.nextFloat() - 0.5f) * ctx.wPx * 0.3f * p)
                        drawCircle(effect.color.copy(alpha = effect.color.alpha * a), ctx.wPx * 0.4f * (0.8f + rand.nextFloat() * 0.4f) * (1f + p * 0.5f), pos + off)
                    }
                }
            }

            // 3. World Layer (Sorted)
            activeWorldItems.clear()
            var itemIdx = 0

            hexes.forEach { (coord, tile) ->
                val screenPos = ctx.toScreen(coord)
                if (tile.type == TileType.PILLAR || tile.type == TileType.GOAL_TABLE || tile.stall != null) {
                    val type = when {
                        tile.type == TileType.PILLAR -> WorldItemType.PILLAR
                        tile.type == TileType.GOAL_TABLE -> WorldItemType.GOAL_TABLE
                        else -> WorldItemType.STALL
                    }
                    if (itemIdx < worldItemPool.size) {
                        worldItemPool[itemIdx].set(coord.r.toFloat(), type, coord, tile, null, screenPos)
                        activeWorldItems.add(worldItemPool[itemIdx])
                        itemIdx++
                    }
                }
            }
            enemies.forEach { enemy ->
                if (itemIdx < worldItemPool.size) {
                    worldItemPool[itemIdx].set(enemy.position.r, WorldItemType.ENEMY, null, null, enemy, ctx.toScreenPrecise(enemy.position.q, enemy.position.r))
                    activeWorldItems.add(worldItemPool[itemIdx])
                    itemIdx++
                }
            }
            activeWorldItems.sortBy { it.r }
            activeWorldItems.forEach { item ->
                val screenPos = item.screenPos
                when (item.type) {
                    WorldItemType.PILLAR -> {
                        if (isRemovePillarModeActive) {
                            val p = (ctx.now % 1000) / 1000f
                            val s = 1.0f + 0.1f * sin(p * 2 * PI).toFloat()
                            drawPath(ctx.resetHexPath(screenPos, s), Color.Yellow.copy(alpha = 0.4f))
                        }
                        val r = SpriteConstants.PILLAR_RECT
                        val sc = ctx.wPx / 101f
                        ctx.drawSprite(this, r, screenPos, Size(r.width * sc, r.height * sc), anchor = Offset(0.5f, 0.8f))
                    }
                    WorldItemType.GOAL_TABLE -> {
                        val sc = ctx.wPx / 101f
                        val idx = (10 - health).coerceIn(0, 9)
                        val r = IntRect(0, idx * SpriteConstants.END_TABLE_SPRITE_HEIGHT, SpriteConstants.END_TABLE_SPRITE_WIDTH, (idx + 1) * SpriteConstants.END_TABLE_SPRITE_HEIGHT)
                        val w = 263f * sc
                        ctx.drawSprite(this, r, screenPos, Size(w, w * SpriteConstants.END_TABLE_SPRITE_HEIGHT / SpriteConstants.END_TABLE_SPRITE_WIDTH), anchor = Offset(0.5f, 1.0f), bitmap = ctx.endTableSheet)
                    }
                    WorldItemType.STALL -> {
                        val stall = item.tile?.stall ?: return@forEach
                        val def = StallRegistry.get(stall.stallType)
                        val w = ctx.wPx * 0.8f
                        val sc = w / def.spriteRect.width
                        ctx.drawSprite(this, def.spriteRect, screenPos, Size(w, def.spriteRect.height * sc), anchor = Offset(0.5f, 0.8f), bitmap = ctx.stallsSheet)
                        if (stall.disabledWaves > 0) {
                            val cSc = ctx.wPx / 101f
                            ctx.drawSprite(this, SpriteConstants.FX_CONE_RECT, screenPos, Size(64f * cSc, 62f * cSc))
                        }
                        if (selectedBoardStall == item.coord) {
                            drawPath(ctx.resetHexPath(screenPos), Color.White.copy(alpha = 0.3f))
                        }
                    }
                    WorldItemType.ENEMY -> {
                        val enemy = item.enemy ?: return@forEach
                        val def = EnemyRegistry.get(enemy.type)
                        val frame = ((enemy.animationTimeMs / 500) % SpriteConstants.ENEMY_SPRITE_FRAMES).toInt()
                        val r = IntRect(frame * SpriteConstants.ENEMY_SPRITE_WIDTH, def.spriteRow * SpriteConstants.ENEMY_SPRITE_HEIGHT, (frame + 1) * SpriteConstants.ENEMY_SPRITE_WIDTH, (def.spriteRow + 1) * SpriteConstants.ENEMY_SPRITE_HEIGHT)
                        ctx.drawSprite(this, r, screenPos, Size(SpriteConstants.ENEMY_SPRITE_WIDTH.toFloat(), SpriteConstants.ENEMY_SPRITE_HEIGHT.toFloat()), anchor = Offset(0.5f, 1.0f), bitmap = ctx.enemiesSheet, flipHorizontal = enemy.isFacingLeft)
                        val bW = 2.dp.toPx(); val bH = ctx.hPx * 0.5f
                        val bX = screenPos.x + SpriteConstants.ENEMY_SPRITE_WIDTH / 2f + 4.dp.toPx()
                        val bY = screenPos.y - SpriteConstants.ENEMY_SPRITE_HEIGHT / 2f - bH / 2f
                        drawRect(Color.Black, Offset(bX, bY), Size(bW, bH))
                        drawRect(Color.Red, Offset(bX, bY + bH * (1f - enemy.health.toFloat() / enemy.maxHealth)), Size(bW, bH * (enemy.health.toFloat() / enemy.maxHealth)))
                        if (enemy.buffs.any { it.type == BuffType.ARMOR }) {
                            val s = 10.dp.toPx(); val path = Path().apply {
                                moveTo(bX - s/2f - 2.dp.toPx(), bY + bH/2f - s/2f)
                                lineTo(bX - 2.dp.toPx(), bY + bH/2f - s*0.2f); lineTo(bX - 2.dp.toPx(), bY + bH/2f + s*0.2f)
                                quadraticTo(bX - s/2f - 2.dp.toPx(), bY + bH/2f + s/2f, bX - s - 2.dp.toPx(), bY + bH/2f + s*0.2f)
                                lineTo(bX - s - 2.dp.toPx(), bY + bH/2f - s*0.2f); close()
                            }
                            drawPath(path, Color(0xFF90A4AE)); drawPath(path, Color.White, style = Stroke(1.dp.toPx()))
                        }
                    }
                }
            }

            // 4. Foreground Layer (Direct Draw)
            hexes.forEach { (coord, tile) ->
                tile.stall?.let { stall ->
                    val screenPos = ctx.toScreen(coord)
                    if (selectedBoardStall == coord) {
                        drawables.add(DrawableEntity(
                            q = coord.q.toFloat(),
                            r = coord.r.toFloat(),
                            zOrder = 30,
                            draw = {
                                val radiusX = (stall.range + 0.25f) * wPx
                                val radiusY = (stall.range * rowSpacingFactor + 0.25f) * hPx
                                val ovalSize = Size(radiusX * 2, radiusY * 2)
                                val ovalTopLeft = Offset(screenPos.x - radiusX, screenPos.y - radiusY)

                                // 1. Filled area
                                drawOval(
                                    color = Color.White.copy(alpha = 0.15f),
                                    topLeft = ovalTopLeft,
                                    size = ovalSize
                                )
                                // 2. Yellow border (wider stroke)
                                drawOval(
                                    color = Color.Yellow,
                                    topLeft = ovalTopLeft,
                                    size = ovalSize,
                                    style = Stroke(width = 4.dp.toPx())
                                )
                                // 3. Red outline (thinner stroke on top)
                                drawOval(
                                    color = Color.Red,
                                    topLeft = ovalTopLeft,
                                    size = ovalSize,
                                    style = Stroke(width = 2.dp.toPx())
                                )

                                // LOS Blocked area visualization
                                if (stall.isBlockable) {
                                    val obstructions = hexes.values.filter { it.type is TileType.Obstruction }
                                    val ratio = 91f / 101f
                                    val yFactor = rowSpacingFactor * ratio

                                    // Local coordinates for calculations
                                    val x1 = coord.q + coord.r / 2f
                                    val y1 = coord.r * yFactor

                                    val radius = 0.25f
                                    val stallVisualRadius = 0.4f // Approximate visual radius of the stall tile

                                    obstructions.forEach { obs ->
                                        val px = obs.coordinate.q + obs.coordinate.r / 2f
                                        val py = obs.coordinate.r * yFactor

                                        val dx = px - x1
                                        val dy = py - y1
                                        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                                        if (dist > radius) {
                                            val angleToPillar = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
                                            val alpha = Math.asin((radius / dist).toDouble()).toFloat()

                                            val angle1 = angleToPillar - alpha
                                            val angle2 = angleToPillar + alpha

                                            val startX1 = x1 + Math.cos(angle1.toDouble()).toFloat() * stallVisualRadius
                                            val startY1 = y1 + Math.sin(angle1.toDouble()).toFloat() * stallVisualRadius
                                            val startX2 = x1 + Math.cos(angle2.toDouble()).toFloat() * stallVisualRadius
                                            val startY2 = y1 + Math.sin(angle2.toDouble()).toFloat() * stallVisualRadius

                                            val endX1 = x1 + Math.cos(angle1.toDouble()).toFloat() * stall.range
                                            val endY1 = y1 + Math.sin(angle1.toDouble()).toFloat() * stall.range
                                            val endX2 = x1 + Math.cos(angle2.toDouble()).toFloat() * stall.range
                                            val endY2 = y1 + Math.sin(angle2.toDouble()).toFloat() * stall.range

                                            fun projectedToScreen(x: Float, y: Float): Offset {
                                                val r = y / yFactor
                                                val q = x - r / 2f
                                                return GridUtils.toScreenPrecise(q, r, wPx, hPx, rowSpacingFactor, borderPx)
                                            }

                                            val p1Start = projectedToScreen(startX1, startY1)
                                            val p2Start = projectedToScreen(startX2, startY2)
                                            val p1End = projectedToScreen(endX1, endY1)
                                            val p2End = projectedToScreen(endX2, endY2)

                                            val shadowPath = Path().apply {
                                                moveTo(p1Start.x, p1Start.y)
                                                lineTo(p1End.x, p1End.y)
                                                lineTo(p2End.x, p2End.y)
                                                lineTo(p2Start.x, p2Start.y)
                                                close()
                                            }

                                            drawPath(
                                                path = shadowPath,
                                                color = Color.Black.copy(alpha = 0.2f)
                                            )

                                            drawLine(
                                                color = Color.Red.copy(alpha = 0.5f),
                                                start = p1Start,
                                                end = p1End,
                                                strokeWidth = 2.dp.toPx()
                                            )
                                            drawLine(
                                                color = Color.Red.copy(alpha = 0.5f),
                                                start = p2Start,
                                                end = p2End,
                                                strokeWidth = 2.dp.toPx()
                                            )
                                        }
                                        val path = Path().apply { moveTo(p[0].x, p[0].y); lineTo(p[1].x, p[1].y); lineTo(p[3].x, p[3].y); lineTo(p[2].x, p[2].y); close() }
                                        drawPath(path, Color.Black.copy(alpha = 0.2f))
                                        drawLine(Color.Red.copy(alpha = 0.5f), p[0], p[1], 2.dp.toPx())
                                        drawLine(Color.Red.copy(alpha = 0.5f), p[2], p[3], 2.dp.toPx())
                                    }
                                }
                            }
                        ))
                    }

                    // Upgrade Indicator
                    drawables.add(DrawableEntity(
                        q = coord.q.toFloat(),
                        r = coord.r.toFloat(),
                        zOrder = 31,
                        draw = {
                            val canAfford = gold >= stall.getUpgradeCost()
                            val indicatorColor = if (canAfford) Color.Green else Color.Gray
                            val indicatorRadius = 7.dp.toPx()
                            // Positioned at bottom right relative to center (screenPos), moved closer to center
                            val indicatorPos = Offset(screenPos.x + wPx * 0.25f, screenPos.y + hPx * 0.25f)

                            drawCircle(
                                color = indicatorColor,
                                radius = indicatorRadius,
                                center = indicatorPos
                            )
                            drawCircle(
                                color = Color.White,
                                radius = indicatorRadius,
                                center = indicatorPos,
                                style = Stroke(width = 1.dp.toPx())
                            )

                            drawIntoCanvas { canvas ->
                                upgradePaint.textSize = if (stall.upgradeCount > 9) 7.dp.toPx() else 9.dp.toPx()
                                canvas.nativeCanvas.drawText(
                                    stall.upgradeCount.toString(),
                                    indicatorPos.x,
                                    indicatorPos.y + upgradePaint.textSize / 3f,
                                    upgradePaint
                                )
                            }
                        }
                    }
                    val col = if (gold >= stall.getUpgradeCost()) Color.Green else Color.Gray
                    val pos = Offset(screenPos.x + ctx.wPx * 0.25f, screenPos.y + ctx.hPx * 0.25f)
                    drawCircle(col, 7.dp.toPx(), pos)
                    drawCircle(Color.White, 7.dp.toPx(), pos, style = Stroke(1.dp.toPx()))
                    drawIntoCanvas { it.nativeCanvas.drawText(stall.upgradeCount.toString(), pos.x, pos.y + 3.dp.toPx(), ctx.upgradePaint.apply { textSize = (if (stall.upgradeCount > 9) 7.dp else 9.dp).toPx() }) }
                }
            }
            visualEffects.forEach { effect ->
                val screenPos = toScreenPrecise(effect.position.q, effect.position.r)
                val effectZOrder = when(effect.type) {
                    VisualEffectType.GAS_CLOUD, VisualEffectType.MONEY_SPRAY -> 21
                    else -> 1
                }
                drawables.add(DrawableEntity(
                    q = effect.position.q,
                    r = effect.position.r,
                    zOrder = effectZOrder,
                    draw = {
                        val currentTimeMs = System.currentTimeMillis()
                        val elapsed = currentTimeMs - effect.startTimeMs
                        val progress = (elapsed.toFloat() / effect.durationMs).coerceIn(0f, 1f)
                        val fraction = 1.0f - progress

                        when (effect.type) {
                            VisualEffectType.GAS_CLOUD -> {
                                // Pseudo-random patchy gas effect seeded by effect ID
                                val random = kotlin.random.Random(effect.id.hashCode().toLong())
                                val baseRadius = wPx * 0.4f
                                for (i in 0 until 8) {
                                    val offsetX = (random.nextFloat() - 0.5f) * wPx * 1.5f
                                    val offsetY = (random.nextFloat() - 0.5f) * wPx * 1.5f
                                    val individualScale = 0.8f + random.nextFloat() * 0.4f
                                    val driftX = (random.nextFloat() - 0.5f) * wPx * 0.3f * progress
                                    val driftY = (random.nextFloat() - 0.5f) * wPx * 0.3f * progress

                                    drawCircle(
                                        color = effect.color.copy(alpha = effect.color.alpha * fraction),
                                        radius = baseRadius * individualScale * (1f + progress * 0.5f),
                                        center = Offset(screenPos.x + offsetX + driftX, screenPos.y + offsetY + driftY)
                                    )
                                }
                            }
                            VisualEffectType.MONEY_SPRAY -> {
                                val random = kotlin.random.Random(effect.id.hashCode().toLong())
                                val noteWidth = 64.dp.toPx()
                                val scale = noteWidth / SpriteConstants.DOLLAR_NOTE_RECT.width
                                val noteHeight = SpriteConstants.DOLLAR_NOTE_RECT.height * scale

                                for (i in 0 until 5) {
                                    val angle = (random.nextFloat() * 2 * Math.PI).toFloat()
                                    val dist = progress * wPx * 1.5f
                                    val offsetX = Math.cos(angle.toDouble()).toFloat() * dist
                                    val offsetY = Math.sin(angle.toDouble()).toFloat() * dist

                                    drawSprite(
                                        srcRect = SpriteConstants.DOLLAR_NOTE_RECT,
                                        destCenter = Offset(screenPos.x + offsetX, screenPos.y + offsetY),
                                        destSize = Size(noteWidth, noteHeight),
                                        alpha = fraction
                                    )
                                }
                            }
                            else -> {
                                drawCircle(
                                    color = effect.color.copy(alpha = effect.color.alpha * fraction),
                                    radius = wPx * 1.2f, // Slightly larger than a hex
                                    center = screenPos
                                )
                            }
                        }
                    }
                ))
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

                val enemyDef = EnemyRegistry.get(enemy.type)
                val rowIndex = enemyDef.spriteRow
                val frameIndex = ((enemy.animationTimeMs / 500) % SpriteConstants.ENEMY_SPRITE_FRAMES).toInt()

                val srcRect = IntRect(
                    left = frameIndex * SpriteConstants.ENEMY_SPRITE_WIDTH,
                    top = rowIndex * SpriteConstants.ENEMY_SPRITE_HEIGHT,
                    right = (frameIndex + 1) * SpriteConstants.ENEMY_SPRITE_WIDTH,
                    bottom = (rowIndex + 1) * SpriteConstants.ENEMY_SPRITE_HEIGHT
                )

                drawables.add(DrawableEntity(
                    q = enemy.position.q,
                    r = enemy.position.r,
                    zOrder = 4,
                    draw = {
                        val dSize = Size(SpriteConstants.ENEMY_SPRITE_WIDTH.toFloat(), SpriteConstants.ENEMY_SPRITE_HEIGHT.toFloat())
                        drawSprite(
                            srcRect = srcRect,
                            destCenter = screenPos,
                            destSize = dSize,
                            anchor = Offset(0.5f, 1.0f), // Anchor feet to hex center
                            bitmap = enemiesSheet,
                            flipHorizontal = enemy.isFacingLeft
                        )

                        val barWidth = 2.dp.toPx()
                        val barHeight = hPx * 0.5f
                        val healthRatio = if (enemy.maxHealth > 0f) (enemy.health / enemy.maxHealth).coerceIn(0.0f, 1.0f) else 0f

                        // Positioned on the right side of the enemy, vertically centered
                        val barX = screenPos.x + SpriteConstants.ENEMY_SPRITE_WIDTH / 2f + 4.dp.toPx()
                        val spriteCenterY = screenPos.y - SpriteConstants.ENEMY_SPRITE_HEIGHT / 2f
                        val barY = spriteCenterY - barHeight / 2f

                        // Background (Black)
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(barX, barY),
                            size = Size(barWidth, barHeight)
                        )

                        // Health (Red) - Drains from top to bottom (bottom remains filled)
                        val filledHeight = barHeight * healthRatio
                        drawRect(
                            color = Color.Red,
                            topLeft = Offset(barX, barY + (barHeight - filledHeight)),
                            size = Size(barWidth, filledHeight)

                        )

                        // Health Percentage Text
                        drawIntoCanvas { canvas ->
                            val text = "${Math.round(healthRatio * 100)}%"
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 8.dp.toPx()
                                isFakeBoldText = true
                                setShadowLayer(2f, 0f, 0f, android.graphics.Color.BLACK)
                            }
                            canvas.nativeCanvas.drawText(
                                text,
                                barX + barWidth + 2.dp.toPx(),
                                barY + barHeight,
                                paint
                            )
                        }

                        // Armor Buff Icon (Shield)
                        if (enemy.buffs.any { it.type == BuffType.ARMOR }) {
                            val shieldSize = 10.dp.toPx()
                            val shieldX = barX - shieldSize - 2.dp.toPx()
                            val shieldY = barY + (barHeight - shieldSize) / 2f

                            val shieldPath = Path().apply {
                                moveTo(shieldX + shieldSize / 2f, shieldY) // Top center
                                lineTo(shieldX + shieldSize, shieldY + shieldSize * 0.3f) // Right top
                                lineTo(shieldX + shieldSize, shieldY + shieldSize * 0.7f) // Right bottom
                                quadraticTo(
                                    shieldX + shieldSize / 2f, shieldY + shieldSize, // Bottom center control
                                    shieldX, shieldY + shieldSize * 0.7f // Left bottom
                                )
                                lineTo(shieldX, shieldY + shieldSize * 0.3f) // Left top
                                close()
                            }

                            drawPath(
                                path = shieldPath,
                                color = Color(0xFF90A4AE) // Slate Grey
                            )
                            drawPath(
                                path = shieldPath,
                                color = Color.White,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    } else {
                        drawCircle(effect.color.copy(alpha = effect.color.alpha * a), ctx.wPx * 1.2f, pos)
                    }
                }
            }

            projectiles.forEach { projectile ->
                val currentScreenPos = toScreenPrecise(projectile.position.q, projectile.position.r)
                val lastScreenPos = projectile.lastPosition?.let {
                    toScreenPrecise(it.q, it.r)
                } ?: currentScreenPos

                val startScreenPos = projectile.startPosition?.let { toScreenPrecise(it.q, it.r) } ?: lastScreenPos
                val targetScreenPos = toScreenPrecise(projectile.targetPosition.q, projectile.targetPosition.r)

                drawables.add(DrawableEntity(
                    q = projectile.position.q,
                    r = projectile.position.r,
                    zOrder = 20,
                    draw = {
                        val radius = if (projectile.isArc) 6.dp.toPx() else 4.dp.toPx()
                        // Draw 4 sub-frames between last position and current position for smoothness
                        val steps = 4
                        for (i in 0..steps) {
                            val subFrameFraction = i.toFloat() / steps

                            val lerpX = lastScreenPos.x + (currentScreenPos.x - lastScreenPos.x) * subFrameFraction
                            val lerpY = lastScreenPos.y + (currentScreenPos.y - lastScreenPos.y) * subFrameFraction

                            var finalPos = Offset(lerpX, lerpY)

                            if (projectile.isArc) {
                                // Calculate total distance and progress for arc height
                                val totalDist = Math.sqrt(Math.pow((targetScreenPos.x - startScreenPos.x).toDouble(), 2.0) + Math.pow((targetScreenPos.y - startScreenPos.y).toDouble(), 2.0)).toFloat()
                                if (totalDist > 0) {
                                    val currentDist = Math.sqrt(Math.pow((lerpX - startScreenPos.x).toDouble(), 2.0) + Math.pow((lerpY - startScreenPos.y).toDouble(), 2.0)).toFloat()
                                    val progress = (currentDist / totalDist).coerceIn(0f, 1f)
                                    // Parabola: y = -4 * h * x * (x - 1)
                                    val arcHeight = wPx * 1.5f
                                    val verticalOffset = -4 * arcHeight * progress * (progress - 1)
                                    finalPos = Offset(lerpX, lerpY - verticalOffset)
                                }
                            }

                            drawCircle(
                                color = projectile.color.copy(alpha = 0.4f + 0.6f * subFrameFraction),
                                radius = radius * (0.6f + 0.4f * subFrameFraction),
                                center = finalPos
                            )
                        }
                    }
                ))
            }

            val sortedDrawables = drawables.sortedWith(object : Comparator<DrawableEntity> {
                override fun compare(a: DrawableEntity, b: DrawableEntity): Int {
                    val aGroup = when {
                        a.zOrder == 0 -> 0
                        a.zOrder == 1 -> 1
                        a.zOrder in 2..19 -> 2
                        a.zOrder in 20..29 -> 3
                        else -> 4
                    }
                    val bGroup = when {
                        b.zOrder == 0 -> 0
                        b.zOrder == 1 -> 1
                        b.zOrder in 2..19 -> 2
                        b.zOrder in 20..29 -> 3
                        else -> 4
                    }
                    
                    if (aGroup != bGroup) return aGroup.compareTo(bGroup)
                    if (aGroup == 2) {
                        val rComp = a.r.compareTo(b.r)
                        if (rComp != 0) return rComp
                    }
                    val zComp = a.zOrder.compareTo(b.zOrder)
                    if (zComp != 0) return zComp
                    return a.q.compareTo(b.q)
                }
            }
        }
    }
}
