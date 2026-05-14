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
    private val gameStateRepository: GameStateRepository = GameStateRepository(application),
    private val random: kotlin.random.Random = kotlin.random.Random(System.currentTimeMillis())
) : AndroidViewModel(application) {
    internal val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _logoVisible = MutableStateFlow(true)
    val logoVisible: StateFlow<Boolean> = _logoVisible.asStateFlow()

    private val enemyTiers = listOf(
        EnemyType.SALARYMAN,
        EnemyType.TOURIST,
        EnemyType.AUNTIE,
        EnemyType.DELIVERY_RIDER,
        EnemyType.TIGER_MOM
    )

    private val earlyWaveConfigs = mapOf(
        1 to listOf(EnemyType.SALARYMAN to 5),
        2 to listOf(EnemyType.SALARYMAN to 6),
        3 to listOf(EnemyType.SALARYMAN to 5, EnemyType.TOURIST to 1),
        4 to listOf(EnemyType.SALARYMAN to 6, EnemyType.TOURIST to 1),
        5 to listOf(EnemyType.SALARYMAN to 5, EnemyType.TOURIST to 2),
        6 to listOf(EnemyType.SALARYMAN to 4, EnemyType.TOURIST to 2, EnemyType.AUNTIE to 1)
    )

    private val _availableStalls = MutableStateFlow(
        StallRegistry.all().map { it.toStall() }
    )
    val availableStalls: StateFlow<List<Stall>> = _availableStalls.asStateFlow()

    internal var gameJob: Job? = null
    private var lastHapticTimeMs = 0L

    private val _hapticEvents = MutableSharedFlow<Unit>()
    val hapticEvents: SharedFlow<Unit> = _hapticEvents.asSharedFlow()

    init {
        initializeGame()
        startGameLoop()
    }

    private fun initializeGame() {
        val (hexes, startPos, endPos) = MapGenerator.generateRandomVerticalMap(width = 8, height = 16, random = random)

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

    fun applyCheat() {
        val current = _gameState.value
        val base = if (current.currentScreen == AppScreen.GAME) {
            current
        } else {
            gameStateRepository.loadGameState() ?: return
        }
        val cheated = base.copy(
            gold = base.gold + 5000,
            kitchelinStars = base.kitchelinStars + 1
        )
        gameStateRepository.saveGameState(cheated)
        if (current.currentScreen == AppScreen.GAME) {
            _gameState.value = cheated
        }
    }

    fun resumeGame() {
        val savedState = gameStateRepository.loadGameState()
        if (savedState != null) {
            _gameState.value = savedState
        }
    }

    fun resetGame() {
        gameStateRepository.deleteGameState()
        val (hexes, startPos, endPos) = MapGenerator.generateRandomVerticalMap(width = 8, height = 16, random = random)

        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            var tutorialToShow: TutorialData? = null

            if (settings.showTutorials && !settings.shownTutorials.contains("game_aim")) {
                tutorialToShow = TutorialData(
                    id = "game_aim",
                    type = TutorialType.GAME_AIM,
                    title = "Chope your table!",
                    description = "Wah lau! Everyone is rushing for the best table in the hawker center! You must stop the hungry crowd from filling up the Goal Table by feeding them first. Don't let them chope your spot, okay?"
                )
                settingsRepository.updateSettings {
                    it.copy(shownTutorials = it.shownTutorials + "game_aim")
                }
            }

            _gameState.update {
                GameState(
                    currentScreen = AppScreen.GAME,
                    hexes = hexes,
                    startPosition = startPos,
                    endPosition = endPos,
                    gold = 500,
                    score = 0,
                    activeTutorial = tutorialToShow
                )
            }
        }
    }

    fun selectStall(stall: Stall) {
        _gameState.update { it.copy(selectedStallType = stall, lastSoldStall = null) }
    }

    fun startWave() {
        val currentState = _gameState.value
        if (currentState.waveActive || currentState.activeTutorial != null) return

        val hasBkt = currentState.hexes.values.any { it.stall?.stallType == StallType.BAK_KUT_TEH }
        val (buffType, toast) = if (hasBkt) {
            if (random.nextBoolean()) {
                BktBuffType.MEATY to "Meaty!"
            } else {
                BktBuffType.HERBAL to "Herbal!"
            }
        } else {
            currentState.bktBuffType to null
        }

        _gameState.update { it.copy(
            goldEarnedThisWave = 0,
            showBonusMessage = false,
            lastSoldStall = null,
            bktBuffType = buffType,
            bktToastMessage = toast
        ) }

        if (toast != null) {
            viewModelScope.launch {
                delay(2000)
                _gameState.update { it.copy(bktToastMessage = null) }
            }
        }

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
        earlyWaveConfigs[wave]?.let { config ->
            val list = config.flatMap { (type, count) -> List(count) { type } }
            return list.shuffled(random)
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

        while (remainingBudget > 0) {
            val affordableTiers = allowedTiers.filter {
                getEnemyHP(it, wave) <= remainingBudget &&
                        (it != EnemyType.TIGER_MOM || !enemyList.contains(EnemyType.TIGER_MOM))
            }

            if (affordableTiers.isEmpty()) break

            val type = affordableTiers[random.nextInt(affordableTiers.size)]
            enemyList.add(type)
            remainingBudget -= getEnemyHP(type, wave)
        }

        if (enemyList.isEmpty()) {
            enemyList.add(EnemyType.SALARYMAN)
        }

        return enemyList.shuffled(random)
    }

    private fun getEnemyHP(type: EnemyType, wave: Int): Float {
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
        var hapticRequested = false
        var gameOverState: GameState? = null

        _gameState.update { state ->
            if (state.activeTutorial != null) return@update state
            var newState = state

            // 0. Update Transients (Puddles, Effects, and Held Enemies)
            newState = updateTransientState(newState, currentTimeMs)

            // 1. Spawning
            newState = handleSpawning(newState, currentTimeMs)

            // 2. Enemy Movement
            val (movedState, updatedEnemies) = handleEnemyMovement(newState, currentTimeMs)
            newState = movedState.copy(enemies = updatedEnemies)

            // 3. Stall Firing
            newState = handleStallFiring(newState, currentTimeMs)

            // 4. Projectile Movement and Collision
            val (projectilesState, hitHaptic) = handleProjectiles(newState, currentTimeMs)
            newState = projectilesState
            if (hitHaptic) hapticRequested = true

            // 5. Wave completion check
            if (newState.waveActive && newState.enemiesToSpawn == 0 && newState.enemies.isEmpty()) {
                val starAwarded = newState.currentWave % 10 == 0
                starAwardedOutside = starAwarded
                val preNewStarCount = newState.kitchelinStars
                val nextStars = if (starAwarded) newState.kitchelinStars + 1 else newState.kitchelinStars

                val bonusBudget = (newState.goldEarnedThisWave * (0.01f * preNewStarCount + 1.00f * newState.activeBudgetBonuses)).toInt()
                bonusAwardedOutside = bonusBudget

                // 1. Identify which stalls were enabled before decrementing (for ATM income eligibility)
                val previouslyEnabledStalls = newState.hexes.filter { it.value.stall?.disabledWaves == 0 }.keys

                // 2. Decrement disabledWaves for all stalls
                val updatedHexes = newState.hexes.mapValues { (_, tile) ->
                    tile.stall?.let { stall ->
                        if (stall.disabledWaves > 0) {
                            tile.copy(stall = stall.copy(disabledWaves = stall.disabledWaves - 1))
                        } else tile
                    } ?: tile
                }.toMutableMap()

                // 3. Collect ATM income and update BKT stats
                var atmGold = 0
                val atmEffects = mutableListOf<VisualEffect>()

                updatedHexes.toList().forEach { (coord, tile) ->
                    val stall = tile.stall
                    // Use previouslyEnabledStalls check for ATM income eligibility
                    if (stall != null && previouslyEnabledStalls.contains(coord)) {
                        val stallDef = StallRegistry.get(stall.stallType)
                        if (stallDef.passiveIncome > 0) {
                            // calculateStatBoost uses the updated hexes, so re-enabled BKTs are counted
                        val boostResult = calculateStatBoost(coord, updatedHexes, newState.bktBuffType)
                        val boost = if (newState.bktBuffType == BktBuffType.MEATY) boostResult.damageMultiplier else 1.0f
                            atmGold += (stallDef.passiveIncome * boost).toInt()

                            // Update Bak Kut Teh stats
                        val providers = boostResult.providerCoords
                            providers.forEach { providerCoord ->
                                val providerTile = updatedHexes[providerCoord]
                                if (providerTile?.stall != null) {
                                    val updatedBkt = providerTile.stall.copy(
                                        uniqueTargetIds = providerTile.stall.uniqueTargetIds + stall.id
                                    )
                                    updatedHexes[providerCoord] = providerTile.copy(stall = updatedBkt)
                                }
                            }

                            atmEffects.add(
                                VisualEffect(
                                    id = UUID.randomUUID().toString(),
                                    position = PreciseAxialCoordinate(coord.q.toFloat(), coord.r.toFloat()),
                                    color = Color.Green,
                                    startTimeMs = currentTimeMs,
                                    durationMs = 1000L,
                                    type = VisualEffectType.MONEY_SPRAY
                                )
                            )
                        }
                    }
                }

                newState = newState.copy(
                    waveActive = false,
                    isBossWave = false,
                    kitchelinStars = nextStars,
                    hexes = updatedHexes,
                    gold = newState.gold + bonusBudget + atmGold,
                    lastWaveBonusGold = bonusBudget,
                    showBonusMessage = bonusBudget > 0,
                    activeBudgetBonuses = 0,
                    visualEffects = newState.visualEffects + atmEffects
                )
                gameStateRepository.saveGameState(newState)
            }

            // 6. Game over check
            if (newState.health <= 0 && state.health > 0) {
                gameOverState = newState
            }

            newState
        }

        if (hapticRequested) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastHapticTimeMs >= 1000) {
                triggerHaptic()
                lastHapticTimeMs = currentTime
            }
        }

        if (gameOverState != null) {
            handleGameOver(gameOverState!!)
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

    private fun updateTransientState(state: GameState, currentTimeMs: Long): GameState {
        val anyPuddleExpired = state.puddles.any { currentTimeMs - it.spawnTimeMs >= it.durationMs }
        val anyEffectExpired = state.visualEffects.any { currentTimeMs - it.startTimeMs >= it.durationMs }

        var updatedHexes: MutableMap<AxialCoordinate, HexTile>? = null
        var updatedEnemies: MutableList<Enemy>? = null

        val enemyIndexMap = if (state.hexes.values.any { it.stall?.heldEnemyId != null }) {
            state.enemies.withIndex().associate { it.value.id to it.index }
        } else emptyMap()

        state.hexes.forEach { (coord, tile) ->
            val stall = tile.stall
            if (stall?.heldEnemyId != null) {
                val enemyIndex = enemyIndexMap[stall.heldEnemyId] ?: -1
                if (enemyIndex != -1) {
                    val enemy = (updatedEnemies ?: state.enemies)[enemyIndex]
                    if (currentTimeMs >= stall.releaseTimeMs) {
                        // Release enemy
                        var releasedEnemy = releaseEnemy(enemy, coord, state.hexes, state.endPosition)
                        if (releasedEnemy.type == EnemyType.TIGER_MOM && releasedEnemy.buffingTargetId != null) {
                            releasedEnemy = releasedEnemy.copy(
                                buffingTargetId = null,
                                isStopped = false,
                                stopDurationMs = 0
                            )
                        }
                        if (updatedEnemies == null) updatedEnemies = state.enemies.toMutableList()
                        updatedEnemies!![enemyIndex] = releasedEnemy

                        if (updatedHexes == null) updatedHexes = state.hexes.toMutableMap()
                        updatedHexes!![coord] = tile.copy(stall = stall.copy(heldEnemyId = null))
                    } else {
                        // Move to stall center
                        if (!enemy.isGrabbed || enemy.position.q != coord.q.toFloat() || enemy.position.r != coord.r.toFloat()) {
                            if (updatedEnemies == null) updatedEnemies = state.enemies.toMutableList()
                            updatedEnemies!![enemyIndex] = enemy.copy(
                                isGrabbed = true,
                                position = PreciseAxialCoordinate(coord.q.toFloat(), coord.r.toFloat())
                            )
                        }
                    }
                } else {
                    // Enemy gone?
                    if (updatedHexes == null) updatedHexes = state.hexes.toMutableMap()
                    updatedHexes!![coord] = tile.copy(stall = stall.copy(heldEnemyId = null))
                }
            }
        }

        return if (anyPuddleExpired || anyEffectExpired || updatedHexes != null || updatedEnemies != null) {
            state.copy(
                puddles = if (anyPuddleExpired) state.puddles.filter { currentTimeMs - it.spawnTimeMs < it.durationMs } else state.puddles,
                visualEffects = if (anyEffectExpired) state.visualEffects.filter { currentTimeMs - it.startTimeMs < it.durationMs } else state.visualEffects,
                hexes = updatedHexes ?: state.hexes,
                enemies = updatedEnemies ?: state.enemies
            )
        } else state
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

            var freezeDuration = maxOf(0, enemy.freezeDurationMs - 32)
            var speedBoostDuration = maxOf(0, enemy.speedBoostDurationMs - 32)

            val behaviorUpdatedEnemy = enemyDef.updateSpecialBehavior(enemy, currentTimeMs)
            var isStopped = behaviorUpdatedEnemy.isStopped
            var stopDurationMs = behaviorUpdatedEnemy.stopDurationMs
            var lastStopMs = behaviorUpdatedEnemy.lastStopMs

            var speedMultiplier = 1.0f
            // Permanent Outdoor Puddles
            val currentHex = GridUtils.hexRound(enemy.position.q, enemy.position.r)
            if (state.hexes[currentHex]?.isPermanentlyWet == true) {
                speedMultiplier = enemyDef.getPuddleSlowMultiplier()
            }

            state.puddles.forEach { puddle ->
                if (GridUtils.axialDistance(enemy.position, puddle.position) < 0.8) {
                    speedMultiplier = enemyDef.getPuddleSlowMultiplier()
                    if (puddle.sourceStallCoord != null && puddle.sourceStallId != null) {
                        affectingStalls
                            .getOrPut(puddle.sourceStallCoord to puddle.sourceStallId) { mutableSetOf() }
                            .add(enemy.id)
                    }
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

            if (speedBoostDuration > 0) {
                speedMultiplier *= 1.5f
            }

            val effectiveSpeed = enemy.baseSpeed * speedMultiplier

            val targetIndex = enemy.currentPathIndex + 1
            if (targetIndex >= enemy.path.size) {
                mutableState = mutableState.copy(health = maxOf(0, mutableState.health - 1))
                return@mapNotNull null
            }

            val target = enemy.path[targetIndex]
            val targetPrecise = PreciseAxialCoordinate(target.q.toFloat(), target.r.toFloat())
            val dq = targetPrecise.q - enemy.position.q
            val dr = targetPrecise.r - enemy.position.r
            val dist = GridUtils.axialDistance(enemy.position, targetPrecise)

            val newIsFacingLeft = if (targetPrecise.q + targetPrecise.r / 2f != enemy.position.q + enemy.position.r / 2f) {
                targetPrecise.q + targetPrecise.r / 2f < enemy.position.q + enemy.position.r / 2f
            } else {
                enemy.isFacingLeft
            }

            var nextEnemy = if (dist < effectiveSpeed) {
                enemy.copy(
                    position = targetPrecise,
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
                        .minByOrNull { GridUtils.axialDistance(nextEnemy.position, it.position) }

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

        return Pair(mutableState, finalEnemies)
    }

    /**
     * Handles the firing logic for all stalls on the board.
     * Efficiently filters for active stalls and determines targets based on their targeting mode.
     *
     * @param state Current game state.
     * @param currentTimeMs Current game time in milliseconds.
     * @return Updated game state with new projectiles, puddles, and stall states.
     */
    private fun handleStallFiring(state: GameState, currentTimeMs: Long): GameState {
        val firingStalls = state.hexes.entries.filter { (_, tile) ->
            val stall = tile.stall
            if (stall == null || stall.fireRateMs <= 0 || stall.disabledWaves > 0 || stall.heldEnemyId != null) return@filter false

            val boostResult = calculateStatBoost(tile.coordinate, state.hexes, state.bktBuffType)
            val effectiveFireRate = if (state.bktBuffType == BktBuffType.HERBAL) {
                (stall.fireRateMs / boostResult.rateMultiplier).toLong()
            } else stall.fireRateMs

            (currentTimeMs - stall.lastFiredMs) >= effectiveFireRate
        }

        if (firingStalls.isEmpty() || state.enemies.isEmpty()) return state

        val newProjectiles = state.projectiles.toMutableList()
        val newPuddles = state.puddles.toMutableList()
        val updatedHexes = state.hexes.toMutableMap()
        val newlyGrabbedEnemyIds = mutableSetOf<String>()

        val obstructions = if (firingStalls.any { it.value.stall?.isBlockable == true }) {
            state.hexes.values.filter { it.type is TileType.Obstruction }.map { it.coordinate }
        } else emptyList()

        val enemiesByMode = mapOf(
            TargetMode.FIRST to state.enemies.sortedByDescending { it.currentPathIndex },
            TargetMode.STRONGEST to state.enemies.sortedByDescending { it.health },
            TargetMode.WEAKEST to state.enemies.sortedBy { it.health }
        )

        firingStalls.forEach { (coord, tile) ->
            val stall = tile.stall!!
            val stallDef = StallRegistry.get(stall.stallType)

            val target = stallDef.behavior.selectTarget(
                stall, coord, enemiesByMode, state.enemies, obstructions, newlyGrabbedEnemyIds
            ) ?: return@forEach

            val boostResult = calculateStatBoost(coord, state.hexes, state.bktBuffType)
            val damageBoost = boostResult.damageMultiplier
            val durationBoost = boostResult.rateMultiplier

            // Update Bak Kut Teh stats for adjacency tracking
            boostResult.providerCoords.forEach { providerCoord ->
                updatedHexes[providerCoord]?.let { tileWithBkt ->
                    tileWithBkt.stall?.let { bktStall ->
                        updatedHexes[providerCoord] = tileWithBkt.copy(
                            stall = bktStall.copy(uniqueTargetIds = bktStall.uniqueTargetIds + stall.id)
                        )
                    }
                }
            }

            val boost = if (state.bktBuffType == BktBuffType.MEATY) damageBoost else 1.0f
            val effectBoost = if (state.bktBuffType == BktBuffType.HERBAL) durationBoost else 1.0f

            val boostedStall = if (boost > 1.0f || effectBoost > 1.0f) {
                stall.copy(
                    damage = stall.damage * boost,
                    effectDurationMs = (stall.effectDurationMs * effectBoost).toLong(),
                    freezeDurationMs = (stall.freezeDurationMs * effectBoost).toLong()
                )
            } else stall

            val fireResult = stallDef.fire(boostedStall, coord, target, currentTimeMs, state.hexes)

            when (fireResult) {
                is FireResult.NewProjectile -> {
                    newProjectiles.add(fireResult.projectile)
                    val updatedStall = (fireResult.updatedStall ?: stall).copy(
                        lastFiredMs = currentTimeMs,
                        damage = stall.damage,
                        effectDurationMs = stall.effectDurationMs,
                        freezeDurationMs = stall.freezeDurationMs
                    )
                    updatedHexes[coord] = tile.copy(stall = updatedStall)
                }
                is FireResult.NewPuddle -> {
                    newPuddles.add(fireResult.puddle)
                    updatedHexes[coord] = tile.copy(stall = stall.copy(lastFiredMs = currentTimeMs))
                }
                is FireResult.HoldEnemy -> {
                    newlyGrabbedEnemyIds.add(fireResult.targetId)
                    updatedHexes[coord] = tile.copy(
                        stall = stall.copy(
                            lastFiredMs = currentTimeMs,
                            heldEnemyId = fireResult.targetId,
                            releaseTimeMs = fireResult.releaseTimeMs,
                            uniqueTargetIds = stall.uniqueTargetIds + fireResult.targetId
                        )
                    )
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
    private fun handleProjectiles(state: GameState, currentTimeMs: Long): Pair<GameState, Boolean> {
        val finalProjectiles = mutableListOf<Projectile>()
        val hitEnemiesDetails = mutableMapOf<String, MutableList<Projectile>>()
        val newVisualEffects = state.visualEffects.toMutableList()
        val enemyLookup = state.enemies.associateBy { it.id }

        state.projectiles.forEach { proj ->
            val targetPos = if (proj.targetEnemyId != null) {
                enemyLookup[proj.targetEnemyId]?.let { if (!it.isGrabbed) it.position else null }
                    ?: proj.targetPosition
            } else {
                proj.targetPosition
            }

            val dq = targetPos.q - proj.position.q
            val dr = targetPos.r - proj.position.r
            val dist = GridUtils.axialDistance(proj.position, targetPos)

            if (dist < proj.speed) {
                // Visual Effect for AoE
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

                // Collect hits - Direct target first
                if (proj.targetEnemyId != null && enemyLookup.containsKey(proj.targetEnemyId) && !enemyLookup[proj.targetEnemyId]!!.isGrabbed) {
                    hitEnemiesDetails.getOrPut(proj.targetEnemyId) { mutableListOf() }.add(proj)
                }

                // Collect AoE hits
                if (proj.aoeRadius > 0) {
                    state.enemies.forEach { enemy ->
                        if (enemy.isGrabbed || enemy.id == proj.targetEnemyId) return@forEach
                        if (GridUtils.axialDistance(enemy.position, targetPos) <= proj.aoeRadius) {
                            hitEnemiesDetails.getOrPut(enemy.id) { mutableListOf() }.add(proj)
                        }
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

        if (hitEnemiesDetails.isEmpty()) {
            return state.copy(projectiles = finalProjectiles, visualEffects = newVisualEffects) to false
        }

        var updatedGold = state.gold
        var updatedScore = state.score
        var shouldTriggerHaptic = false
        val updatedHexes = state.hexes.toMutableMap()

        val finalEnemies = state.enemies.mapNotNull { enemy ->
            val hits = hitEnemiesDetails[enemy.id] ?: return@mapNotNull enemy

            var currentHealth = enemy.health
            var maxFreezeDuration = enemy.freezeDurationMs
            var speedBoostDuration = enemy.speedBoostDurationMs

            hits.forEach { proj ->
                if (currentHealth < 1.0f) return@forEach

                var damage = proj.damage
                enemy.buffs.forEach { if (it.type == BuffType.ARMOR) damage *= (1.0f - it.value) }

                var freezeDuration = proj.freezeDurationMs
                if (proj.sourceStallType != null) {
                    val stallDef = StallRegistry.get(proj.sourceStallType)
                    damage = stallDef.applyDamageModifiers(enemy, damage)
                    freezeDuration = stallDef.getFreezeModifier(enemy, freezeDuration)
                    stallDef.getSpeedBoost(enemy).let { if (it > 0) speedBoostDuration = it }
                }

                currentHealth = maxOf(0f, currentHealth - damage)
                maxFreezeDuration = maxOf(maxFreezeDuration, freezeDuration)

                // Track hit and kill on the source stall
                if (proj.sourceStallCoord != null && proj.sourceStallId != null) {
                    val coord = proj.sourceStallCoord
                    updatedHexes[coord]?.stall?.let { stall ->
                        if (stall.id == proj.sourceStallId) {
                            val isKill = currentHealth < 1.0f
                            val newKills = if (isKill && !stall.stallType.isUtility) stall.kills + 1 else stall.kills
                            updatedHexes[coord] = updatedHexes[coord]!!.copy(
                                stall = stall.copy(
                                    uniqueTargetIds = stall.uniqueTargetIds + enemy.id,
                                    kills = newKills
                                )
                            )
                        }
                    }
                }
            }

            if (currentHealth < 1.0f) { // Consider dead if health rounds to 0
                updatedGold += enemy.reward
                updatedScore += enemy.reward
                shouldTriggerHaptic = true
                null // Enemy is dead
            } else {
                enemy.copy(
                    health = currentHealth,
                    freezeDurationMs = maxFreezeDuration,
                    speedBoostDurationMs = speedBoostDuration
                )
            }
        }

        val survivingEnemyLookup = finalEnemies.associateBy { it.id }
        val fullyUpdatedEnemies = finalEnemies.map { e ->
            // Clean up buffs: source must be alive, still targeting this enemy, and not grabbed
            val validBuffs = e.buffs.filter { buff ->
                val source = survivingEnemyLookup[buff.sourceId]
                source != null && source.buffingTargetId == e.id && !source.isGrabbed
            }
            if (validBuffs.size != e.buffs.size) {
                e.copy(buffs = validBuffs)
            } else e
        }

        return state.copy(
            hexes = updatedHexes,
            enemies = fullyUpdatedEnemies,
            projectiles = finalProjectiles,
            visualEffects = newVisualEffects,
            gold = updatedGold,
            score = updatedScore,
            goldEarnedThisWave = state.goldEarnedThisWave + (updatedGold - state.gold)
        ) to shouldTriggerHaptic
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

    fun onCellClick(coord: AxialCoordinate) {
        val currentState = _gameState.value
        val tile = currentState.hexes[coord] ?: return

        if (currentState.isRemovePillarModeActive) {
            if (tile.type == TileType.PILLAR) {
                removePillar(coord)
            }
            return
        }

        if (currentState.isOutdoorPuddleModeActive) {
            val chain = getOutdoorPuddleChain(coord)
            if (chain.size == 4) {
                applyOutdoorPuddles(chain)
            }
            return
        }

        if (tile.stall != null) {
            // Select existing stall
            _gameState.update { it.copy(selectedBoardStall = coord, selectedStallType = null, lastSoldStall = null) }
        } else if (currentState.selectedStallType != null) {
            // Place new stall
            val stallToPlace = currentState.selectedStallType
            _gameState.update { it.copy(lastSoldStall = null) }
            if (currentState.gold >= stallToPlace.cost && (tile.type == TileType.FLOOR || tile.type == TileType.DRAIN)) {
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
                    val uncleNeighbors = GridUtils.getNeighbors(uncleCoord)
                    val freeUncleNeighbors = uncleNeighbors.filter {
                        val neighborTile = currentState.hexes[it] ?: return@filter false
                        val isWalkableFloor = (neighborTile.type == TileType.FLOOR || neighborTile.type == TileType.DRAIN) && !blocked.contains(it)
                        it != coord && (isWalkableFloor ||
                                neighborTile.type == TileType.START ||
                                neighborTile.type == TileType.GOAL_TABLE)
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
                        newHexes[coord] = tile.copy(
                            stall = stallToPlace.copy(id = UUID.randomUUID().toString()),
                            isPermanentlyWet = false
                        )

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
                selectedBoardStall = null,
                lastSoldStall = coord to stall.copy(heldEnemyId = null, releaseTimeMs = 0L)
            )
        }
    }

    fun undoSell() {
        val currentState = _gameState.value
        val (coord, stall) = currentState.lastSoldStall ?: return
        val tile = currentState.hexes[coord] ?: return

        if (tile.stall != null) {
             _gameState.update { it.copy(lastSoldStall = null) }
             return
        }

        val refund = (stall.totalInvestment * 0.5f).toInt()
        if (currentState.gold < refund) return

        val newHexes = currentState.hexes.toMutableMap()
        newHexes[coord] = tile.copy(stall = stall)

        val blocked = getBlockedCoordinates(newHexes)

        _gameState.update { state ->
            if (state.gold < refund) return@update state
            val updatedEnemies = recalculateEnemyPaths(state, blocked, newHexes)
            state.copy(
                hexes = newHexes,
                gold = state.gold - refund,
                enemies = updatedEnemies,
                lastSoldStall = null
            )
        }
    }

    fun upgradeStall() {
        if (_gameState.value.waveActive) {
            applyUpgrade(isSpecific = false)
        } else {
            _gameState.update { it.copy(showUpgradeOverlay = true, lastSoldStall = null) }
        }
    }

    fun dismissUpgradeOverlay() {
        _gameState.update { it.copy(showUpgradeOverlay = false) }
    }

    fun openStarActionOverlay() {
        if (!_gameState.value.waveActive && _gameState.value.kitchelinStars > 0) {
            _gameState.update { it.copy(showStarActionOverlay = true, lastSoldStall = null) }
        }
    }

    fun dismissStarActionOverlay() {
        _gameState.update { it.copy(showStarActionOverlay = false) }
    }

    fun enterRemovePillarMode() {
        _gameState.update { it.copy(isRemovePillarModeActive = true, showStarActionOverlay = false) }
    }

    fun exitRemovePillarMode() {
        _gameState.update { it.copy(isRemovePillarModeActive = false) }
    }

    fun enterOutdoorPuddleMode() {
        _gameState.update { it.copy(isOutdoorPuddleModeActive = true, showStarActionOverlay = false) }
    }

    fun exitOutdoorPuddleMode() {
        _gameState.update { it.copy(isOutdoorPuddleModeActive = false) }
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

    fun restoreHealth() {
        _gameState.update {
            if (it.kitchelinStars > 0 && it.health < 10) {
                it.copy(
                    kitchelinStars = it.kitchelinStars - 1,
                    health = it.health + 1,
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

    fun getOutdoorPuddleChain(startCoord: AxialCoordinate): List<AxialCoordinate> {
        val hexes = _gameState.value.hexes
        val tile = hexes[startCoord] ?: return emptyList()

        if (tile.type != TileType.FLOOR || tile.stall != null || tile.isPermanentlyWet) return emptyList()

        // Perimeter check for start tile
        if (GridUtils.getNeighbors(startCoord).none { n ->
                val nTile = hexes[n]
                nTile == null || nTile.type.name.startsWith("EDGE_")
            }) return emptyList()

        fun isPerimeter(coord: AxialCoordinate) = GridUtils.getNeighbors(coord).any { n ->
            val nTile = hexes[n]
            nTile == null || nTile.type.name.startsWith("EDGE_")
        }

        fun isValid(coord: AxialCoordinate, currentChain: List<AxialCoordinate>): Boolean {
            val t = hexes[coord]
            return t != null && t.type == TileType.FLOOR && t.stall == null &&
                    !t.isPermanentlyWet && coord !in currentChain && isPerimeter(coord)
        }

        fun findChain(current: AxialCoordinate, currentChain: List<AxialCoordinate>): List<AxialCoordinate>? {
            if (currentChain.size == 4) return currentChain

            for (neighbor in GridUtils.getNeighbors(current)) {
                if (isValid(neighbor, currentChain)) {
                    val result = findChain(neighbor, currentChain + neighbor)
                    if (result != null) return result
                }
            }
            return null
        }

        return findChain(startCoord, listOf(startCoord)) ?: emptyList()
    }

    private fun applyOutdoorPuddles(chain: List<AxialCoordinate>) {
        var success = false
        _gameState.update { state ->
            if (state.kitchelinStars < 2) return@update state

            val newHexes = state.hexes.toMutableMap()
            chain.forEach { coord ->
                newHexes[coord] = newHexes[coord]!!.copy(isPermanentlyWet = true)
            }

            success = true
            state.copy(
                kitchelinStars = state.kitchelinStars - 2,
                hexes = newHexes,
                isOutdoorPuddleModeActive = false
            )
        }
        if (success) {
            triggerHaptic()
        }
    }

    private fun removePillar(coord: AxialCoordinate) {
        val currentTime = System.currentTimeMillis()
        var success = false
        _gameState.update { state ->
            if (state.kitchelinStars > 0 && state.hexes[coord]?.type == TileType.PILLAR) {
                val newHexes = state.hexes.toMutableMap()
                newHexes[coord] = state.hexes[coord]!!.copy(type = TileType.FLOOR)

                val blocked = getBlockedCoordinates(newHexes)
                val updatedEnemies = recalculateEnemyPaths(state.copy(hexes = newHexes), blocked, newHexes)

                success = true

                state.copy(
                    kitchelinStars = state.kitchelinStars - 1,
                    hexes = newHexes,
                    enemies = updatedEnemies,
                    isRemovePillarModeActive = false,
                    lastShakeTimeMs = currentTime
                )
            } else state
        }
        if (success) {
            triggerHaptic()
        }
    }

    private fun applyUpgrade(isSpecific: Boolean, specificStat: String? = null) {
        _gameState.update { state ->
            val coord = state.selectedBoardStall ?: return@update state
            val tile = state.hexes[coord] ?: return@update state
            val stall = tile.stall ?: return@update state

            val hasFreeUpgrade = state.freeSpecificUpgrades > 0
            val finalUpgradeCost = StallUpgradeManager.calculateUpgradeCost(stall, isSpecific, hasFreeUpgrade)

            if (state.gold >= finalUpgradeCost) {
                val availableStats = StallUpgradeManager.getAvailableUpgradeStats(stall)
                if (availableStats.isEmpty()) return@update state
                val statToUpgrade = if (isSpecific && specificStat != null) {
                    if (!availableStats.contains(specificStat)) return@update state
                    specificStat
                } else {
                    availableStats.random(this@MainViewModel.random)
                }

                val updatedStall = StallUpgradeManager.applyUpgrade(stall, statToUpgrade, finalUpgradeCost, isSpecific)

                val newHexes = state.hexes.toMutableMap()
                newHexes[coord] = tile.copy(stall = updatedStall)

                return@update state.copy(
                    hexes = newHexes,
                    gold = state.gold - finalUpgradeCost,
                    freeSpecificUpgrades = if (isSpecific && hasFreeUpgrade) state.freeSpecificUpgrades - 1 else state.freeSpecificUpgrades
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
        _gameState.update { it.copy(hexes = newHexes, lastSoldStall = null) }
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
            it.stall != null || it.type is TileType.Obstruction || it.type == TileType.GOAL_TABLE || it.type.name.startsWith("EDGE_")
        }.map { it.coordinate }.toSet()
    }



    private fun releaseEnemy(
        enemy: Enemy,
        stallCoord: AxialCoordinate,
        hexes: Map<AxialCoordinate, HexTile>,
        endPos: AxialCoordinate?
    ): Enemy {
        val adjacentCoords = GridUtils.getNeighbors(stallCoord)

        val blocked = getBlockedCoordinates(hexes)
        val validTiles = adjacentCoords.filter { adj ->
            val tile = hexes[adj] ?: return@filter false
            val isStandardWalkable = !blocked.contains(adj) && tile.type !is TileType.Obstruction && !tile.type.name.startsWith("EDGE_")
            isStandardWalkable || tile.type == TileType.START || tile.type == TileType.GOAL_TABLE
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

    data class BoostResult(
        val damageMultiplier: Float,
        val rateMultiplier: Float,
        val providerCoords: List<AxialCoordinate>
    )

    fun calculateStatBoost(coord: AxialCoordinate, hexes: Map<AxialCoordinate, HexTile>, buffType: BktBuffType): BoostResult {
        val adjacentCoords = GridUtils.getNeighbors(coord)
        var totalBoostPercent = 0f
        val providers = mutableListOf<AxialCoordinate>()
        adjacentCoords.forEach { adj ->
            val stall = hexes[adj]?.stall
            if (stall != null && stall.stallType == StallType.BAK_KUT_TEH && stall.disabledWaves == 0) {
                totalBoostPercent += stall.damage // Bak Kut Teh damage field stores its current boost %
                providers.add(adj)
            }
        }
        val multiplier = 1.0f + (totalBoostPercent / 100f)
        return if (buffType == BktBuffType.MEATY) {
            BoostResult(multiplier, 1.0f, providers)
        } else {
            BoostResult(1.0f, multiplier, providers)
        }
    }
}
