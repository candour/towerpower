package com.messark.hawker

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messark.hawker.model.*
import com.messark.hawker.registry.*
import com.messark.hawker.utils.*
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

    private val random = kotlin.random.Random(System.currentTimeMillis())

    private val _logoVisible = MutableStateFlow(true)
    val logoVisible: StateFlow<Boolean> = _logoVisible.asStateFlow()

    private val enemyTiers = listOf(
        EnemyType.SALARYMAN,
        EnemyType.TOURIST,
        EnemyType.AUNTIE,
        EnemyType.DELIVERY_RIDER,
        EnemyType.TIGER_MOM
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

        _gameState.update { it.copy(goldEarnedThisWave = 0, showBonusMessage = false) }

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

        // Only allow Tiger Mom from wave 16 onwards, and only one per wave
        if (wave < 16) {
            allowedTiers = allowedTiers.filter { it != EnemyType.TIGER_MOM }
        }

        var attempts = 0
        while (remainingBudget > 0 && attempts < 100) {
            val type = allowedTiers[kotlin.random.Random.nextInt(allowedTiers.size)]

            if (type == EnemyType.TIGER_MOM && enemyList.contains(EnemyType.TIGER_MOM)) {
                attempts++
                continue
            }

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
        var starAwardedOutside = false
        var bonusAwardedOutside = 0
        _gameState.update { state ->
            if (state.activeTutorial != null) return@update state
            var newState = state

            // 0. Update Puddles and Visual Effects
            newState = updateTransients(newState, currentTimeMs)

            // 0.5 Update Held Enemies
            newState = updateHeldEnemies(newState, currentTimeMs)

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
                val starAwarded = newState.currentWave % 10 == 0
                starAwardedOutside = starAwarded
                val nextStars = if (starAwarded) newState.kitchelinStars + 1 else newState.kitchelinStars

                val bonusBudget = if (newState.activeBudgetBonuses > 0) {
                    (newState.goldEarnedThisWave * (0.10f * newState.activeBudgetBonuses)).toInt()
                } else 0
                bonusAwardedOutside = bonusBudget

                // Decrement disabledWaves for all stalls
                val updatedHexes = newState.hexes.mapValues { (_, tile) ->
                    tile.stall?.let { stall ->
                        if (stall.disabledWaves > 0) {
                            tile.copy(stall = stall.copy(disabledWaves = stall.disabledWaves - 1))
                        } else tile
                    } ?: tile
                }

                newState = newState.copy(
                    waveActive = false,
                    isBossWave = false,
                    kitchelinStars = nextStars,
                    hexes = updatedHexes,
                    gold = newState.gold + bonusBudget,
                    lastWaveBonusGold = bonusBudget,
                    showBonusMessage = bonusBudget > 0,
                    activeBudgetBonuses = 0
                )
                gameStateRepository.saveGameState(newState)
            }

            // 6. Game over check
            if (newState.health <= 0 && state.health > 0) {
                handleGameOver(newState)
            }

            newState
        }

        if (starAwardedOutside) {
            handleStarAwardedTutorial()
        }
        if (bonusAwardedOutside > 0) {
            viewModelScope.launch {
                delay(3000)
                _gameState.update { it.copy(showBonusMessage = false) }
            }
        }
    }

    private fun updateTransients(state: GameState, currentTimeMs: Long): GameState {
        val updatedPuddles = state.puddles.filter { currentTimeMs - it.spawnTimeMs < it.durationMs }
        val updatedVisualEffects = state.visualEffects.filter { currentTimeMs - it.startTimeMs < it.durationMs }
        return state.copy(puddles = updatedPuddles, visualEffects = updatedVisualEffects)
    }

    private fun handleSpawning(state: GameState, currentTimeMs: Long): GameState {
        if (state.waveActive && state.enemiesToSpawn > 0 && currentTimeMs - state.lastSpawnTimeMs > 1000 && state.hexes.isNotEmpty() && state.enemiesToSpawnList.isNotEmpty()) {
            val type = state.enemiesToSpawnList.first()

            // One Tiger Mom on board limit
            if (type == EnemyType.TIGER_MOM && state.enemies.any { it.type == EnemyType.TIGER_MOM }) {
                // Delay spawning by resetting lastSpawnTimeMs to try again next tick
                return state.copy(lastSpawnTimeMs = currentTimeMs - 500)
            }

            val startPos = state.startPosition ?: return state
            val endPos = state.endPosition ?: return state
            val path = Pathfinding.findPath(
                startPos, endPos, getBlockedCoordinates(state.hexes), state.hexes.keys
            ) ?: emptyList()

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
        val buffActions = mutableListOf<Pair<String, String>>() // TigerMomId, TargetEnemyId
        val stopBuffingIds = mutableSetOf<String>() // TigerMomIds

        val updatedEnemies = state.enemies.mapNotNull { enemy ->
            if (enemy.isDead) return@mapNotNull null
            if (enemy.isGrabbed) return@mapNotNull enemy

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
                if (enemy.type == EnemyType.TIGER_MOM && enemy.buffingTargetId != null) {
                    val targetExists = state.enemies.any { it.id == enemy.buffingTargetId && !it.isDead }
                    if (!targetExists) {
                        stopBuffingIds.add(enemy.id)
                        isStopped = false
                    }
                }

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

            var nextEnemy = if (dist < effectiveSpeed) {
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

            // Tiger Mom activation check
            if (nextEnemy.type == EnemyType.TIGER_MOM && !nextEnemy.hasActivatedBuff && nextEnemy.currentPathIndex > enemy.currentPathIndex) {
                if (random.nextFloat() < 0.125f) { // 1/8 chance
                    // Find nearest non-buffed enemy
                    val targetEnemy = state.enemies
                        .filter { it.id != nextEnemy.id && it.buffs.none { b -> b.type == BuffType.ARMOR } && !it.isDead && !it.isGrabbed }
                        .minByOrNull { axialDistance(nextEnemy.position, it.position) }

                    if (targetEnemy != null) {
                        buffActions.add(nextEnemy.id to targetEnemy.id)
                        nextEnemy = nextEnemy.copy(
                            isStopped = true,
                            stopDurationMs = 999999L, // "Until she is fully fed"
                            hasActivatedBuff = true,
                            buffingTargetId = targetEnemy.id
                        )
                    }
                }
            }

            nextEnemy
        }

        var finalEnemies = updatedEnemies
        if (buffActions.isNotEmpty()) {
            finalEnemies = finalEnemies.map { e ->
                val buffAction = buffActions.find { it.second == e.id }
                if (buffAction != null) {
                    e.copy(buffs = e.buffs + Buff(BuffType.ARMOR, buffAction.first, 0.9f))
                } else e
            }
        }

        if (stopBuffingIds.isNotEmpty()) {
            finalEnemies = finalEnemies.map { e ->
                if (stopBuffingIds.contains(e.id)) {
                    e.copy(buffingTargetId = null)
                } else {
                    // Also remove the buff from anyone who was being buffed by these Tiger Moms
                    e.copy(buffs = e.buffs.filter { !stopBuffingIds.contains(it.sourceId) })
                }
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
            if (stall != null && stall.disabledWaves == 0 && stall.heldEnemyId == null && currentTimeMs - stall.lastFiredMs >= stall.fireRateMs) {
                val stallPos = PreciseAxialCoordinate(coord.q.toFloat(), coord.r.toFloat())
                val potentialTargets = state.enemies.filter { enemy ->
                    !enemy.isGrabbed && axialDistance(enemy.position, stallPos) <= stall.range
                }

                val target = when (stall.targetMode) {
                    TargetMode.FIRST -> potentialTargets.maxByOrNull { it.currentPathIndex }
                    TargetMode.CLOSEST -> potentialTargets.minByOrNull { axialDistance(it.position, stallPos) }
                    TargetMode.STRONGEST -> potentialTargets.maxByOrNull { it.health }
                    TargetMode.WEAKEST -> potentialTargets.minByOrNull { it.health }
                }

                if (target != null) {
                    if (stall.stallType == StallType.TRAY_RETURN_UNCLE) {
                        val updatedStall = stall.copy(
                            lastFiredMs = currentTimeMs,
                            heldEnemyId = target.id,
                            releaseTimeMs = currentTimeMs + stall.effectDurationMs,
                            uniqueTargetIds = stall.uniqueTargetIds + target.id
                        )
                        updatedHexes[coord] = tile.copy(stall = updatedStall)
                    } else {
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
                state.enemies.find { it.id == proj.targetEnemyId && !it.isGrabbed }?.position ?: proj.targetPosition
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
                    if (enemy.isGrabbed) return@forEach
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
                var currentHealth = enemy.health.toFloat()
                var maxFreezeDuration = enemy.freezeDurationMs
                var speedBoostDuration = enemy.speedBoostDurationMs

                hits.forEach { proj ->
                    if (currentHealth <= 0) return@forEach

                    var damage = proj.damage.toFloat()

                    // Apply Armor Buffs
                    enemy.buffs.forEach { buff ->
                        if (buff.type == BuffType.ARMOR) {
                            damage *= (1.0f - buff.value)
                        }
                    }

                    var freezeDuration = proj.freezeDurationMs

                    // Apply modifiers
                    if (proj.sourceStallType != null) {
                        val stallDef = StallRegistry.get(proj.sourceStallType)
                        damage = stallDef.applyDamageModifiers(enemy, damage)
                        freezeDuration = stallDef.getFreezeModifier(enemy, freezeDuration)
                        val boost = stallDef.getSpeedBoost(enemy)
                        if (boost > 0) speedBoostDuration = boost
                    }

                    val damageDealt = damage
                    currentHealth = Math.max(0f, currentHealth - damageDealt)
                    maxFreezeDuration = Math.max(maxFreezeDuration, freezeDuration)

                    // Track hit and kill
                    if (proj.sourceStallCoord != null && proj.sourceStallId != null) {
                        val coord = proj.sourceStallCoord
                        updatedHexes[coord]?.stall?.let { stall ->
                            if (stall.id == proj.sourceStallId) {
                                val isKill = currentHealth <= 0
                                val newTargetIds = stall.uniqueTargetIds + enemy.id
                                // Only count kill if stall is NOT a utility stall
                                val newKills = if (isKill && !stall.stallType.isUtility) stall.kills + 1 else stall.kills
                                updatedHexes[coord] = updatedHexes[coord]!!.copy(stall = stall.copy(
                                    uniqueTargetIds = newTargetIds,
                                    kills = newKills
                                ))
                            }
                        }
                    }
                }

                val finalHealthInt = currentHealth.toInt()
                if (finalHealthInt <= 0) {
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
                    enemy.copy(health = finalHealthInt, freezeDurationMs = maxFreezeDuration, speedBoostDurationMs = speedBoostDuration)
                }
            } else enemy
        }.filter { !it.isDead }.map { e ->
            // Clean up buffs: source must be alive, still targeting this enemy, and not grabbed
            val validBuffs = e.buffs.filter { buff ->
                val source = state.enemies.find { it.id == buff.sourceId }
                source != null && source.buffingTargetId == e.id && !source.isGrabbed
            }
            if (validBuffs.size != e.buffs.size) {
                e.copy(buffs = validBuffs)
            } else e
        }

        return state.copy(
            hexes = updatedHexes,
            enemies = finalEnemies,
            projectiles = finalProjectiles,
            visualEffects = newVisualEffects,
            gold = updatedGold,
            score = updatedScore,
            goldEarnedThisWave = state.goldEarnedThisWave + (updatedGold - state.gold)
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

                // Check "last empty space" rule for Tray Return Uncle
                val trayUncles = currentState.hexes.filter { it.value.stall?.stallType == StallType.TRAY_RETURN_UNCLE }.toMutableMap()
                if (stallToPlace.stallType == StallType.TRAY_RETURN_UNCLE) {
                    trayUncles[coord] = tile // Add current tile as potential Uncle
                }

                var violatesTrayUncleRule = false
                for ((uncleCoord, _) in trayUncles) {
                    val uncleNeighbors = getAdjacentCoordinates(uncleCoord)
                    val freeUncleNeighbors = uncleNeighbors.filter {
                        currentState.hexes.containsKey(it) && !blocked.contains(it) && it != coord && currentState.hexes[it]?.type == TileType.FLOOR
                    }
                    if (freeUncleNeighbors.isEmpty()) {
                        violatesTrayUncleRule = true
                        break
                    }
                }

                if (violatesTrayUncleRule) return

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
            var updatedEnemies = state.enemies
            if (stall.heldEnemyId != null) {
                updatedEnemies = updatedEnemies.map { enemy ->
                    if (enemy.id == stall.heldEnemyId) {
                        releaseEnemy(enemy, coord, newHexes, state.endPosition)
                    } else enemy
                }
            }
            updatedEnemies = recalculateEnemyPaths(state.copy(enemies = updatedEnemies), blocked, newHexes)
            state.copy(
                hexes = newHexes,
                gold = state.gold + refund,
                enemies = updatedEnemies,
                selectedBoardStall = null
            )
        }
    }

    fun upgradeStall() {
        if (_gameState.value.waveActive) {
            applyUpgrade(isSpecific = false)
        } else {
            _gameState.update { it.copy(showUpgradeOverlay = true) }
        }
    }

    fun dismissUpgradeOverlay() {
        _gameState.update { it.copy(showUpgradeOverlay = false) }
    }

    fun openStarActionOverlay() {
        if (!_gameState.value.waveActive && _gameState.value.kitchelinStars > 0) {
            _gameState.update { it.copy(showStarActionOverlay = true) }
        }
    }

    fun dismissStarActionOverlay() {
        _gameState.update { it.copy(showStarActionOverlay = false) }
    }

    fun chooseBudgetBonus() {
        _gameState.update {
            if (it.kitchelinStars > 0) {
                it.copy(
                    kitchelinStars = it.kitchelinStars - 1,
                    activeBudgetBonuses = it.activeBudgetBonuses + 1,
                    showStarActionOverlay = false
                )
            } else it
        }
    }

    fun chooseFreeUpgrade() {
        _gameState.update {
            if (it.kitchelinStars > 0) {
                it.copy(
                    kitchelinStars = it.kitchelinStars - 1,
                    freeSpecificUpgrades = it.freeSpecificUpgrades + 1,
                    showStarActionOverlay = false
                )
            } else it
        }
    }

    fun upgradeStallRandomly() {
        applyUpgrade(isSpecific = false)
        dismissUpgradeOverlay()
    }

    fun upgradeStallSpecifically(stat: String) {
        applyUpgrade(isSpecific = true, specificStat = stat)
        dismissUpgradeOverlay()
    }

    private fun applyUpgrade(isSpecific: Boolean, specificStat: String? = null) {
        _gameState.update { state ->
            val coord = state.selectedBoardStall ?: return@update state
            val tile = state.hexes[coord] ?: return@update state
            val stall = tile.stall ?: return@update state

            val baseStall = _availableStalls.value.find { it.stallType == stall.stallType } ?: stall
            val baseUpgradeCost = stall.getUpgradeCost()
            val finalUpgradeCost = if (isSpecific) {
                if (state.freeSpecificUpgrades > 0) 0 else baseUpgradeCost * 2
            } else {
                baseUpgradeCost
            }

            if (state.gold >= finalUpgradeCost) {
                val stallDef = StallRegistry.get(stall.stallType)
                val mutableUpgrades = stall.upgrades.toMutableMap()

                var newDamage = stall.damage
                var newRange = stall.range
                var newFireRate = stall.fireRateMs
                var newAoeRadius = stall.aoeRadius
                var newEffectDuration = stall.effectDurationMs
                var newFreezeDuration = stall.freezeDurationMs
                var currentCategoryName = ""

                if (isSpecific && specificStat != null) {
                    currentCategoryName = specificStat
                    when (specificStat) {
                        "Damage" -> {
                            newDamage = Math.round(newDamage * 1.2f)
                            val newLevel = mutableUpgrades.getOrDefault("Damage", 0) + 1
                            if (newLevel % 10 == 0) newDamage = Math.round(newDamage * 1.25f)
                            mutableUpgrades["Damage"] = newLevel
                        }
                        "Range" -> {
                            newRange += 0.5f
                            val newLevel = mutableUpgrades.getOrDefault("Range", 0) + 1
                            if (newLevel % 10 == 0) newRange *= 1.25f
                            mutableUpgrades["Range"] = newLevel
                        }
                        "Rate", "Grab Rate" -> {
                            val rateReduction = when (stall.stallType) {
                                StallType.TRAY_RETURN_UNCLE -> 100L
                                StallType.CHICKEN_RICE -> 15L
                                StallType.DURIAN -> 50L
                                StallType.SATAY -> 25L
                                else -> (baseStall.fireRateMs * 0.1f).toLong()
                            }
                            val newLevel = mutableUpgrades.getOrDefault(specificStat, 0) + 1
                            var potentialRate = stall.fireRateMs - rateReduction
                            if (newLevel % 10 == 0) potentialRate = Math.round(potentialRate * 0.75)
                            val floor = when (stall.stallType) {
                                StallType.TRAY_RETURN_UNCLE -> 10000L
                                StallType.CHICKEN_RICE -> 200L
                                StallType.DURIAN -> 1000L
                                StallType.SATAY -> 750L
                                else -> 50L
                            }
                            newFireRate = Math.max(floor, potentialRate)
                            mutableUpgrades[specificStat] = newLevel
                            if (stall.stallType == StallType.TRAY_RETURN_UNCLE) mutableUpgrades["Rate"] = newLevel
                        }
                        "Radius" -> {
                            newAoeRadius += 0.2f
                            val newLevel = mutableUpgrades.getOrDefault("Radius", 0) + 1
                            if (newLevel % 10 == 0) newAoeRadius *= 1.25f
                            mutableUpgrades["Radius"] = newLevel
                        }
                        "Duration", "Cleaning Time" -> {
                            val newLevel = mutableUpgrades.getOrDefault(specificStat, 0) + 1
                            var potentialDuration = if (stall.stallType == StallType.TRAY_RETURN_UNCLE) stall.effectDurationMs + 100L else stall.effectDurationMs + 500L
                            if (newLevel % 10 == 0) potentialDuration = Math.round(potentialDuration * 1.25)
                            val cap = if (stall.stallType == StallType.TRAY_RETURN_UNCLE) 4000L else Long.MAX_VALUE
                            newEffectDuration = Math.min(cap, potentialDuration)
                            mutableUpgrades[specificStat] = newLevel
                            mutableUpgrades["Duration"] = newLevel
                        }
                        "Effect" -> {
                            newFreezeDuration += 100L
                            val newLevel = mutableUpgrades.getOrDefault("Effect", 0) + 1
                            if (newLevel % 10 == 0) newFreezeDuration = Math.round(newFreezeDuration * 1.25)
                            mutableUpgrades["Effect"] = newLevel
                        }
                    }
                } else {
                    val upgradeCategories = mutableListOf(0, 1, 2).apply { shuffle() }
                    var applied = false
                    while (upgradeCategories.isNotEmpty() && !applied) {
                        val upgradeTypeIndex = upgradeCategories.removeAt(0)
                        when (upgradeTypeIndex) {
                            0 -> {
                                if (stall.stallType == StallType.TRAY_RETURN_UNCLE) {
                                    if (kotlin.random.Random.nextBoolean()) {
                                        currentCategoryName = "Grab Rate"
                                        val rateReduction = 100L
                                        val newLevel = mutableUpgrades.getOrDefault("Grab Rate", 0) + 1
                                        var potentialRate = stall.fireRateMs - rateReduction
                                        if (newLevel % 10 == 0) potentialRate = Math.round(potentialRate * 0.75)
                                        newFireRate = Math.max(10000L, potentialRate)
                                        mutableUpgrades["Grab Rate"] = newLevel
                                        mutableUpgrades["Rate"] = newLevel
                                    } else {
                                        currentCategoryName = "Cleaning Time"
                                        val newLevel = mutableUpgrades.getOrDefault("Cleaning Time", 0) + 1
                                        var potentialDuration = stall.effectDurationMs + 100L
                                        if (newLevel % 10 == 0) potentialDuration = Math.round(potentialDuration * 1.25)
                                        newEffectDuration = Math.min(4000L, potentialDuration)
                                        mutableUpgrades["Cleaning Time"] = newLevel
                                        mutableUpgrades["Duration"] = newLevel
                                    }
                                } else {
                                    if (kotlin.random.Random.nextBoolean() && !stall.stallType.isUtility) {
                                        currentCategoryName = "Damage"
                                        newDamage = Math.round(newDamage * 1.15f)
                                        val newLevel = mutableUpgrades.getOrDefault("Damage", 0) + 1
                                        if (newLevel % 10 == 0) newDamage = Math.round(newDamage * 1.25f)
                                        mutableUpgrades["Damage"] = newLevel
                                    } else {
                                        currentCategoryName = "Range"
                                        newRange += 0.5f
                                        val newLevel = mutableUpgrades.getOrDefault("Range", 0) + 1
                                        if (newLevel % 10 == 0) newRange *= 1.25f
                                        mutableUpgrades["Range"] = newLevel
                                    }
                                }
                                applied = true
                            }
                            1 -> {
                                currentCategoryName = if (stall.stallType == StallType.TRAY_RETURN_UNCLE) "Grab Rate" else "Rate"
                                val rateReduction = when (stall.stallType) {
                                    StallType.TRAY_RETURN_UNCLE -> 100L
                                    StallType.CHICKEN_RICE -> 15L
                                    StallType.DURIAN -> 50L
                                    StallType.SATAY -> 25L
                                    else -> (baseStall.fireRateMs * 0.1f).toLong()
                                }
                                val newLevel = mutableUpgrades.getOrDefault(currentCategoryName, 0) + 1
                                var potentialRate = stall.fireRateMs - rateReduction
                                if (newLevel % 10 == 0) potentialRate = Math.round(potentialRate * 0.75)
                                val floor = when (stall.stallType) {
                                    StallType.TRAY_RETURN_UNCLE -> 10000L
                                    StallType.CHICKEN_RICE -> 200L
                                    StallType.DURIAN -> 1000L
                                    StallType.SATAY -> 750L
                                    else -> 50L
                                }
                                newFireRate = Math.max(floor, potentialRate)
                                mutableUpgrades[currentCategoryName] = newLevel
                                if (stall.stallType == StallType.TRAY_RETURN_UNCLE) mutableUpgrades["Rate"] = newLevel
                                applied = true
                            }
                            2 -> {
                                when (stall.stallType) {
                                    StallType.SATAY, StallType.DURIAN -> {
                                        currentCategoryName = "Radius"
                                        newAoeRadius += 0.2f
                                        val newLevel = mutableUpgrades.getOrDefault("Radius", 0) + 1
                                        if (newLevel % 10 == 0) newAoeRadius *= 1.25f
                                        mutableUpgrades["Radius"] = newLevel
                                    }
                                    StallType.TEH_TARIK -> {
                                        currentCategoryName = "Duration"
                                        newEffectDuration += 500L
                                        val newLevel = mutableUpgrades.getOrDefault("Duration", 0) + 1
                                        if (newLevel % 10 == 0) newEffectDuration = Math.round(newEffectDuration * 1.25)
                                        mutableUpgrades["Duration"] = newLevel
                                    }
                                    StallType.ICE_KACHANG -> {
                                        currentCategoryName = "Effect"
                                        newFreezeDuration += 100L
                                        val newLevel = mutableUpgrades.getOrDefault("Effect", 0) + 1
                                        if (newLevel % 10 == 0) newFreezeDuration = Math.round(newFreezeDuration * 1.25)
                                        mutableUpgrades["Effect"] = newLevel
                                    }
                                    StallType.CHICKEN_RICE -> {
                                        currentCategoryName = "Damage"
                                        newDamage = Math.round(newDamage * 1.15f)
                                        val newLevel = mutableUpgrades.getOrDefault("Damage", 0) + 1
                                        if (newLevel % 10 == 0) newDamage = Math.round(newDamage * 1.25f)
                                        mutableUpgrades["Damage"] = newLevel
                                    }
                                    StallType.TRAY_RETURN_UNCLE -> {
                                        currentCategoryName = "Cleaning Time"
                                        val newLevel = mutableUpgrades.getOrDefault(currentCategoryName, 0) + 1
                                        var potentialDuration = stall.effectDurationMs + 100L
                                        if (newLevel % 10 == 0) potentialDuration = Math.round(potentialDuration * 1.25)
                                        newEffectDuration = Math.min(4000L, potentialDuration)
                                        mutableUpgrades["Cleaning Time"] = newLevel
                                        mutableUpgrades["Duration"] = newLevel
                                    }
                                    else -> {
                                        if (stall.stallType.isUtility) {
                                            currentCategoryName = "Range"
                                            newRange += 0.5f
                                            val newLevel = mutableUpgrades.getOrDefault("Range", 0) + 1
                                            if (newLevel % 10 == 0) newRange *= 1.25f
                                            mutableUpgrades["Range"] = newLevel
                                        }
                                    }
                                }
                                applied = true
                            }
                        }
                    }
                }

                var newPrefix = stall.legendaryPrefix
                var newSuffix = stall.legendarySuffix
                val newNamingCategories = stall.namingCategories.toMutableList()

                val levelOfUpgradedCat = mutableUpgrades[currentCategoryName] ?: 0
                if (levelOfUpgradedCat == 10 && !stall.namingCategories.contains(currentCategoryName)) {
                    val legendaryCat = when(currentCategoryName) {
                        "Grab Rate" -> "Rate"
                        "Cleaning Time" -> "Duration"
                        else -> currentCategoryName
                    }
                    if (stall.namingCategories.isEmpty()) {
                        newSuffix = LegendaryNames.getRandomSuffix(legendaryCat)
                        newNamingCategories.add(currentCategoryName)
                    } else if (stall.namingCategories.size == 1) {
                        newPrefix = LegendaryNames.getRandomPrefix(legendaryCat)
                        newNamingCategories.add(currentCategoryName)
                    }
                }

                val newName = LegendaryNames.constructName(stall.baseName, newPrefix, newSuffix)

                var freeUpgradesLeft = state.freeSpecificUpgrades
                var disabledWaves = stall.disabledWaves
                if (isSpecific) {
                    if (freeUpgradesLeft > 0) {
                        freeUpgradesLeft -= 1
                    } else {
                        disabledWaves += 1
                    }
                }

                val updatedStall = stall.copy(
                    name = newName,
                    damage = newDamage,
                    range = newRange,
                    fireRateMs = newFireRate,
                    aoeRadius = newAoeRadius,
                    effectDurationMs = newEffectDuration,
                    freezeDurationMs = newFreezeDuration,
                    upgradeCount = stall.upgradeCount + 1,
                    totalInvestment = stall.totalInvestment + finalUpgradeCost,
                    upgrades = mutableUpgrades,
                    legendaryPrefix = newPrefix,
                    legendarySuffix = newSuffix,
                    namingCategories = newNamingCategories,
                    disabledWaves = disabledWaves
                )

                val newHexes = state.hexes.toMutableMap()
                newHexes[coord] = tile.copy(stall = updatedStall)
                return@update state.copy(
                    hexes = newHexes,
                    gold = state.gold - finalUpgradeCost,
                    freeSpecificUpgrades = freeUpgradesLeft
                )
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

    private fun updateHeldEnemies(state: GameState, currentTimeMs: Long): GameState {
        var updatedHexes = state.hexes.toMutableMap()
        var updatedEnemies = state.enemies.toMutableList()
        var changed = false

        state.hexes.forEach { (coord, tile) ->
            val stall = tile.stall
            if (stall != null && stall.heldEnemyId != null) {
                val enemyIndex = updatedEnemies.indexOfFirst { it.id == stall.heldEnemyId }
                if (enemyIndex != -1) {
                    val enemy = updatedEnemies[enemyIndex]
                    if (currentTimeMs >= stall.releaseTimeMs) {
                        // Release enemy
                        var releasedEnemy = releaseEnemy(enemy, coord, state.hexes, state.endPosition)
                        if (releasedEnemy.type == EnemyType.TIGER_MOM && releasedEnemy.buffingTargetId != null) {
                            // Interrupt buff
                            releasedEnemy = releasedEnemy.copy(
                                buffingTargetId = null,
                                isStopped = false,
                                stopDurationMs = 0
                            )
                        }
                        updatedEnemies[enemyIndex] = releasedEnemy
                        updatedHexes[coord] = tile.copy(stall = stall.copy(heldEnemyId = null))
                        changed = true
                    } else {
                        // Move to stall center
                        if (!enemy.isGrabbed || enemy.position.q != coord.q.toFloat() || enemy.position.r != coord.r.toFloat()) {
                            updatedEnemies[enemyIndex] = enemy.copy(
                                isGrabbed = true,
                                position = PreciseAxialCoordinate(coord.q.toFloat(), coord.r.toFloat())
                            )
                            changed = true
                        }
                    }
                } else {
                    // Enemy gone?
                    updatedHexes[coord] = tile.copy(stall = stall.copy(heldEnemyId = null))
                    changed = true
                }
            }
        }

        return if (changed) state.copy(hexes = updatedHexes, enemies = updatedEnemies) else state
    }

    private fun releaseEnemy(
        enemy: Enemy,
        stallCoord: AxialCoordinate,
        hexes: Map<AxialCoordinate, HexTile>,
        endPos: AxialCoordinate?
    ): Enemy {
        val adjacentCoords = getAdjacentCoordinates(stallCoord)

        val blocked = getBlockedCoordinates(hexes)
        val validTiles = adjacentCoords.filter { adj ->
            hexes.containsKey(adj) && !blocked.contains(adj) && hexes[adj]?.type != TileType.PILLAR && hexes[adj]?.type != TileType.GOAL_TABLE && hexes[adj]?.type?.name?.startsWith("EDGE_") == false
        }

        val releaseCoord = if (validTiles.isNotEmpty()) {
            validTiles[random.nextInt(validTiles.size)]
        } else {
            stallCoord // Fallback to stall coord if no adjacent is free, though shouldn't happen with our placement rule
        }

        val preciseRelease = PreciseAxialCoordinate(releaseCoord.q.toFloat(), releaseCoord.r.toFloat())

        // Recalculate path from release point
        val newPath = if (endPos != null) {
            Pathfinding.findPath(releaseCoord, endPos, blocked, hexes.keys) ?: listOf(releaseCoord)
        } else {
            listOf(releaseCoord)
        }

        return enemy.copy(
            isGrabbed = false,
            position = preciseRelease,
            path = newPath,
            currentPathIndex = 0
        )
    }

    private fun handleStarAwardedTutorial() {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.showTutorials && !settings.shownTutorials.contains("kitchelin_star")) {
                val tutorial = TutorialData(
                    id = "kitchelin_star",
                    type = TutorialType.KITCHELIN_STAR,
                    title = "You’ve got a Kitchelin star!",
                    description = "Kitchelin stars are awarded occasionally. Tap the stars in the top-left between waves to choose a powerful bonus action!"
                )
                _gameState.update { it.copy(activeTutorial = tutorial) }
                settingsRepository.updateSettings {
                    it.copy(shownTutorials = it.shownTutorials + "kitchelin_star")
                }
            }
        }
    }

    private fun getAdjacentCoordinates(coord: AxialCoordinate): List<AxialCoordinate> {
        return listOf(
            AxialCoordinate(coord.q + 1, coord.r),
            AxialCoordinate(coord.q + 1, coord.r - 1),
            AxialCoordinate(coord.q, coord.r - 1),
            AxialCoordinate(coord.q - 1, coord.r),
            AxialCoordinate(coord.q - 1, coord.r + 1),
            AxialCoordinate(coord.q, coord.r + 1)
        )
    }
}
