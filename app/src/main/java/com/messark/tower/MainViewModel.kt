package com.messark.tower

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messark.tower.model.*
import com.messark.tower.utils.MapGenerator
import com.messark.tower.utils.Pathfinding
import com.messark.tower.utils.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val settingsRepository: SettingsRepository = SettingsRepository(application)
) : AndroidViewModel(application) {
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _availableTowers = MutableStateFlow(
        listOf(
            Tower("t1", "Teh Tarik", 150, Color.Blue, stallType = StallType.TEH_TARIK, range = 3f, description = "Creates slowing puddles"),
            Tower("t2", "Satay", 200, Color.Red, stallType = StallType.SATAY, range = 2.5f, damage = 5, fireRateMs = 500, description = "Fast area damage"),
            Tower("t3", "Chicken Rice", 100, Color.Yellow, stallType = StallType.CHICKEN_RICE, range = 4f, damage = 15, fireRateMs = 700, description = "High single-target damage"),
            Tower("t4", "Durian", 300, Color(0xFF4CAF50), stallType = StallType.DURIAN, range = 3f, damage = 25, fireRateMs = 2000, description = "Massive damage, slow fire"),
            Tower("t5", "Ice Kachang", 250, Color.Cyan, stallType = StallType.ICE_KACHANG, range = 3.5f, damage = 2, fireRateMs = 1500, description = "Freezes enemies in place")
        )
    )
    val availableTowers: StateFlow<List<Tower>> = _availableTowers.asStateFlow()

    private var gameJob: Job? = null
    private var lastHapticTimeMs = 0L

    private val _hapticEvents = MutableSharedFlow<Unit>()
    val hapticEvents: SharedFlow<Unit> = _hapticEvents.asSharedFlow()

    init {
        initializeGame()
        startGameLoop()
    }

    private fun initializeGame() {
        val (hexes, startPos, endPos) = MapGenerator.generateRandomVerticalMap(width = 8, height = 16)

        _gameState.update { it.copy(
            hexes = hexes,
            startPosition = startPos,
            endPosition = endPos,
            gold = 500 // Start with some gold to place towers
        ) }
    }

    fun selectTower(tower: Tower) {
        _gameState.update { it.copy(selectedTowerType = tower) }
    }

    fun startWave() {
        val currentState = _gameState.value
        if (currentState.waveActive) return

        val newWave = currentState.currentWave + 1
        _gameState.update {
            it.copy(
                waveActive = true,
                currentWave = newWave,
                enemiesToSpawn = 5,
                lastSpawnTimeMs = System.currentTimeMillis()
            )
        }
    }

    private fun startGameLoop() {
        gameJob?.cancel()
        gameJob = viewModelScope.launch {
            while (isActive) {
                val startTime = System.currentTimeMillis()
                updateGame(startTime)
                val delayTime = 32L - (System.currentTimeMillis() - startTime)
                if (delayTime > 0) delay(delayTime)
            }
        }
    }

    private fun updateGame(currentTimeMs: Long) {
        _gameState.update { state ->
            var newState = state

            // 0. Update Puddles
            val updatedPuddles = state.puddles.filter { currentTimeMs - it.spawnTimeMs < it.durationMs }
            newState = newState.copy(puddles = updatedPuddles)

            // 1. Spawning
            if (state.waveActive && state.enemiesToSpawn > 0 && currentTimeMs - state.lastSpawnTimeMs > 1000 && state.hexes.isNotEmpty()) {
                val startPos = state.startPosition ?: return@update state
                val endPos = state.endPosition ?: return@update state
                val path = Pathfinding.findPath(
                    startPos, endPos, getBlockedCoordinates(state.hexes), state.hexes.keys
                ) ?: emptyList()

                val type = when {
                    state.enemiesToSpawn == 1 && state.currentWave % 5 == 0 -> EnemyType.DELIVERY_RIDER
                    Random().nextFloat() < 0.2f -> EnemyType.AUNTIE
                    Random().nextFloat() < 0.4f -> EnemyType.TOURIST
                    else -> EnemyType.SALARYMAN
                }

                val enemyHealth = when (type) {
                    EnemyType.SALARYMAN -> 50 + (state.currentWave - 1) * 10
                    EnemyType.TOURIST -> 100 + (state.currentWave - 1) * 20
                    EnemyType.AUNTIE -> 150 + (state.currentWave - 1) * 30
                    EnemyType.DELIVERY_RIDER -> 500 + (state.currentWave - 1) * 100
                }

                val speed = when (type) {
                    EnemyType.SALARYMAN -> 0.08f
                    EnemyType.TOURIST -> 0.04f
                    EnemyType.AUNTIE -> 0.03f
                    EnemyType.DELIVERY_RIDER -> 0.06f
                }

                val newEnemy = Enemy(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    health = enemyHealth,
                    maxHealth = enemyHealth,
                    position = PreciseAxialCoordinate(startPos.q.toFloat(), startPos.r.toFloat()),
                    baseSpeed = speed,
                    currentSpeed = speed,
                    path = path,
                    currentPathIndex = 0,
                    reward = if (type == EnemyType.DELIVERY_RIDER) 100 else 20
                )
                newState = newState.copy(
                    enemies = newState.enemies + newEnemy,
                    enemiesToSpawn = newState.enemiesToSpawn - 1,
                    lastSpawnTimeMs = currentTimeMs
                )
            }

            // 2. Enemy Movement
            val updatedEnemies = newState.enemies.mapNotNull { enemy ->
                if (enemy.isDead) return@mapNotNull null

                if (enemy.freezeDurationMs > 0) {
                    return@mapNotNull enemy.copy(freezeDurationMs = Math.max(0, enemy.freezeDurationMs - 32))
                }

                var isStopped = enemy.isStopped
                var stopDurationMs = enemy.stopDurationMs
                var lastStopMs = enemy.lastStopMs

                if (enemy.type == EnemyType.TOURIST) {
                    if (isStopped) {
                        stopDurationMs -= 32
                        if (stopDurationMs <= 0) {
                            isStopped = false
                            lastStopMs = currentTimeMs
                        }
                    } else if (currentTimeMs - lastStopMs > 8000) {
                        isStopped = true
                        stopDurationMs = 2000L
                    }
                }

                if (isStopped) {
                    return@mapNotNull enemy.copy(isStopped = isStopped, stopDurationMs = stopDurationMs, lastStopMs = lastStopMs)
                }

                var speedMultiplier = 1.0f
                if (enemy.type != EnemyType.DELIVERY_RIDER) {
                    val inPuddle = newState.puddles.any { puddle ->
                        axialDistance(enemy.position, puddle.position) < 0.8
                    }
                    if (inPuddle) speedMultiplier = 0.6f
                }
                val effectiveSpeed = enemy.baseSpeed * speedMultiplier

                val targetIndex = enemy.currentPathIndex + 1
                if (targetIndex >= enemy.path.size) {
                    newState = newState.copy(health = Math.max(0, newState.health - 1))
                    return@mapNotNull null
                }

                val target = enemy.path[targetIndex]
                val dq = target.q - enemy.position.q
                val dr = target.r - enemy.position.r
                val dist = axialDistance(enemy.position, PreciseAxialCoordinate(target.q.toFloat(), target.r.toFloat()))

                if (dist < effectiveSpeed) {
                    enemy.copy(
                        position = PreciseAxialCoordinate(target.q.toFloat(), target.r.toFloat()),
                        currentPathIndex = targetIndex,
                        currentSpeed = effectiveSpeed,
                        isStopped = isStopped,
                        stopDurationMs = stopDurationMs,
                        lastStopMs = lastStopMs
                    )
                } else {
                    enemy.copy(
                        position = PreciseAxialCoordinate(
                            enemy.position.q + (dq / dist) * effectiveSpeed,
                            enemy.position.r + (dr / dist) * effectiveSpeed
                        ),
                        currentSpeed = effectiveSpeed,
                        isStopped = isStopped,
                        stopDurationMs = stopDurationMs,
                        lastStopMs = lastStopMs
                    )
                }
            }
            newState = newState.copy(enemies = updatedEnemies)

            // 3. Tower Firing
            val newProjectiles = newState.projectiles.toMutableList()
            val newPuddles = newState.puddles.toMutableList()
            val updatedHexes = newState.hexes.toMutableMap()

            newState.hexes.forEach { (coord, tile) ->
                val tower = tile.tower
                if (tower != null && currentTimeMs - tower.lastFiredMs >= tower.fireRateMs) {
                    val target = newState.enemies.firstOrNull { enemy ->
                        axialDistance(enemy.position, PreciseAxialCoordinate(coord.q.toFloat(), coord.r.toFloat())) <= tower.range
                    }

                    if (target != null) {
                        var updatedTower = tower.copy(lastFiredMs = currentTimeMs)
                        when (tower.stallType) {
                            StallType.CHICKEN_RICE -> {
                                newProjectiles.add(Projectile(
                                    id = UUID.randomUUID().toString(),
                                    position = PreciseAxialCoordinate(coord.q.toFloat(), coord.r.toFloat()),
                                    targetEnemyId = target.id,
                                    targetPosition = target.position,
                                    damage = tower.damage,
                                    color = tower.color
                                ))
                            }
                            StallType.TEH_TARIK -> {
                                newPuddles.add(StickyPuddle(
                                    id = UUID.randomUUID().toString(),
                                    position = target.position,
                                    spawnTimeMs = currentTimeMs
                                ))
                            }
                            StallType.SATAY -> {
                                val dq = target.position.q - coord.q
                                val dr = target.position.r - coord.r
                                val angle = Math.atan2(dr.toDouble(), dq.toDouble()).toFloat()
                                updatedTower = updatedTower.copy(rotation = angle)
                                newProjectiles.add(Projectile(
                                    id = UUID.randomUUID().toString(),
                                    position = PreciseAxialCoordinate(coord.q.toFloat(), coord.r.toFloat()),
                                    targetEnemyId = null,
                                    targetPosition = target.position,
                                    damage = tower.damage,
                                    color = tower.color,
                                    speed = 0.5f
                                ))
                            }
                            StallType.ICE_KACHANG -> {
                                newProjectiles.add(Projectile(
                                    id = UUID.randomUUID().toString(),
                                    position = PreciseAxialCoordinate(coord.q.toFloat(), coord.r.toFloat()),
                                    targetEnemyId = target.id,
                                    targetPosition = target.position,
                                    damage = tower.damage,
                                    color = tower.color,
                                    isFreeze = true
                                ))
                            }
                            StallType.DURIAN -> {
                                // Placeholder for Durian: high damage single target
                                newProjectiles.add(Projectile(
                                    id = UUID.randomUUID().toString(),
                                    position = PreciseAxialCoordinate(coord.q.toFloat(), coord.r.toFloat()),
                                    targetEnemyId = target.id,
                                    targetPosition = target.position,
                                    damage = tower.damage,
                                    color = tower.color
                                ))
                            }
                        }
                        updatedHexes[coord] = tile.copy(tower = updatedTower)
                    }
                }
            }
            newState = newState.copy(hexes = updatedHexes, projectiles = newProjectiles, puddles = newPuddles)

            // 4. Projectile Movement and Collision
            val finalProjectiles = mutableListOf<Projectile>()
            val hitEnemies = mutableMapOf<String, Int>()
            val frozenEnemies = mutableSetOf<String>()

            newState.projectiles.forEach { proj ->
                val targetPos = if (proj.targetEnemyId != null) {
                    newState.enemies.find { it.id == proj.targetEnemyId }?.position ?: proj.targetPosition
                } else {
                    proj.targetPosition
                }

                val dq = targetPos.q - proj.position.q
                val dr = targetPos.r - proj.position.r
                val dist = axialDistance(proj.position, targetPos)

                if (dist < proj.speed) {
                    if (proj.targetEnemyId != null) {
                        hitEnemies[proj.targetEnemyId] = hitEnemies.getOrDefault(proj.targetEnemyId, 0) + proj.damage
                        if (proj.isFreeze) frozenEnemies.add(proj.targetEnemyId)
                    } else {
                        newState.enemies.forEach { enemy ->
                            if (axialDistance(enemy.position, proj.targetPosition) <= 1.0) {
                                hitEnemies[enemy.id] = hitEnemies.getOrDefault(enemy.id, 0) + proj.damage
                            }
                        }
                    }
                } else {
                    finalProjectiles.add(proj.copy(
                        position = PreciseAxialCoordinate(
                            proj.position.q + (dq / dist) * proj.speed,
                            proj.position.r + (dr / dist) * proj.speed
                        )
                    ))
                }
            }

            val finalEnemies = newState.enemies.map { enemy ->
                val damageTaken = hitEnemies.getOrDefault(enemy.id, 0)
                if (damageTaken > 0 || frozenEnemies.contains(enemy.id)) {
                    val newHealth = Math.max(0, enemy.health - damageTaken)
                    var updatedEnemy = enemy.copy(health = newHealth)
                    if (frozenEnemies.contains(enemy.id)) updatedEnemy = updatedEnemy.copy(freezeDurationMs = 1500L)
                    if (newHealth <= 0) {
                        newState = newState.copy(gold = newState.gold + enemy.reward)
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastHapticTimeMs >= 1000) {
                            viewModelScope.launch {
                                if (settingsRepository.settingsFlow.first().hapticEnabled) _hapticEvents.emit(Unit)
                            }
                            lastHapticTimeMs = currentTime
                        }
                        enemy.copy(health = 0, isDead = true)
                    } else updatedEnemy
                } else enemy
            }.filter { !it.isDead }

            newState = newState.copy(enemies = finalEnemies, projectiles = finalProjectiles)

            if (newState.waveActive && newState.enemiesToSpawn == 0 && newState.enemies.isEmpty()) {
                newState = newState.copy(waveActive = false)
            }

            newState
        }
    }

    private fun axialDistance(a: PreciseAxialCoordinate, b: PreciseAxialCoordinate): Float {
        return (Math.abs(a.q - b.q) + Math.abs(a.q + a.r - b.q - b.r) + Math.abs(a.r - b.r)) / 2f
    }

    fun onCellClick(coord: AxialCoordinate) {
        val currentState = _gameState.value
        val towerToPlace = currentState.selectedTowerType

        if (towerToPlace != null && currentState.gold >= towerToPlace.cost) {
            val tile = currentState.hexes[coord]
            if (tile != null && tile.type == TileType.FLOOR && tile.tower == null) {
                val blocked = getBlockedCoordinates(currentState.hexes) + coord
                val startPos = currentState.startPosition ?: return
                val endPos = currentState.endPosition ?: return

                // Check if path from START is still possible
                val startPath = Pathfinding.findPath(
                    startPos, endPos, blocked, currentState.hexes.keys
                )

                if (startPath != null) {
                    // Check if path for each active enemy is still possible
                    val canRepathAll = currentState.enemies.all { enemy ->
                        val currentTarget = enemy.path.getOrNull(enemy.currentPathIndex + 1) ?: endPos
                        Pathfinding.findPath(
                            currentTarget, endPos, blocked, currentState.hexes.keys
                        ) != null
                    }

                    if (canRepathAll) {
                        val newHexes = currentState.hexes.toMutableMap()
                        newHexes[coord] = tile.copy(tower = towerToPlace.copy(id = UUID.randomUUID().toString()))

                        _gameState.update { state ->
                            val updatedEnemies = state.enemies.map { enemy ->
                                val currentTargetIndex = enemy.currentPathIndex + 1
                                if (currentTargetIndex >= enemy.path.size) return@map enemy

                                val currentTarget = enemy.path[currentTargetIndex]
                                val newPathToFollow = Pathfinding.findPath(
                                    currentTarget, endPos, blocked, state.hexes.keys
                                ) ?: listOf(currentTarget)

                                // Construct the new full path: part already traveled + new path from current target
                                val newPath = enemy.path.subList(0, currentTargetIndex + 1) + newPathToFollow.drop(1)
                                enemy.copy(path = newPath)
                            }
                            state.copy(hexes = newHexes, gold = state.gold - towerToPlace.cost, enemies = updatedEnemies)
                        }
                    }
                }
            }
        }
    }

    private fun getBlockedCoordinates(hexes: Map<AxialCoordinate, HexTile>): Set<AxialCoordinate> {
        return hexes.values.filter {
            it.tower != null || it.type == TileType.PILLAR || it.type == TileType.GOAL_TABLE || it.type.name.startsWith("EDGE_")
        }.map { it.coordinate }.toSet()
    }
}
