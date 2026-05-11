package com.messark.hawker.ui.components

import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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

private data class DrawableEntity(
    val q: Float,
    val r: Float,
    val zOrder: Int,
    val draw: DrawScope.() -> Unit
)

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
    val healthTextPaint: android.graphics.Paint,
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
    getOutdoorPuddleChain: (AxialCoordinate) -> List<AxialCoordinate> = { emptyList() },
    isOutdoorPuddleModeActive: Boolean = false,
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
    var hoveredChain by remember { mutableStateOf<List<AxialCoordinate>>(emptyList()) }
    val reusedPath = remember { Path() }
    val density = LocalDensity.current
    val healthTextPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 8.dp.toPx() }
            isFakeBoldText = true
            setShadowLayer(2f, 0f, 0f, android.graphics.Color.BLACK)
        }
    }

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
                .pointerInput(isOutdoorPuddleModeActive) {
                    if (isOutdoorPuddleModeActive) {
                        detectTapGestures(
                            onTap = { offset ->
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
                        )
                    } else {
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
                }
                .pointerInput(isOutdoorPuddleModeActive) {
                    if (isOutdoorPuddleModeActive) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val position = event.changes.first().position
                                val w = hexWidth.toPx()
                                val h = hexHeight.toPx()
                                val b = 20.dp.toPx()

                                val fr = (position.y - b - h / 2f) / (h * rowSpacingFactor)
                                val fq = (position.x - b - w / 2f) / w - fr / 2f

                                val coord = GridUtils.hexRound(fq, fr)
                                hoveredChain = getOutdoorPuddleChain(coord)
                            }
                        }
                    } else {
                        hoveredChain = emptyList()
                    }
                }
        ) {
            val ctx = RenderingContext(
                wPx = hexWidth.toPx(), hPx = hexHeight.toPx(),
                rowSpacingFactor = rowSpacingFactor, borderPx = 20.dp.toPx(),
                now = System.currentTimeMillis(),
                spriteSheet, stallsSheet, enemiesSheet, endTableSheet, upgradePaint, healthTextPaint, spritePaint,
                hexPath = reusedPath
            )
            val drawables = mutableListOf<DrawableEntity>()

            // 1. Background Layer (Group 0)
            hexes.forEach { (coord, tile) ->
                val screenPos = ctx.toScreen(coord)
                if (tile.type !is TileType.EDGE_NW && tile.type !is TileType.EDGE_NE && tile.type !is TileType.EDGE_SW && tile.type !is TileType.EDGE_SE && tile.type !is TileType.EDGE_TOP) {
                    val floorSrc = SpriteConstants.FLOOR_RECTS[tile.floorVariant % SpriteConstants.FLOOR_RECTS.size]
                    ctx.drawSprite(this, floorSrc, screenPos, Size(ctx.wPx + 3.0f, ctx.hPx + 3.0f), clipHex = true)
                } else {
                    val edgeSrc = when (tile.type) {
                        is TileType.EDGE_NW -> SpriteConstants.EDGE_NW_RECT
                        is TileType.EDGE_NE -> SpriteConstants.EDGE_NE_RECT
                        is TileType.EDGE_SW -> SpriteConstants.EDGE_SW_RECT
                        is TileType.EDGE_SE -> SpriteConstants.EDGE_SE_RECT
                        is TileType.EDGE_TOP -> SpriteConstants.EDGE_TOP_RECT
                        else -> null
                    }
                    edgeSrc?.let { rect ->
                        val scale = ctx.wPx / 101f
                        ctx.drawSprite(this, rect, screenPos, Size(rect.width * scale, rect.height * scale), clipHex = true)
                    }
                }

                if (tile.type is TileType.START) {
                    drawPath(path = ctx.resetHexPath(screenPos), color = Color.Green.copy(alpha = 0.3f))
                }
                if (tile.type is TileType.DRAIN) {
                    val s = ctx.wPx * 0.3f
                    drawRect(Color.DarkGray, Offset(screenPos.x - s / 2f, screenPos.y - s / 2f), Size(s, s))
                    for (i in 1..3) {
                        val x = screenPos.x - s / 2f + i * (s / 4f)
                        drawLine(Color.Black, Offset(x, screenPos.y - s / 2f), Offset(x, screenPos.y + s / 2f), 1.dp.toPx())
                    }
                }
                if (tile.isPermanentlyWet) {
                    val scale = ctx.wPx / 101f
                    ctx.drawSprite(this, SpriteConstants.FX_PUDDLE_RECT, screenPos, Size(64f * scale, 62f * scale), clipHex = true)
                }
                if (hoveredChain.contains(coord)) {
                    drawPath(path = ctx.resetHexPath(screenPos), color = Color.Cyan.copy(alpha = 0.4f))
                }
            }

            // 3. World Layer (Group 2)
            val obstructions = hexes.values.filter { it.type is TileType.Obstruction }
            hexes.forEach { (coord, tile) ->
                val screenPos = ctx.toScreen(coord)
                if (tile.type is TileType.PILLAR) {
                    drawables.add(DrawableEntity(coord.q.toFloat(), coord.r.toFloat(), 2) {
                        if (isRemovePillarModeActive) {
                            val p = (ctx.now % 1000) / 1000f
                            val s = 1.0f + 0.1f * sin(p * 2 * PI).toFloat()
                            drawPath(ctx.resetHexPath(screenPos, s), Color.Yellow.copy(alpha = 0.4f))
                        }
                        val r = SpriteConstants.PILLAR_RECT; val sc = ctx.wPx / 101f
                        ctx.drawSprite(this, r, screenPos, Size(r.width * sc, r.height * sc), anchor = Offset(0.5f, 0.8f))
                    })
                } else if (tile.type is TileType.GOAL_TABLE) {
                    drawables.add(DrawableEntity(coord.q.toFloat(), coord.r.toFloat(), 2) {
                        val sc = ctx.wPx / 101f; val idx = (10 - health).coerceIn(0, 9)
                        val r = IntRect(0, idx * SpriteConstants.END_TABLE_SPRITE_HEIGHT, SpriteConstants.END_TABLE_SPRITE_WIDTH, (idx + 1) * SpriteConstants.END_TABLE_SPRITE_HEIGHT)
                        ctx.drawSprite(this, r, screenPos, Size(263f * sc, 263f * sc * SpriteConstants.END_TABLE_SPRITE_HEIGHT / SpriteConstants.END_TABLE_SPRITE_WIDTH), anchor = Offset(0.5f, 0.8f), bitmap = ctx.endTableSheet)
                    })
                }
                tile.stall?.let { stall ->
                    drawables.add(DrawableEntity(coord.q.toFloat(), coord.r.toFloat(), 2) {
                        val def = StallRegistry.get(stall.stallType); val w = ctx.wPx * 0.8f; val sc = w / def.spriteRect.width
                        ctx.drawSprite(this, def.spriteRect, screenPos, Size(w, def.spriteRect.height * sc), anchor = Offset(0.5f, 0.8f), bitmap = ctx.stallsSheet)
                        if (stall.disabledWaves > 0) {
                            val cSc = ctx.wPx / 101f; ctx.drawSprite(this, SpriteConstants.FX_CONE_RECT, screenPos, Size(64f * cSc, 62f * cSc))
                        }
                        if (selectedBoardStall == coord) {
                            drawPath(ctx.resetHexPath(screenPos), Color.White.copy(alpha = 0.3f))
                        }
                    })
                }
            }
            enemies.forEach { enemy ->
                val screenPos = ctx.toScreenPrecise(enemy.position.q, enemy.position.r)
                drawables.add(DrawableEntity(enemy.position.q, enemy.position.r, 2) {
                    val def = EnemyRegistry.get(enemy.type); val frame = ((enemy.animationTimeMs / 500) % SpriteConstants.ENEMY_SPRITE_FRAMES).toInt()
                    val r = IntRect(frame * SpriteConstants.ENEMY_SPRITE_WIDTH, def.spriteRow * SpriteConstants.ENEMY_SPRITE_HEIGHT, (frame + 1) * SpriteConstants.ENEMY_SPRITE_WIDTH, (def.spriteRow + 1) * SpriteConstants.ENEMY_SPRITE_HEIGHT)
                    ctx.drawSprite(this, r, screenPos, Size(SpriteConstants.ENEMY_SPRITE_WIDTH.toFloat(), SpriteConstants.ENEMY_SPRITE_HEIGHT.toFloat()), anchor = Offset(0.5f, 1.0f), bitmap = ctx.enemiesSheet, flipHorizontal = enemy.isFacingLeft)
                    val bW = 2.dp.toPx(); val bH = ctx.hPx * 0.5f; val hR = if (enemy.maxHealth > 0f) (enemy.health / enemy.maxHealth).coerceIn(0f, 1f) else 0f
                    val bX = screenPos.x + SpriteConstants.ENEMY_SPRITE_WIDTH / 2f + 4.dp.toPx()
                    val bY = screenPos.y - SpriteConstants.ENEMY_SPRITE_HEIGHT / 2f - bH / 2f
                    drawRect(Color.Black, Offset(bX, bY), Size(bW, bH))
                    drawRect(Color.Red, Offset(bX, bY + bH * (1f - hR)), Size(bW, bH * hR))
                    drawIntoCanvas { canvas ->
                        val text = "${Math.round(hR * 100)}%"
                        canvas.nativeCanvas.drawText(text, bX + bW + 2.dp.toPx(), bY + bH, ctx.healthTextPaint)
                    }
                    if (enemy.buffs.any { it.type == BuffType.ARMOR }) {
                        val s = 10.dp.toPx(); val sX = bX - s - 2.dp.toPx(); val sY = bY + (bH - s) / 2f
                        val path = Path().apply {
                            moveTo(sX + s/2f, sY); lineTo(sX + s, sY + s*0.3f); lineTo(sX + s, sY + s*0.7f)
                            quadraticTo(sX + s/2f, sY + s, sX, sY + s*0.7f); lineTo(sX, sY + s*0.3f); close()
                        }
                        drawPath(path, Color(0xFF90A4AE)); drawPath(path, Color.White, style = Stroke(1.dp.toPx()))
                    }
                })
            }

            // 4. Foreground Layer (Projectiles, UI, Effects)
            puddles.forEach { puddle ->
                val screenPos = ctx.toScreenPrecise(puddle.position.q, puddle.position.r)
                drawables.add(DrawableEntity(puddle.position.q, puddle.position.r, 1) {
                    val scale = ctx.wPx / 101f
                    ctx.drawSprite(this, SpriteConstants.FX_PUDDLE_RECT, screenPos, Size(64f * scale, 62f * scale), clipHex = true)
                })
            }

            visualEffects.forEach { effect ->
                val screenPos = ctx.toScreenPrecise(effect.position.q, effect.position.r)
                val z = if (effect.type == VisualEffectType.GAS_CLOUD || effect.type == VisualEffectType.MONEY_SPRAY) 21 else 1
                drawables.add(DrawableEntity(effect.position.q, effect.position.r, z) {
                    val progress = ((ctx.now - effect.startTimeMs).toFloat() / effect.durationMs).coerceIn(0f, 1f)
                    val alpha = 1f - progress
                    when (effect.type) {
                        VisualEffectType.GAS_CLOUD -> {
                            val rand = kotlin.random.Random(effect.id.hashCode().toLong())
                            for (i in 0 until 8) {
                                val off = Offset((rand.nextFloat() - 0.5f) * ctx.wPx * 1.5f + (rand.nextFloat() - 0.5f) * ctx.wPx * 0.3f * progress, (rand.nextFloat() - 0.5f) * ctx.wPx * 1.5f + (rand.nextFloat() - 0.5f) * ctx.wPx * 0.3f * progress)
                                drawCircle(effect.color.copy(alpha = effect.color.alpha * alpha), ctx.wPx * 0.4f * (0.8f + rand.nextFloat() * 0.4f) * (1f + progress * 0.5f), screenPos + off)
                            }
                        }
                        VisualEffectType.MONEY_SPRAY -> {
                            val rand = kotlin.random.Random(effect.id.hashCode().toLong())
                            val nW = 64.dp.toPx(); val nH = SpriteConstants.DOLLAR_NOTE_RECT.height * (nW / SpriteConstants.DOLLAR_NOTE_RECT.width)
                            for (i in 0 until 5) {
                                val ang = (rand.nextFloat() * 2 * PI).toFloat()
                                val dst = progress * ctx.wPx * 1.5f
                                ctx.drawSprite(this, SpriteConstants.DOLLAR_NOTE_RECT, Offset(screenPos.x + cos(ang) * dst, screenPos.y + sin(ang) * dst), Size(nW, nH), alpha = alpha)
                            }
                        }
                        else -> drawCircle(effect.color.copy(alpha = effect.color.alpha * alpha), ctx.wPx * 1.2f, screenPos)
                    }
                })
            }

            hexes.forEach { (coord, tile) ->
                tile.stall?.let { stall ->
                    val screenPos = ctx.toScreen(coord)
                    if (selectedBoardStall == coord) {
                        drawables.add(DrawableEntity(coord.q.toFloat(), coord.r.toFloat(), 30) {
                            val rx = (stall.range + 0.25f) * ctx.wPx; val ry = (stall.range * rowSpacingFactor + 0.25f) * ctx.hPx
                            drawOval(Color.White.copy(alpha = 0.15f), Offset(screenPos.x - rx, screenPos.y - ry), Size(rx * 2, ry * 2))
                            drawOval(Color.Yellow, Offset(screenPos.x - rx, screenPos.y - ry), Size(rx * 2, ry * 2), style = Stroke(4.dp.toPx()))
                            drawOval(Color.Red, Offset(screenPos.x - rx, screenPos.y - ry), Size(rx * 2, ry * 2), style = Stroke(2.dp.toPx()))
                            if (stall.isBlockable) {
                                val yF = rowSpacingFactor * (91f / 101f)
                                val x1 = coord.q + coord.r / 2f; val y1 = coord.r * yF
                                obstructions.forEach { obs ->
                                    val px = obs.coordinate.q + obs.coordinate.r / 2f; val py = obs.coordinate.r * yF
                                    val dx = px - x1; val dy = py - y1; val dist = sqrt(dx * dx + dy * dy)
                                    if (dist > 0.25f) {
                                        val angP = atan2(dy, dx); val alp = asin(0.25f / dist)
                                        val a1 = angP - alp; val a2 = angP + alp
                                        val p = listOf(
                                            Offset(x1 + cos(a1) * 0.4f, y1 + sin(a1) * 0.4f), Offset(x1 + cos(a1) * stall.range, y1 + sin(a1) * stall.range),
                                            Offset(x1 + cos(a2) * 0.4f, y1 + sin(a2) * 0.4f), Offset(x1 + cos(a2) * stall.range, y1 + sin(a2) * stall.range)
                                        ).map { GridUtils.toScreenPrecise(it.x - (it.y / yF) / 2f, it.y / yF, ctx.wPx, ctx.hPx, rowSpacingFactor, ctx.borderPx) }
                                        val path = Path().apply { moveTo(p[0].x, p[0].y); lineTo(p[1].x, p[1].y); lineTo(p[3].x, p[3].y); lineTo(p[2].x, p[2].y); close() }
                                        drawPath(path, Color.Black.copy(alpha = 0.2f))
                                        drawLine(Color.Red.copy(alpha = 0.5f), p[0], p[1], 2.dp.toPx())
                                        drawLine(Color.Red.copy(alpha = 0.5f), p[2], p[3], 2.dp.toPx())
                                    }
                                }
                            }
                        })
                    }
                    drawables.add(DrawableEntity(coord.q.toFloat(), coord.r.toFloat(), 31) {
                        val col = if (gold >= stall.getUpgradeCost()) Color.Green else Color.Gray
                        val pos = Offset(screenPos.x + ctx.wPx * 0.25f, screenPos.y + ctx.hPx * 0.25f)
                        drawCircle(col, 7.dp.toPx(), pos)
                        drawCircle(Color.White, 7.dp.toPx(), pos, style = Stroke(1.dp.toPx()))
                        drawIntoCanvas { it.nativeCanvas.drawText(stall.upgradeCount.toString(), pos.x, pos.y + 3.dp.toPx(), ctx.upgradePaint.apply { textSize = (if (stall.upgradeCount > 9) 7.dp else 9.dp).toPx() }) }
                    })
                }
            }

            projectiles.forEach { projectile ->
                val curr = ctx.toScreenPrecise(projectile.position.q, projectile.position.r)
                val last = projectile.lastPosition?.let { ctx.toScreenPrecise(it.q, it.r) } ?: curr
                val start = projectile.startPosition?.let { ctx.toScreenPrecise(it.q, it.r) } ?: last
                val target = ctx.toScreenPrecise(projectile.targetPosition.q, projectile.targetPosition.r)

                drawables.add(DrawableEntity(projectile.position.q, projectile.position.r, 20) {
                    val radius = (if (projectile.isArc) 6.dp else 4.dp).toPx()
                    for (i in 0..4) {
                        val frac = i.toFloat() / 4f
                        val lx = last.x + (curr.x - last.x) * frac; val ly = last.y + (curr.y - last.y) * frac
                        var fPos = Offset(lx, ly)
                        if (projectile.isArc) {
                            val tDist = sqrt((target.x - start.x).pow(2f) + (target.y - start.y).pow(2f))
                            if (tDist > 0) {
                                val cDist = sqrt((lx - start.x).pow(2f) + (ly - start.y).pow(2f))
                                val prog = (cDist / tDist).coerceIn(0f, 1f)
                                val aH = ctx.wPx * 1.5f
                                fPos = Offset(lx, ly - (-4 * aH * prog * (prog - 1)))
                            }
                        }
                        drawCircle(projectile.color.copy(alpha = 0.4f + 0.6f * frac), radius * (0.6f + 0.4f * frac), fPos)
                    }
                })
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
            })
            sortedDrawables.forEach { it.draw(this) }
        }
    }
}
