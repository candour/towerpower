package com.messark.hawkerrush

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messark.hawkerrush.model.*
import com.messark.hawkerrush.utils.GameStateRepository
import com.messark.hawkerrush.utils.MapGenerator
import com.messark.hawkerrush.utils.Pathfinding
import com.messark.hawkerrush.utils.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val settingsRepository: SettingsRepository = SettingsRepository(application),
    private val gameStateRepository: GameStateRepository = GameStateRepository(application)
) : AndroidViewModel(application) {
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _logoVisible = MutableStateFlow(true)
    val logoVisible: StateFlow<Boolean> = _logoVisible.asStateFlow()

    private val enemyTiers = listOf(
        EnemyType.SALARYMAN,
        EnemyType.TOURIST,
        EnemyType.AUNTIE,
        EnemyType.DELIVERY_RIDER
    )

    private val _availableStalls = MutableStateFlow(
        listOf(
            Stall("t1", "Teh Tarik", 150, Color.Blue, stallType = StallType.TEH_TARIK, range = 3f, description = "Creates slowing puddles"),
            Stall("t2", "Satay", 200, Color.Red, stallType = StallType.SATAY, range = 2.5f, damage = 5, fireRateMs = 500, description = "Fast area damage"),
            Stall("t3", "Chicken Rice", 100, Color.Yellow, stallType = StallType.CHICKEN_RICE, range = 4f, damage = 15, fireRateMs = 700, description = "High single-target damage"),
            Stall("t4", "Durian", 300, Color(0xFF4CAF50), stallType = StallType.DURIAN, range = 3f, damage = 25, fireRateMs = 2000, description = "Massive damage, slow fire"),
            Stall("t5", "Ice Kachang", 250, Color.Cyan, stallType = StallType.ICE_KACHANG, range = 3.5f, damage = 2, fireRateMs = 1500, description = "Freezes enemies in place")
        )
    )
    val availableStalls: StateFlow<List<Stall>> = _availableStalls.asStateFlow()

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
            gold = 500, // Start with some gold to place stalls
            currentScreen = AppScreen.LOADING
        ) }
    }

    fun navigateTo(screen: AppScreen) {
        _gameState.update { it.copy(currentScreen = screen) }
    }

    fun hideLogo() {
        _logoVisible.value = false
    }

    fun triggerHaptic() {
        viewModelScope.launch {
            if (settingsRepository.settingsFlow.first().hapticEnabled) {
                _hapticEvents.emit(Unit)
            }
        }
    }

    fun updateHapticSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(hapticEnabled = enabled) }
        }
    }

    fun hasSavedGame(): Boolean = gameStateRepository.hasSavedGame()

    fun resumeGame() {
        val savedState = gameStateRepository.loadGameState()
        if (savedState != null) {
            _gameState.value = savedState
        }
    }

    fun resetGame() {
        gameStateRepository.deleteGameState()
        val (hexes, startPos, endPos) = MapGenerator.generateRandomVerticalMap(width = 8, height = 16)
        _gameState.update {
            GameState(
                currentScreen = AppScreen.GAME,
                hexes = hexes,
                startPosition = startPos,
                endPosition = endPos,
                gold = 500,
                score = 0
            )
        }
    }

    fun selectStall(stall: Stall) {
        _gameState.update { it.copy(selectedStallType = stall) }
    }

    fun startWave() {
        val currentState = _gameState.value
        if (currentState.waveActive) return

        val newWave = currentState.currentWave + 1
        val enemyList = generateEnemyList(newWave)
        val isBossWave = newWave % 10 == 0
        val currentTime = System.currentTimeMillis()

        _gameState.update {
            it.copy(
                waveActive = true,
                currentWave = newWave,
                enemiesToSpawn = enemyList.size,
                enemiesToSpawnList = enemyList,
                isBossWave = isBossWave,
                bossWaveTriggerTimeMs = if (isBossWave) currentTime else 0L,
                lastSpawnTimeMs = currentTime
            )
        }
    }

    private fun generateEnemyList(wave: Int): List<EnemyType> {
        if (wave <= 6) {
            val list = when (wave) {
                1 -> List(5) { EnemyType.SALARYMAN }
                2 -> List(6) { EnemyType.SALARYMAN }
                3 -> List(5) { EnemyType.SALARYMAN } + List(1) { EnemyType.TOURIST }
                4 -> List(6) { EnemyType.SALARYMAN } + List(1) { EnemyType.TOURIST }
                5 -> List(5) { EnemyType.SALARYMAN } + List(2) { EnemyType.TOURIST }
                6 -> List(4) { EnemyType.SALARYMAN } + List(2) { EnemyType.TOURIST } + List(1) { EnemyType.AUNTIE }
                else -> emptyList()
            }
            return list.shuffled()
        }

        // Algorithmic for Wave 7+
        // Calculate budget iteratively for consistency
        var budget = 883.0 // Base budget for Wave 6: (4*80 + 2*161 + 1*241)
        for (i in 7..wave) {
            if (i % 10 == 0) {
                budget *= 1.44 // Boss wave budget jump (1.2 * 1.2)
            } else if ((i - 1) % 10 == 0) {
                budget *= 1.0 // Plateau after boss wave
            } else {
                budget *= 1.2
            }
        }

        val enemyList = mutableListOf<EnemyType>()
        var remainingBudget = budget

        val maxTierIndex = minOf((wave - 1) / 2, enemyTiers.size - 1)
        var allowedTiers = enemyTiers.subList(0, maxTierIndex + 1)

        // Only allow Delivery Riders in boss waves until level 30
        if (wave <= 30 && wave % 10 != 0) {
            allowedTiers = allowedTiers.filter { it != EnemyType.DELIVERY_RIDER }
        }

        val random = Random()
        var attempts = 0
        while (remainingBudget > 0 && attempts < 100) {
            val type = allowedTiers[random.nextInt(allowedTiers.size)]
            val hp = getEnemyHP(type, wave)
            if (hp <= remainingBudget) {
                enemyList.add(type)
                remainingBudget -= hp
            } else if (allowedTiers.all { getEnemyHP(it, wave) > remainingBudget }) {
                break
            }
            attempts++
        }

        if (enemyList.isEmpty()) {
            enemyList.add(EnemyType.SALARYMAN)
        }

        return enemyList.shuffled()
    }

    private fun getEnemyHP(type: EnemyType, wave: Int): Int {
        val baseHp = when (type) {
            EnemyType.SALARYMAN -> 50
            EnemyType.TOURIST -> 100
            EnemyType.AUNTIE -> 150
            EnemyType.DELIVERY_RIDER -> 500
        }
        return (baseHp * Math.pow(1.1, (wave - 1).toDouble())).toInt()
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

            // 0. Update Puddles and Visual Effects
            val updatedPuddles = state.puddles.filter { currentTimeMs - it.spawnTimeMs < it.durationMs }
            val updatedVisualEffects = state.visualEffects.filter { currentTimeMs - it.startTimeMs < it.durationMs }
            newState = newState.copy(puddles = updatedPuddles, visualEffects = updatedVisualEffects)

            // 1. Spawning
            if (state.waveActive && state.enemiesToSpawn > 0 && currentTimeMs - state.lastSpawnTimeMs > 1000 && state.hexes.isNotEmpty() && state.enemiesToSpawnList.isNotEmpty()) {
                val startPos = state.startPosition ?: return@update state
                val endPos = state.endPosition ?: return@update state
                val path = Pathfinding.findPath(
                    startPos, endPos, getBlockedCoordinates(state.hexes), state.hexes.keys
                ) ?: emptyList()

                val type = state.enemiesToSpawnList.first()
                val remainingSpawnList = state.enemiesToSpawnList.drop(1)

                val enemyHealth = getEnemyHP(type, state.currentWave)

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
                    enemiesToSpawnList = remainingSpawnList,
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

            // 3. Stall Firing
            val newProjectiles = newState.projectiles.toMutableList()
            val newPuddles = newState.puddles.toMutableList()
            val updatedHexes = newState.hexes.toMutableMap()

            newState.hexes.forEach { (coord, tile) ->
                val stall = tile.stall
                if (stall != null && currentTimeMs - stall.lastFiredMs >= stall.fireRateMs) {
                    val stallPos = PreciseAxialCoordinate(coord.q.toFloat(), coord.r.toFloat())
                    val potentialTargets = newState.enemies.filter { enemy ->
                        axialDistance(enemy.position, stallPos) <= stall.range
                    }

                    val target = when (stall.targetMode) {
                        TargetMode.FIRST -> potentialTargets.maxByOrNull { it.currentPathIndex }
                        TargetMode.CLOSEST -> potentialTargets.minByOrNull { axialDistance(it.position, stallPos) }
                        TargetMode.STRONGEST -> potentialTargets.maxByOrNull { it.health }
                        TargetMode.WEAKEST -> potentialTargets.minByOrNull { it.health }
                    }

                    if (target != null) {
                        var updatedStall = stall.copy(lastFiredMs = currentTimeMs)
                        when (stall.stallType) {
                            StallType.CHICKEN_RICE -> {
                                newProjectiles.add(Projectile(
                                    id = UUID.randomUUID().toString(),
                                    position = stallPos,
                                    targetEnemyId = target.id,
                                    targetPosition = target.position,
                                    damage = stall.damage,
                                    color = stall.color
                                ))
                            }
                            StallType.TEH_TARIK -> {
                                newPuddles.add(StickyPuddle(
                                    id = UUID.randomUUID().toString(),
                                    position = target.position,
                                    spawnTimeMs = currentTimeMs,
                                    durationMs = stall.effectDurationMs
                                ))
                            }
                            StallType.SATAY -> {
                                val dq = target.position.q - coord.q
                                val dr = target.position.r - coord.r
                                val angle = Math.atan2(dr.toDouble(), dq.toDouble()).toFloat()
                                updatedStall = updatedStall.copy(rotation = angle)
                                newProjectiles.add(Projectile(
                                    id = UUID.randomUUID().toString(),
                                    position = stallPos,
                                    targetEnemyId = null,
                                    targetPosition = target.position,
                                    damage = stall.damage,
                                    color = stall.color,
                                    speed = 0.5f,
                                    aoeRadius = stall.aoeRadius
                                ))
                            }
                            StallType.ICE_KACHANG -> {
                                newProjectiles.add(Projectile(
                                    id = UUID.randomUUID().toString(),
                                    position = stallPos,
                                    targetEnemyId = target.id,
                                    targetPosition = target.position,
                                    damage = stall.damage,
                                    color = stall.color,
                                    isFreeze = true,
                                    freezeDurationMs = stall.freezeDurationMs
                                ))
                            }
                            StallType.DURIAN -> {
                                newProjectiles.add(Projectile(
                                    id = UUID.randomUUID().toString(),
                                    position = stallPos,
                                    targetEnemyId = target.id,
                                    targetPosition = target.position,
                                    damage = stall.damage,
                                    color = stall.color,
                                    aoeRadius = stall.aoeRadius
                                ))
                            }
                        }
                        updatedHexes[coord] = tile.copy(stall = updatedStall)
                    }
                }
            }
            newState = newState.copy(hexes = updatedHexes, projectiles = newProjectiles, puddles = newPuddles)

            // 4. Projectile Movement and Collision
            val finalProjectiles = mutableListOf<Projectile>()
            val hitEnemies = mutableMapOf<String, Int>()
            val frozenEnemies = mutableSetOf<String>()
            val frozenDurations = mutableMapOf<String, Long>()

            val newVisualEffects = newState.visualEffects.toMutableList()
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
                    if (proj.aoeRadius > 0) {
                        val effectColor = when {
                            proj.color == Color.Red -> Color.Red.copy(alpha = 0.5f) // Satay
                            proj.color == Color(0xFF4CAF50) -> Color(0xFFCDDC39).copy(alpha = 0.5f) // Durian (greeny-yellow)
                            else -> proj.color.copy(alpha = 0.5f)
                        }
                        newVisualEffects.add(VisualEffect(
                            id = UUID.randomUUID().toString(),
                            position = targetPos,
                            color = effectColor,
                            startTimeMs = currentTimeMs,
                            durationMs = 150L
                        ))
                    }

                    if (proj.targetEnemyId != null) {
                        if (proj.aoeRadius > 0) {
                            newState.enemies.forEach { enemy ->
                                if (axialDistance(enemy.position, targetPos) <= proj.aoeRadius) {
                                    hitEnemies[enemy.id] = hitEnemies.getOrDefault(enemy.id, 0) + proj.damage
                                    if (proj.isFreeze) {
                                        frozenEnemies.add(enemy.id)
                                        frozenDurations[enemy.id] = Math.max(frozenDurations.getOrDefault(enemy.id, 0L), proj.freezeDurationMs)
                                    }
                                }
                            }
                        } else {
                            hitEnemies[proj.targetEnemyId] = hitEnemies.getOrDefault(proj.targetEnemyId, 0) + proj.damage
                            if (proj.isFreeze) {
                                frozenEnemies.add(proj.targetEnemyId)
                                frozenDurations[proj.targetEnemyId] = Math.max(frozenDurations.getOrDefault(proj.targetEnemyId, 0L), proj.freezeDurationMs)
                            }
                        }
                    } else {
                        newState.enemies.forEach { enemy ->
                            if (axialDistance(enemy.position, proj.targetPosition) <= proj.aoeRadius) {
                                hitEnemies[enemy.id] = hitEnemies.getOrDefault(enemy.id, 0) + proj.damage
                                if (proj.isFreeze) {
                                    frozenEnemies.add(enemy.id)
                                    frozenDurations[enemy.id] = Math.max(frozenDurations.getOrDefault(enemy.id, 0L), proj.freezeDurationMs)
                                }
                            }
                        }
                    }
                } else {
                    finalProjectiles.add(proj.copy(
                        lastPosition = proj.position,
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
                    if (frozenEnemies.contains(enemy.id)) {
                        updatedEnemy = updatedEnemy.copy(freezeDurationMs = frozenDurations.getOrDefault(enemy.id, 0L))
                    }
                    if (newHealth <= 0) {
                        newState = newState.copy(
                            gold = newState.gold + enemy.reward,
                            score = newState.score + enemy.reward
                        )
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

            newState = newState.copy(enemies = finalEnemies, projectiles = finalProjectiles, visualEffects = newVisualEffects)

            if (newState.waveActive && newState.enemiesToSpawn == 0 && newState.enemies.isEmpty()) {
                newState = newState.copy(waveActive = false, isBossWave = false)
                gameStateRepository.saveGameState(newState)
            }

            if (newState.health <= 0 && state.health > 0) {
                handleGameOver(newState)
            }

            newState
        }
    }

    private fun handleGameOver(state: GameState) {
        val finalScore = state.score
        val finalWave = state.currentWave
        val date = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val newHighScore = HighScore(finalScore, finalWave, date)

        viewModelScope.launch {
            settingsRepository.updateSettings { currentSettings ->
                val updatedScores = (currentSettings.highScores + newHighScore)
                    .sortedByDescending { it.score }
                    .take(5)
                currentSettings.copy(highScores = updatedScores)
            }
            gameStateRepository.deleteGameState()
        }
    }

    private fun axialDistance(a: PreciseAxialCoordinate, b: PreciseAxialCoordinate): Float {
        return (Math.abs(a.q - b.q) + Math.abs(a.q + a.r - b.q - b.r) + Math.abs(a.r - b.r)) / 2f
    }

    fun onCellClick(coord: AxialCoordinate) {
        val currentState = _gameState.value
        val tile = currentState.hexes[coord] ?: return

        if (tile.stall != null) {
            // Select existing stall
            _gameState.update { it.copy(selectedBoardStall = coord, selectedStallType = null) }
        } else if (currentState.selectedStallType != null) {
            // Place new stall
            val stallToPlace = currentState.selectedStallType
            if (currentState.gold >= stallToPlace.cost && tile.type == TileType.FLOOR && tile.stall == null) {
                val blocked = getBlockedCoordinates(currentState.hexes) + coord
                val startPos = currentState.startPosition ?: return
                val endPos = currentState.endPosition ?: return

                val startPath = Pathfinding.findPath(startPos, endPos, blocked, currentState.hexes.keys)

                if (startPath != null) {
                    val canRepathAll = currentState.enemies.all { enemy ->
                        val currentTarget = enemy.path.getOrNull(enemy.currentPathIndex + 1) ?: endPos
                        Pathfinding.findPath(currentTarget, endPos, blocked, currentState.hexes.keys) != null
                    }

                    if (canRepathAll) {
                        val newHexes = currentState.hexes.toMutableMap()
                        newHexes[coord] = tile.copy(stall = stallToPlace.copy(id = UUID.randomUUID().toString()))

                        _gameState.update { state ->
                            val updatedEnemies = state.enemies.map { enemy ->
                                val currentTargetIndex = enemy.currentPathIndex + 1
                                if (currentTargetIndex >= enemy.path.size) return@map enemy

                                val currentTarget = enemy.path[currentTargetIndex]
                                val newPathToFollow = Pathfinding.findPath(
                                    currentTarget, endPos, blocked, state.hexes.keys
                                ) ?: listOf(currentTarget)

                                val newPath = enemy.path.subList(0, currentTargetIndex + 1) + newPathToFollow.drop(1)
                                enemy.copy(path = newPath)
                            }
                            state.copy(hexes = newHexes, gold = state.gold - stallToPlace.cost, enemies = updatedEnemies)
                        }
                    }
                }
            }
        } else {
            // Deselect
            _gameState.update { it.copy(selectedBoardStall = null, selectedStallType = null) }
        }
    }

    fun sellStall() {
        val currentState = _gameState.value
        val coord = currentState.selectedBoardStall ?: return
        val tile = currentState.hexes[coord] ?: return
        val stall = tile.stall ?: return

        val refund = (stall.totalInvestment * 0.5f).toInt()
        val newHexes = currentState.hexes.toMutableMap()
        newHexes[coord] = tile.copy(stall = null)

        val endPos = currentState.endPosition ?: return
        val blocked = getBlockedCoordinates(newHexes)

        _gameState.update { state ->
            val updatedEnemies = state.enemies.map { enemy ->
                val currentTargetIndex = enemy.currentPathIndex + 1
                if (currentTargetIndex >= enemy.path.size) return@map enemy

                val currentTarget = enemy.path[currentTargetIndex]
                val newPathToFollow = Pathfinding.findPath(
                    currentTarget, endPos, blocked, state.hexes.keys
                ) ?: listOf(currentTarget)

                val newPath = enemy.path.subList(0, currentTargetIndex + 1) + newPathToFollow.drop(1)
                enemy.copy(path = newPath)
            }
            state.copy(
                hexes = newHexes,
                gold = state.gold + refund,
                enemies = updatedEnemies,
                selectedBoardStall = null
            )
        }
    }

    fun upgradeStall() {
        val currentState = _gameState.value
        val coord = currentState.selectedBoardStall ?: return
        val tile = currentState.hexes[coord] ?: return
        val stall = tile.stall ?: return

        // Cost of upgrade is the same as the base cost of the tower
        val upgradeCost = _availableStalls.value.find { it.stallType == stall.stallType }?.cost ?: stall.cost

        if (currentState.gold >= upgradeCost) {
            val upgradeTypeIndex = Random().nextInt(3) // 0: Damage/Range, 1: Rate, 2: Special (Radius/Duration)
            var updatedStall = stall.copy(
                upgradeCount = stall.upgradeCount + 1,
                totalInvestment = stall.totalInvestment + upgradeCost
            )
            val mutableUpgrades = updatedStall.upgrades.toMutableMap()

            when (upgradeTypeIndex) {
                0 -> {
                    if (Random().nextBoolean()) {
                        updatedStall = updatedStall.copy(damage = (updatedStall.damage * 1.2f).toInt() + 1)
                        mutableUpgrades["Damage"] = mutableUpgrades.getOrDefault("Damage", 0) + 1
                    } else {
                        updatedStall = updatedStall.copy(range = updatedStall.range + 0.5f)
                        mutableUpgrades["Range"] = mutableUpgrades.getOrDefault("Range", 0) + 1
                    }
                }
                1 -> {
                    updatedStall = updatedStall.copy(fireRateMs = (updatedStall.fireRateMs * 0.9f).toLong())
                    mutableUpgrades["Rate"] = mutableUpgrades.getOrDefault("Rate", 0) + 1
                }
                2 -> {
                    when (stall.stallType) {
                        StallType.SATAY, StallType.DURIAN -> {
                            updatedStall = updatedStall.copy(aoeRadius = updatedStall.aoeRadius + 0.2f)
                            mutableUpgrades["Radius"] = mutableUpgrades.getOrDefault("Radius", 0) + 1
                        }
                        StallType.TEH_TARIK -> {
                            updatedStall = updatedStall.copy(effectDurationMs = updatedStall.effectDurationMs + 500L)
                            mutableUpgrades["Duration"] = mutableUpgrades.getOrDefault("Duration", 0) + 1
                        }
                        StallType.ICE_KACHANG -> {
                            updatedStall = updatedStall.copy(freezeDurationMs = updatedStall.freezeDurationMs + 300L)
                            mutableUpgrades["Effect"] = mutableUpgrades.getOrDefault("Effect", 0) + 1
                        }
                        StallType.CHICKEN_RICE -> {
                            updatedStall = updatedStall.copy(damage = (updatedStall.damage * 1.3f).toInt() + 2)
                            mutableUpgrades["Damage"] = mutableUpgrades.getOrDefault("Damage", 0) + 1
                        }
                    }
                }
            }

            updatedStall = updatedStall.copy(upgrades = mutableUpgrades)
            val newHexes = currentState.hexes.toMutableMap()
            newHexes[coord] = tile.copy(stall = updatedStall)
            _gameState.update { it.copy(hexes = newHexes, gold = it.gold - upgradeCost) }
        }
    }

    fun cycleTargetMode() {
        val currentState = _gameState.value
        val coord = currentState.selectedBoardStall ?: return
        val tile = currentState.hexes[coord] ?: return
        val stall = tile.stall ?: return

        val modes = TargetMode.values()
        val nextMode = modes[(stall.targetMode.ordinal + 1) % modes.size]

        val newHexes = currentState.hexes.toMutableMap()
        newHexes[coord] = tile.copy(stall = stall.copy(targetMode = nextMode))
        _gameState.update { it.copy(hexes = newHexes) }
    }

    private fun getBlockedCoordinates(hexes: Map<AxialCoordinate, HexTile>): Set<AxialCoordinate> {
        return hexes.values.filter {
            it.stall != null || it.type == TileType.PILLAR || it.type == TileType.GOAL_TABLE || it.type.name.startsWith("EDGE_")
        }.map { it.coordinate }.toSet()
    }
}
