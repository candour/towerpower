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
    val spritePaint: android.graphics.Paint
) {
    fun toScreen(coord: AxialCoordinate): Offset =
        GridUtils.toScreenPrecise(coord.q.toFloat(), coord.r.toFloat(), wPx, hPx, rowSpacingFactor, borderPx)

    fun toScreenPrecise(q: Float, r: Float): Offset =
        GridUtils.toScreenPrecise(q, r, wPx, hPx, rowSpacingFactor, borderPx)

    fun createHexPath(center: Offset): Path {
        val bleed = 3.5f
        val w = wPx + bleed
        val h = hPx + bleed
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
            drawScope.clipPath(createHexPath(destCenter)) {
                drawBlock()
            }
        } else {
            drawScope.drawBlock()
        }
    }
}

private data class DrawableEntity(
    val r: Float,
    val draw: DrawScope.() -> Unit
)

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
                        val r = round((offset.y - b - h / 2f) / (h * rowSpacingFactor)).toInt()
                        val q = round((offset.x - b - w / 2f) / w - r / 2f).toInt()
                        onCellClick(AxialCoordinate(q, r))
                    }
                }
        ) {
            val ctx = RenderingContext(
                wPx = hexWidth.toPx(), hPx = hexHeight.toPx(),
                rowSpacingFactor = rowSpacingFactor, borderPx = 20.dp.toPx(),
                now = System.currentTimeMillis(),
                spriteSheet, stallsSheet, enemiesSheet, endTableSheet, upgradePaint, spritePaint
            )

            val backgroundLayer = mutableListOf<DrawScope.() -> Unit>()
            val decalLayer = mutableListOf<DrawScope.() -> Unit>()
            val worldLayer = mutableListOf<DrawableEntity>()
            val foregroundLayer = mutableListOf<DrawScope.() -> Unit>()

            hexes.forEach { (coord, tile) ->
                val screenPos = ctx.toScreen(coord)

                // 1. Background (Floor & Edges)
                if (!tile.type.name.startsWith("EDGE_")) {
                    backgroundLayer.add {
                        val floorSrc = SpriteConstants.FLOOR_RECTS[tile.floorVariant % SpriteConstants.FLOOR_RECTS.size]
                        ctx.drawSprite(this, floorSrc, screenPos, Size(ctx.wPx + 3.0f, ctx.hPx + 3.0f), clipHex = true)
                    }
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
                        backgroundLayer.add {
                            val scale = ctx.wPx / 101f
                            ctx.drawSprite(this, rect, screenPos, Size(rect.width * scale, rect.height * scale), clipHex = true)
                        }
                    }
                }

                // 2. Decals
                if (tile.type == TileType.START) {
                    decalLayer.add {
                        drawPath(path = ctx.createHexPath(screenPos), color = Color.Green.copy(alpha = 0.3f))
                    }
                }
                if (tile.type == TileType.DRAIN) {
                    decalLayer.add {
                        val s = ctx.wPx * 0.3f
                        drawRect(Color.DarkGray, Offset(screenPos.x - s / 2f, screenPos.y - s / 2f), Size(s, s))
                        for (i in 1..3) {
                            val x = screenPos.x - s / 2f + i * (s / 4f)
                            drawLine(Color.Black, Offset(x, screenPos.y - s / 2f), Offset(x, screenPos.y + s / 2f), 1.dp.toPx())
                        }
                    }
                }

                // 3. World (Pillars, Goals, Stalls)
                if (tile.type == TileType.PILLAR) {
                    worldLayer.add(DrawableEntity(coord.r.toFloat()) {
                        if (isRemovePillarModeActive) {
                            val p = (ctx.now % 1000) / 1000f
                            val s = 1.0f + 0.1f * sin(p * 2 * PI).toFloat()
                            drawPath(ctx.createHexPath(screenPos), Color.Yellow.copy(alpha = 0.4f)) // simplified pulse
                        }
                        val r = SpriteConstants.PILLAR_RECT
                        val sc = ctx.wPx / 101f
                        ctx.drawSprite(this, r, screenPos, Size(r.width * sc, r.height * sc), anchor = Offset(0.5f, 0.8f))
                    })
                }

                if (tile.type == TileType.GOAL_TABLE) {
                    worldLayer.add(DrawableEntity(coord.r.toFloat()) {
                        val sc = ctx.wPx / 101f
                        val idx = (10 - health).coerceIn(0, 9)
                        val r = IntRect(0, idx * SpriteConstants.END_TABLE_SPRITE_HEIGHT, SpriteConstants.END_TABLE_SPRITE_WIDTH, (idx + 1) * SpriteConstants.END_TABLE_SPRITE_HEIGHT)
                        val w = 263f * sc
                        ctx.drawSprite(this, r, screenPos, Size(w, w * SpriteConstants.END_TABLE_SPRITE_HEIGHT / SpriteConstants.END_TABLE_SPRITE_WIDTH), anchor = Offset(0.5f, 1.0f), bitmap = ctx.endTableSheet)
                    })
                }

                tile.stall?.let { stall ->
                    worldLayer.add(DrawableEntity(coord.r.toFloat()) {
                        val def = StallRegistry.get(stall.stallType)
                        val w = ctx.wPx * 0.8f
                        val sc = w / def.spriteRect.width
                        ctx.drawSprite(this, def.spriteRect, screenPos, Size(w, def.spriteRect.height * sc), anchor = Offset(0.5f, 0.8f), bitmap = ctx.stallsSheet)

                        if (stall.disabledWaves > 0) {
                            val cSc = ctx.wPx / 101f
                            ctx.drawSprite(this, SpriteConstants.FX_CONE_RECT, screenPos, Size(64f * cSc, 62f * cSc))
                        }
                        if (selectedBoardStall == coord) {
                            drawPath(ctx.createHexPath(screenPos), Color.White.copy(alpha = 0.3f))
                        }
                    })

                    if (selectedBoardStall == coord) {
                        foregroundLayer.add {
                            val rx = (stall.range + 0.25f) * ctx.wPx
                            val ry = (stall.range * ctx.rowSpacingFactor + 0.25f) * ctx.hPx
                            val s = Size(rx * 2, ry * 2)
                            val tl = Offset(screenPos.x - rx, screenPos.y - ry)
                            drawOval(Color.White.copy(alpha = 0.15f), tl, s)
                            drawOval(Color.Yellow, tl, s, style = Stroke(4.dp.toPx()))
                            drawOval(Color.Red, tl, s, style = Stroke(2.dp.toPx()))

                            if (stall.isBlockable) {
                                val ratio = 91f / 101f
                                val yF = ctx.rowSpacingFactor * ratio
                                val x1 = coord.q + coord.r / 2f
                                val y1 = coord.r * yF

                                hexes.values.filter { it.type is TileType.Obstruction }.forEach { obs ->
                                    val dx = (obs.coordinate.q + obs.coordinate.r / 2f) - x1
                                    val dy = (obs.coordinate.r * yF) - y1
                                    val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                                    if (dist > 0.25f) {
                                        val angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                                        val a = asin((0.25f / dist).toDouble()).toFloat()
                                        val p = listOf(angle - a, angle + a).flatMap { ang ->
                                            listOf(
                                                ctx.toScreenPrecise(coord.q + cos(ang.toDouble()).toFloat() * 0.4f, coord.r + sin(ang.toDouble()).toFloat() * 0.4f / ratio),
                                                ctx.toScreenPrecise(coord.q + cos(ang.toDouble()).toFloat() * stall.range, coord.r + sin(ang.toDouble()).toFloat() * stall.range / ratio)
                                            )
                                        }
                                        val path = Path().apply { moveTo(p[0].x, p[0].y); lineTo(p[1].x, p[1].y); lineTo(p[3].x, p[3].y); lineTo(p[2].x, p[2].y); close() }
                                        drawPath(path, Color.Black.copy(alpha = 0.2f))
                                        drawLine(Color.Red.copy(alpha = 0.5f), p[0], p[1], 2.dp.toPx())
                                        drawLine(Color.Red.copy(alpha = 0.5f), p[2], p[3], 2.dp.toPx())
                                    }
                                }
                            }
                        }
                    }

                    foregroundLayer.add {
                        val col = if (gold >= stall.getUpgradeCost()) Color.Green else Color.Gray
                        val pos = Offset(screenPos.x + ctx.wPx * 0.25f, screenPos.y + ctx.hPx * 0.25f)
                        drawCircle(col, 7.dp.toPx(), pos)
                        drawCircle(Color.White, 7.dp.toPx(), pos, style = Stroke(1.dp.toPx()))
                        drawIntoCanvas { it.nativeCanvas.drawText(stall.upgradeCount.toString(), pos.x, pos.y + 3.dp.toPx(), ctx.upgradePaint.apply { textSize = (if (stall.upgradeCount > 9) 7.dp else 9.dp).toPx() }) }
                    }
                }
            }

            puddles.forEach { puddle ->
                decalLayer.add {
                    val sc = ctx.wPx / 101f
                    ctx.drawSprite(this, SpriteConstants.FX_PUDDLE_RECT, ctx.toScreenPrecise(puddle.position.q, puddle.position.r), Size(64f * sc, 62f * sc), clipHex = true)
                }
            }

            enemies.forEach { enemy ->
                worldLayer.add(DrawableEntity(enemy.position.r) {
                    val pos = ctx.toScreenPrecise(enemy.position.q, enemy.position.r)
                    val def = EnemyRegistry.get(enemy.type)
                    val frame = ((enemy.animationTimeMs / 500) % SpriteConstants.ENEMY_SPRITE_FRAMES).toInt()
                    val r = IntRect(frame * SpriteConstants.ENEMY_SPRITE_WIDTH, def.spriteRow * SpriteConstants.ENEMY_SPRITE_HEIGHT, (frame + 1) * SpriteConstants.ENEMY_SPRITE_WIDTH, (def.spriteRow + 1) * SpriteConstants.ENEMY_SPRITE_HEIGHT)
                    ctx.drawSprite(this, r, pos, Size(SpriteConstants.ENEMY_SPRITE_WIDTH.toFloat(), SpriteConstants.ENEMY_SPRITE_HEIGHT.toFloat()), anchor = Offset(0.5f, 1.0f), bitmap = ctx.enemiesSheet, flipHorizontal = enemy.isFacingLeft)

                    val bW = 2.dp.toPx(); val bH = ctx.hPx * 0.5f
                    val bX = pos.x + SpriteConstants.ENEMY_SPRITE_WIDTH / 2f + 4.dp.toPx()
                    val bY = pos.y - SpriteConstants.ENEMY_SPRITE_HEIGHT / 2f - bH / 2f
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
                })
            }

            visualEffects.forEach { effect ->
                val targetLayer = if (effect.type == VisualEffectType.GAS_CLOUD) decalLayer else foregroundLayer
                targetLayer.add {
                    val pos = ctx.toScreenPrecise(effect.position.q, effect.position.r)
                    val p = ((ctx.now - effect.startTimeMs).toFloat() / effect.durationMs).coerceIn(0f, 1f)
                    val a = 1f - p
                    when (effect.type) {
                        VisualEffectType.GAS_CLOUD -> {
                            val rand = kotlin.random.Random(effect.id.hashCode().toLong())
                            for (i in 0 until 8) {
                                val off = Offset((rand.nextFloat() - 0.5f) * ctx.wPx * 1.5f + (rand.nextFloat() - 0.5f) * ctx.wPx * 0.3f * p, (rand.nextFloat() - 0.5f) * ctx.wPx * 1.5f + (rand.nextFloat() - 0.5f) * ctx.wPx * 0.3f * p)
                                drawCircle(effect.color.copy(alpha = effect.color.alpha * a), ctx.wPx * 0.4f * (0.8f + rand.nextFloat() * 0.4f) * (1f + p * 0.5f), pos + off)
                            }
                        }
                        VisualEffectType.MONEY_SPRAY -> {
                            val rand = kotlin.random.Random(effect.id.hashCode().toLong())
                            val nS = Size(64.dp.toPx(), 64.dp.toPx() * SpriteConstants.DOLLAR_NOTE_RECT.height / SpriteConstants.DOLLAR_NOTE_RECT.width)
                            for (i in 0 until 5) {
                                val ang = rand.nextFloat() * 2 * PI; val dist = p * ctx.wPx * 1.5f
                                ctx.drawSprite(this, SpriteConstants.DOLLAR_NOTE_RECT, pos + Offset(cos(ang).toFloat() * dist, sin(ang).toFloat() * dist), nS, alpha = a)
                            }
                        }
                        else -> drawCircle(effect.color.copy(alpha = effect.color.alpha * a), ctx.wPx * 1.2f, pos)
                    }
                }
            }

            projectiles.forEach { proj ->
                foregroundLayer.add {
                    val curr = ctx.toScreenPrecise(proj.position.q, proj.position.r)
                    val last = proj.lastPosition?.let { ctx.toScreenPrecise(it.q, it.r) } ?: curr
                    for (i in 0..4) {
                        val f = i / 4f
                        var p = Offset(last.x + (curr.x - last.x) * f, last.y + (curr.y - last.y) * f)
                        if (proj.isArc) {
                            val total = GridUtils.axialDistance(proj.startPosition ?: proj.position, proj.targetPosition)
                            if (total > 0) {
                                val progress = (GridUtils.axialDistance(proj.startPosition ?: proj.position, proj.position) / total).coerceIn(0f, 1f)
                                p = p.copy(y = p.y - 4 * ctx.wPx * 1.5f * progress * (progress - 1))
                            }
                        }
                        drawCircle(proj.color.copy(alpha = 0.4f + 0.6f * f), (if (proj.isArc) 6.dp else 4.dp).toPx() * (0.6f + 0.4f * f), p)
                    }
                }
            }

            // --- Execution ---
            backgroundLayer.forEach { it(this) }
            decalLayer.forEach { it(this) }
            worldLayer.sortBy { it.r }
            worldLayer.forEach { it.draw(this) }
            foregroundLayer.forEach { it(this) }
        }
    }
}
