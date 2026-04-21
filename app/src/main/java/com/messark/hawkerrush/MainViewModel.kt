package com.messark.hawkerrush

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messark.hawkerrush.model.*
import com.messark.hawkerrush.registry.*
import com.messark.hawkerrush.utils.*
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
    internal val _gameState = MutableStateFlow(GameState())
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
        StallRegistry.all().map { it.toStall() }
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

    fun updateTutorialsSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(showTutorials = enabled) }
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
        if (currentState.waveActive || currentState.activeTutorial != null) return

        val newWave = currentState.currentWave + 1
        val enemyList = generateEnemyList(newWave)

        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.showTutorials) {
                val newEnemyTypes = enemyList.distinct().filter { !settings.shownTutorials.contains("enemy_${it.name.lowercase()}") }
                if (newEnemyTypes.isNotEmpty()) {
                    val firstNewEnemy = newEnemyTypes.first()
                    val enemyDef = EnemyRegistry.get(firstNewEnemy)
                    val tutorial = TutorialData(
                        id = "enemy_${firstNewEnemy.name.lowercase()}",
                        type = TutorialType.ENEMY,
                        title = enemyDef.name,
                        description = enemyDef.description,
                        enemyType = firstNewEnemy
                    )

                    _gameState.update { it.copy(activeTutorial = tutorial) }
                    // Mark as shown
                    settingsRepository.updateSettings {
                        it.copy(shownTutorials = it.shownTutorials + "enemy_${firstNewEnemy.name.lowercase()}")
                    }
                    return@launch
                }
            }

            proceedWithWave(newWave, enemyList)
        }
    }

    private fun proceedWithWave(newWave: Int, enemyList: List<EnemyType>) {
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

    fun dismissTutorial() {
        val currentState = _gameState.value
        val tutorial = currentState.activeTutorial ?: return

        _gameState.update { it.copy(activeTutorial = null) }

        // If it was an enemy tutorial, we might want to start the wave now
        if (tutorial.type == TutorialType.ENEMY) {
            // Re-check if there are MORE tutorials for this wave (e.g. wave has 2 new enemies)
            startWave()
        }
    }

    fun showStallTutorial(stallType: StallType) {
        val def = StallRegistry.get(stallType)
        val tutorial = TutorialData(
            id = "stall_${stallType.name.lowercase()}",
            type = TutorialType.STALL,
            title = def.tutorialTitle,
            signatureMove = def.signatureMove,
            description = def.tutorialDescription,
            stallType = stallType
        )
        _gameState.update { it.copy(activeTutorial = tutorial) }
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

        var attempts = 0
        while (remainingBudget > 0 && attempts < 100) {
            val type = allowedTiers[kotlin.random.Random.nextInt(allowedTiers.size)]
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
        return EnemyRegistry.get(type).getHp(wave)
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

    /**
     * Updates game state by advancing spawning, movement, and combat.
     *
     * @param currentTimeMs Current game time in milliseconds.
     */
    internal fun updateGame(currentTimeMs: Long) {
        _gameState.update { state ->
            if (state.activeTutorial != null) return@update state
            var newState = state

            // 0. Update Puddles and Visual Effects
            newState = updateTransients(newState, currentTimeMs)

            // 1. Spawning
            newState = handleSpawning(newState, currentTimeMs)

            // 2. Enemy Movement
            val (movedState, updatedEnemies) = handleEnemyMovement(newState, currentTimeMs)
            newState = movedState.copy(enemies = updatedEnemies)

            // 3. Stall Firing
            newState = handleStallFiring(newState, currentTimeMs)

            // 4. Projectile Movement and Collision
            newState = handleProjectiles(newState, currentTimeMs)

            // 5. Wave completion check
            if (newState.waveActive && newState.enemiesToSpawn == 0 && newState.enemies.isEmpty()) {
                newState = newState.copy(waveActive = false, isBossWave = false)
                gameStateRepository.saveGameState(newState)
            }

            // 6. Game over check
            if (newState.health <= 0 && state.health > 0) {
                handleGameOver(newState)
            }

            newState
        }
    }

    private fun updateTransients(state: GameState, currentTimeMs: Long): GameState {
        val updatedPuddles = state.puddles.filter { currentTimeMs - it.spawnTimeMs < it.durationMs }
        val updatedVisualEffects = state.visualEffects.filter { currentTimeMs - it.startTimeMs < it.durationMs }
        return state.copy(puddles = updatedPuddles, visualEffects = updatedVisualEffects)
    }

    private fun handleSpawning(state: GameState, currentTimeMs: Long): GameState {
        if (state.waveActive && state.enemiesToSpawn > 0 && currentTimeMs - state.lastSpawnTimeMs > 1000 && state.hexes.isNotEmpty() && state.enemiesToSpawnList.isNotEmpty()) {
            val startPos = state.startPosition ?: return state
            val endPos = state.endPosition ?: return state
            val path = Pathfinding.findPath(
                startPos, endPos, getBlockedCoordinates(state.hexes), state.hexes.keys
            ) ?: emptyList()

            val type = state.enemiesToSpawnList.first()
            val remainingSpawnList = state.enemiesToSpawnList.drop(1)

            val firstTarget = path.getOrNull(1) ?: startPos
            val isFacingLeft = firstTarget.q + firstTarget.r / 2f < startPos.q + startPos.r / 2f

            val newEnemy = EnemyRegistry.get(type).toEnemy(
                wave = state.currentWave,
                position = PreciseAxialCoordinate(startPos.q.toFloat(), startPos.r.toFloat()),
                path = path,
                isFacingLeft = isFacingLeft
            )
            return state.copy(
                enemies = state.enemies + newEnemy,
                enemiesToSpawn = state.enemiesToSpawn - 1,
                enemiesToSpawnList = remainingSpawnList,
                lastSpawnTimeMs = currentTimeMs
            )
        }
        return state
    }

    /**
     * Handles movement for all active enemies and applies puddle effects.
     *
     * @param state Current game state.
     * @param currentTimeMs Current game time.
     * @return Updated state and list of enemies.
     */
    private fun handleEnemyMovement(state: GameState, currentTimeMs: Long): Pair<GameState, List<Enemy>> {
        var mutableState = state
        val affectingStalls = mutableMapOf<Pair<AxialCoordinate, String>, MutableSet<String>>()

        val updatedEnemies = state.enemies.mapNotNull { enemy ->
            if (enemy.isDead) return@mapNotNull null

            val enemyDef = EnemyRegistry.get(enemy.type)

            var freezeDuration = enemy.freezeDurationMs
            if (freezeDuration > 0) {
                freezeDuration = Math.max(0, freezeDuration - 32)
            }

            var speedBoostDuration = enemy.speedBoostDurationMs
            if (speedBoostDuration > 0) {
                speedBoostDuration = Math.max(0, speedBoostDuration - 32)
            }

            val behaviorUpdatedEnemy = enemyDef.updateSpecialBehavior(enemy, currentTimeMs)
            var isStopped = behaviorUpdatedEnemy.isStopped
            var stopDurationMs = behaviorUpdatedEnemy.stopDurationMs
            var lastStopMs = behaviorUpdatedEnemy.lastStopMs

            state.puddles.forEach { puddle ->
                if (axialDistance(enemy.position, puddle.position) < 0.8 &&
                    puddle.sourceStallCoord != null &&
                    puddle.sourceStallId != null
                ) {
                    affectingStalls
                        .getOrPut(puddle.sourceStallCoord to puddle.sourceStallId) { mutableSetOf() }
                        .add(enemy.id)
                }
            }
            if (isStopped || freezeDuration > 0) {
                return@mapNotNull enemy.copy(
                    isStopped = isStopped,
                    stopDurationMs = stopDurationMs,
                    lastStopMs = lastStopMs,
                    freezeDurationMs = freezeDuration,
                    speedBoostDurationMs = speedBoostDuration
                )
            }

            var speedMultiplier = 1.0f
            state.puddles.forEach { puddle ->
                if (axialDistance(enemy.position, puddle.position) < 0.8) {
                    speedMultiplier = enemyDef.getPuddleSlowMultiplier(enemy.type)
                }
            }

            if (speedBoostDuration > 0) {
                speedMultiplier *= 1.5f
            }

            val effectiveSpeed = enemy.baseSpeed * speedMultiplier

            val targetIndex = enemy.currentPathIndex + 1
            if (targetIndex >= enemy.path.size) {
                mutableState = mutableState.copy(health = Math.max(0, mutableState.health - 1))
                return@mapNotNull null
            }

            val target = enemy.path[targetIndex]
            val dq = target.q - enemy.position.q
            val dr = target.r - enemy.position.r
            val dist = axialDistance(enemy.position, PreciseAxialCoordinate(target.q.toFloat(), target.r.toFloat()))

            val newIsFacingLeft = if (target.q + target.r / 2f != enemy.position.q + enemy.position.r / 2f) {
                target.q + target.r / 2f < enemy.position.q + enemy.position.r / 2f
            } else {
                enemy.isFacingLeft
            }

            if (dist < effectiveSpeed) {
                enemy.copy(
                    position = PreciseAxialCoordinate(target.q.toFloat(), target.r.toFloat()),
                    currentPathIndex = targetIndex,
                    currentSpeed = effectiveSpeed,
                    isStopped = isStopped,
                    stopDurationMs = stopDurationMs,
                    lastStopMs = lastStopMs,
                    freezeDurationMs = freezeDuration,
                    speedBoostDurationMs = speedBoostDuration,
                    animationTimeMs = enemy.animationTimeMs + 32,
                    isFacingLeft = newIsFacingLeft
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
                    lastStopMs = lastStopMs,
                    freezeDurationMs = freezeDuration,
                    speedBoostDurationMs = speedBoostDuration,
                    animationTimeMs = enemy.animationTimeMs + 32,
                    isFacingLeft = newIsFacingLeft
                )
            }
        }

        if (affectingStalls.isNotEmpty()) {
            val updatedHexes = mutableState.hexes.toMutableMap()
            affectingStalls.forEach { (source, enemyIds) ->
                val (coord, stallId) = source
                updatedHexes[coord]?.stall?.let { stall ->
                    if (stall.id == stallId) {
                        val newTargetIds = stall.uniqueTargetIds + enemyIds
                        updatedHexes[coord] = updatedHexes[coord]!!.copy(stall = stall.copy(uniqueTargetIds = newTargetIds))
                    }
                }
            }
            mutableState = mutableState.copy(hexes = updatedHexes)
        }

        return Pair(mutableState, updatedEnemies)
    }

    /**
     * Checks all stalls to see if they are ready to fire and creates projectiles/puddles.
     *
     * @param state Current game state.
     * @param currentTimeMs Current game time.
     * @return Updated state with new projectiles/puddles.
     */
    private fun handleStallFiring(state: GameState, currentTimeMs: Long): GameState {
        val newProjectiles = state.projectiles.toMutableList()
        val newPuddles = state.puddles.toMutableList()
        val updatedHexes = state.hexes.toMutableMap()

        state.hexes.forEach { (coord, tile) ->
            val stall = tile.stall
            if (stall != null && currentTimeMs - stall.lastFiredMs >= stall.fireRateMs) {
                val stallPos = PreciseAxialCoordinate(coord.q.toFloat(), coord.r.toFloat())
                val potentialTargets = state.enemies.filter { enemy ->
                    axialDistance(enemy.position, stallPos) <= stall.range
                }

                val target = when (stall.targetMode) {
                    TargetMode.FIRST -> potentialTargets.maxByOrNull { it.currentPathIndex }
                    TargetMode.CLOSEST -> potentialTargets.minByOrNull { axialDistance(it.position, stallPos) }
                    TargetMode.STRONGEST -> potentialTargets.maxByOrNull { it.health }
                    TargetMode.WEAKEST -> potentialTargets.minByOrNull { it.health }
                }

                if (target != null) {
                    val stallDef = StallRegistry.get(stall.stallType)
                    val fireResult = stallDef.fire(stall, coord, target, currentTimeMs)
                    var updatedStall = (fireResult as? FireResult.NewProjectile)?.updatedStall ?: stall
                    updatedStall = updatedStall.copy(lastFiredMs = currentTimeMs)

                    when (fireResult) {
                        is FireResult.NewProjectile -> {
                            newProjectiles.add(fireResult.projectile)
                        }
                        is FireResult.NewPuddle -> {
                            newPuddles.add(fireResult.puddle)
                        }
                    }
                    updatedHexes[coord] = tile.copy(stall = updatedStall)
                }
            }
        }
        return state.copy(hexes = updatedHexes, projectiles = newProjectiles, puddles = newPuddles)
    }

    /**
     * Updates projectile positions and handles impacts with enemies.
     * Also attributes hits and kills to the source stalls.
     *
     * @param state Current game state.
     * @param currentTimeMs Current game time.
     * @return Updated state after projectile processing.
     */
    private fun handleProjectiles(state: GameState, currentTimeMs: Long): GameState {
        val finalProjectiles = mutableListOf<Projectile>()
        val hitEnemiesDetails = mutableMapOf<String, MutableList<Projectile>>()
        val newVisualEffects = state.visualEffects.toMutableList()

        state.projectiles.forEach { proj ->
            val targetPos = if (proj.targetEnemyId != null) {
                state.enemies.find { it.id == proj.targetEnemyId }?.position ?: proj.targetPosition
            } else {
                proj.targetPosition
            }

            val dq = targetPos.q - proj.position.q
            val dr = targetPos.r - proj.position.r
            val dist = axialDistance(proj.position, targetPos)

            if (dist < proj.speed) {
                // Visual Effect
                if (proj.aoeRadius > 0 && proj.sourceStallType != null) {
                    val stallDef = StallRegistry.get(proj.sourceStallType)
                    newVisualEffects.add(VisualEffect(
                        id = UUID.randomUUID().toString(),
                        position = targetPos,
                        color = stallDef.visualEffectColor ?: proj.color.copy(alpha = 0.5f),
                        startTimeMs = currentTimeMs,
                        durationMs = stallDef.visualEffectDuration,
                        type = stallDef.visualEffectType
                    ))
                }

                // Collect hits
                state.enemies.forEach { enemy ->
                    val isDirectTarget = proj.targetEnemyId == enemy.id
                    val isWithinAoe = proj.aoeRadius > 0 && axialDistance(enemy.position, targetPos) <= proj.aoeRadius
                    if (isDirectTarget || isWithinAoe) {
                        hitEnemiesDetails.getOrPut(enemy.id) { mutableListOf() }.add(proj)
                    }
                }
            } else {
                // Keep moving
                finalProjectiles.add(proj.copy(
                    lastPosition = proj.position,
                    position = PreciseAxialCoordinate(
                        proj.position.q + (dq / dist) * proj.speed,
                        proj.position.r + (dr / dist) * proj.speed
                    )
                ))
            }
        }

        var updatedGold = state.gold
        var updatedScore = state.score
        val updatedHexes = state.hexes.toMutableMap()

        val finalEnemies = state.enemies.map { enemy ->
            val hits = hitEnemiesDetails[enemy.id]
            if (hits != null) {
                var currentHealth = enemy.health
                var maxFreezeDuration = enemy.freezeDurationMs
                var speedBoostDuration = enemy.speedBoostDurationMs

                hits.forEach { proj ->
                    if (currentHealth <= 0) return@forEach

                    var damage = proj.damage.toFloat()
                    var freezeDuration = proj.freezeDurationMs

                    // Apply modifiers
                    if (proj.sourceStallType != null) {
                        val stallDef = StallRegistry.get(proj.sourceStallType)
                        damage = stallDef.applyDamageModifiers(enemy, damage)
                        freezeDuration = stallDef.getFreezeModifier(enemy, freezeDuration)
                        val boost = stallDef.getSpeedBoost(enemy)
                        if (boost > 0) speedBoostDuration = boost
                    }

                    val damageDealt = damage.toInt()
                    currentHealth = Math.max(0, currentHealth - damageDealt)
                    maxFreezeDuration = Math.max(maxFreezeDuration, freezeDuration)

                    // Track hit and kill
                    if (proj.sourceStallCoord != null && proj.sourceStallId != null) {
                        val coord = proj.sourceStallCoord
                        updatedHexes[coord]?.stall?.let { stall ->
                            if (stall.id == proj.sourceStallId) {
                                val isKill = currentHealth <= 0
                                val newTargetIds = stall.uniqueTargetIds + enemy.id
                                val newKills = if (isKill) stall.kills + 1 else stall.kills
                                updatedHexes[coord] = updatedHexes[coord]!!.copy(stall = stall.copy(
                                    uniqueTargetIds = newTargetIds,
                                    kills = newKills
                                ))
                            }
                        }
                    }
                }

                if (currentHealth <= 0) {
                    updatedGold += enemy.reward
                    updatedScore += enemy.reward
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastHapticTimeMs >= 1000) {
                        viewModelScope.launch {
                            if (settingsRepository.settingsFlow.first().hapticEnabled) _hapticEvents.emit(Unit)
                        }
                        lastHapticTimeMs = currentTime
                    }
                    enemy.copy(health = 0, isDead = true)
                } else {
                    enemy.copy(health = currentHealth, freezeDurationMs = maxFreezeDuration, speedBoostDurationMs = speedBoostDuration)
                }
            } else enemy
        }.filter { !it.isDead }

        return state.copy(
            hexes = updatedHexes,
            enemies = finalEnemies,
            projectiles = finalProjectiles,
            visualEffects = newVisualEffects,
            gold = updatedGold,
            score = updatedScore
        )
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
        return GridUtils.axialDistance(a, b)
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
                            val updatedEnemies = recalculateEnemyPaths(state, blocked, newHexes)
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

        val blocked = getBlockedCoordinates(newHexes)

        _gameState.update { state ->
            val updatedEnemies = recalculateEnemyPaths(state, blocked, newHexes)
            state.copy(
                hexes = newHexes,
                gold = state.gold + refund,
                enemies = updatedEnemies,
                selectedBoardStall = null
            )
        }
    }

    fun upgradeStall() {
        _gameState.update { state ->
            val coord = state.selectedBoardStall ?: return@update state
            val tile = state.hexes[coord] ?: return@update state
            val stall = tile.stall ?: return@update state

            val baseStall = _availableStalls.value.find { it.stallType == stall.stallType } ?: stall
            val upgradeCost = stall.getUpgradeCost()

            if (state.gold >= upgradeCost) {
                val upgradeCategories = mutableListOf(0, 1, 2).apply { shuffle() }
                val stallDef = StallRegistry.get(stall.stallType)

                while (upgradeCategories.isNotEmpty()) {
                    val upgradeTypeIndex = upgradeCategories.removeAt(0)
                    val mutableUpgrades = stall.upgrades.toMutableMap()

                    var newDamage = stall.damage
                    var newRange = stall.range
                    var newFireRate = stall.fireRateMs
                    var newAoeRadius = stall.aoeRadius
                    var newEffectDuration = stall.effectDurationMs
                    var newFreezeDuration = stall.freezeDurationMs
                    var currentCategoryName = ""

                    when (upgradeTypeIndex) {
                        0 -> {
                            if (kotlin.random.Random.nextBoolean()) {
                                currentCategoryName = "Damage"
                                val damageIncrease = stallDef.getUpgradeDamageIncrease(baseStall.damage)
                                newDamage += damageIncrease
                                val newLevel = mutableUpgrades.getOrDefault("Damage", 0) + 1
                                if (newLevel % 10 == 0) {
                                    newDamage = Math.round(newDamage * 1.25f)
                                }
                                mutableUpgrades["Damage"] = newLevel
                            } else {
                                currentCategoryName = "Range"
                                newRange += 0.5f
                                val newLevel = mutableUpgrades.getOrDefault("Range", 0) + 1
                                if (newLevel % 10 == 0) {
                                    newRange *= 1.25f
                                }
                                mutableUpgrades["Range"] = newLevel
                            }
                        }
                        1 -> {
                            currentCategoryName = "Rate"
                            val rateReduction = (baseStall.fireRateMs * 0.1f).toLong()
                            var potentialRate = stall.fireRateMs - rateReduction
                            val newLevel = mutableUpgrades.getOrDefault("Rate", 0) + 1
                            if (newLevel % 10 == 0) {
                                potentialRate = Math.round(potentialRate * 0.75)
                            }

                            if (potentialRate < 50L) {
                                continue // Try another category
                            }

                            newFireRate = potentialRate
                            mutableUpgrades["Rate"] = newLevel
                        }
                        2 -> {
                            when (stall.stallType) {
                                StallType.SATAY, StallType.DURIAN -> {
                                    currentCategoryName = "Radius"
                                    newAoeRadius += 0.2f
                                    val newLevel = mutableUpgrades.getOrDefault("Radius", 0) + 1
                                    if (newLevel % 10 == 0) {
                                        newAoeRadius *= 1.25f
                                    }
                                    mutableUpgrades["Radius"] = newLevel
                                }
                                StallType.TEH_TARIK -> {
                                    currentCategoryName = "Duration"
                                    newEffectDuration += 500L
                                    val newLevel = mutableUpgrades.getOrDefault("Duration", 0) + 1
                                    if (newLevel % 10 == 0) {
                                        newEffectDuration = Math.round(newEffectDuration * 1.25)
                                    }
                                    mutableUpgrades["Duration"] = newLevel
                                }
                                StallType.ICE_KACHANG -> {
                                    currentCategoryName = "Effect"
                                    newFreezeDuration += 100L
                                    val newLevel = mutableUpgrades.getOrDefault("Effect", 0) + 1
                                    if (newLevel % 10 == 0) {
                                        newFreezeDuration = Math.round(newFreezeDuration * 1.25)
                                    }
                                    mutableUpgrades["Effect"] = newLevel
                                }
                                StallType.CHICKEN_RICE -> {
                                    currentCategoryName = "Damage"
                                    val damageIncrease = stallDef.getUpgradeDamageIncrease(baseStall.damage)
                                    newDamage += damageIncrease
                                    val newLevel = mutableUpgrades.getOrDefault("Damage", 0) + 1
                                    if (newLevel % 10 == 0) {
                                        newDamage = Math.round(newDamage * 1.25f)
                                    }
                                    mutableUpgrades["Damage"] = newLevel
                                }
                            }
                        }
                    }

                    var newPrefix = stall.legendaryPrefix
                    var newSuffix = stall.legendarySuffix
                    val newNamingCategories = stall.namingCategories.toMutableList()

                    val levelOfUpgradedCat = mutableUpgrades[currentCategoryName] ?: 0
                    if (levelOfUpgradedCat == 10 && !stall.namingCategories.contains(currentCategoryName)) {
                        if (stall.namingCategories.isEmpty()) {
                            newSuffix = LegendaryNames.getRandomSuffix(currentCategoryName)
                            newNamingCategories.add(currentCategoryName)
                        } else if (stall.namingCategories.size == 1) {
                            newPrefix = LegendaryNames.getRandomPrefix(currentCategoryName)
                            newNamingCategories.add(currentCategoryName)
                        }
                    }

                    val newName = LegendaryNames.constructName(stall.baseName, newPrefix, newSuffix)

                    val updatedStall = stall.copy(
                        name = newName,
                        damage = newDamage,
                        range = newRange,
                        fireRateMs = newFireRate,
                        aoeRadius = newAoeRadius,
                        effectDurationMs = newEffectDuration,
                        freezeDurationMs = newFreezeDuration,
                        upgradeCount = stall.upgradeCount + 1,
                        totalInvestment = stall.totalInvestment + upgradeCost,
                        upgrades = mutableUpgrades,
                        legendaryPrefix = newPrefix,
                        legendarySuffix = newSuffix,
                        namingCategories = newNamingCategories
                    )

                    val newHexes = state.hexes.toMutableMap()
                    newHexes[coord] = tile.copy(stall = updatedStall)
                    return@update state.copy(hexes = newHexes, gold = state.gold - upgradeCost)
                }
            }
            state
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

    private fun recalculateEnemyPaths(
        state: GameState,
        blocked: Set<AxialCoordinate>,
        hexes: Map<AxialCoordinate, HexTile>
    ): List<Enemy> {
        val endPos = state.endPosition ?: return state.enemies
        return state.enemies.map { enemy ->
            val currentTargetIndex = enemy.currentPathIndex + 1
            if (currentTargetIndex >= enemy.path.size) return@map enemy

            val currentTarget = enemy.path[currentTargetIndex]
            val newPathToFollow = Pathfinding.findPath(
                currentTarget, endPos, blocked, hexes.keys
            ) ?: listOf(currentTarget)

            val newPath = enemy.path.subList(0, currentTargetIndex + 1) + newPathToFollow.drop(1)

            val nextTarget = newPath.getOrNull(currentTargetIndex + 1) ?: currentTarget
            val newIsFacingLeft = if (nextTarget.q + nextTarget.r / 2f != enemy.position.q + enemy.position.r / 2f) {
                nextTarget.q + nextTarget.r / 2f < enemy.position.q + enemy.position.r / 2f
            } else {
                enemy.isFacingLeft
            }

            enemy.copy(path = newPath, isFacingLeft = newIsFacingLeft)
        }
    }

    private fun getBlockedCoordinates(hexes: Map<AxialCoordinate, HexTile>): Set<AxialCoordinate> {
        return hexes.values.filter {
            it.stall != null || it.type == TileType.PILLAR || it.type == TileType.GOAL_TABLE || it.type.name.startsWith("EDGE_")
        }.map { it.coordinate }.toSet()
    }
}
