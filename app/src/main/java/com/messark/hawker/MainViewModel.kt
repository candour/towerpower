package com.messark.hawker

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messark.hawker.model.*
import com.messark.hawker.registry.*
import com.messark.hawker.utils.*
import com.messark.hawker.utils.SpatialIndex
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
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

    private var stallBoosts = emptyMap<AxialCoordinate, BoostResult>()

    private fun updateBoostCache(state: GameState) {
        stallBoosts = state.hexes.entries
            .filter { it.value.stall != null }
            .associate { (coord, _) ->
                coord to calculateStatBoost(coord, state.hexes, state.bktBuffType)
            }
    }

    private val earlyWaveConfigs = mapOf(
        1 to listOf(EnemyType.SALARYMAN to 5),
        2 to listOf(EnemyType.SALARYMAN to 6),
        3 to listOf(EnemyType.SALARYMAN to 5, EnemyType.TOURIST to 1),
        4 to listOf(EnemyType.SALARYMAN to 6, EnemyType.TOURIST to 1),
        5 to listOf(EnemyType.SALARYMAN to 5, EnemyType.TOURIST to 2),
        6 to listOf(EnemyType.SALARYMAN to 4, EnemyType.TOURIST to 2, EnemyType.AUNTIE to 1)
    )

    private val levelConfigs = listOf(
        8 to 14,
        8 to 12,
        8 to 10,
        8 to 8,
        6 to 8
    )

    private fun getLevelDimensions(level: Int): Pair<Int, Int> {
        val index = (level - 1).coerceIn(0, levelConfigs.size - 1)
        return levelConfigs[index]
    }

    private val _availableStalls = MutableStateFlow(
        StallRegistry.all().map { it.toStall() }
    )
    val availableStalls: StateFlow<List<Stall>> = _availableStalls.asStateFlow()

    internal var gameJob: Job? = null
    private var lastHapticTimeMs = 0L

    private val _hapticEvents = MutableSharedFlow<Unit>()
    val hapticEvents: SharedFlow<Unit> = _hapticEvents.asSharedFlow()

    private val saveChannel = Channel<GameState>(Channel.CONFLATED)

    init {
        initializeGame()
        startGameLoop()
        startSaveProcessor()
    }

    private fun startSaveProcessor() {
        viewModelScope.launch(Dispatchers.IO) {
            for (state in saveChannel) {
                saveGameInternal(state)
            }
        }
    }

    private fun saveGameInternal(state: GameState) {
        gameStateRepository.saveGameState(state)
    }

    private fun initializeGame() {
        val dimensions = getLevelDimensions(1)
        val (hexes, startPos, endPos) = MapGenerator.generateRandomVerticalMap(width = dimensions.first, height = dimensions.second, random = random)

        val updatedState = _gameState.updateAndGet {
            it.copy(
                hexes = hexes,
                startPosition = startPos,
                endPosition = endPos,
                gold = 500, // Start with some gold to place stalls
                currentScreen = AppScreen.LOADING,
                currentLevel = 1,
                pathDistances = Pathfinding.calculateAllDistances(endPos, getBlockedCoordinates(hexes), hexes.keys)
            )
        }
        updateBoostCache(updatedState)
    }

    fun navigateTo(screen: AppScreen) {
        val prevState = _gameState.value
        val updatedState = _gameState.updateAndGet { it.copy(currentScreen = screen) }
        if (screen == AppScreen.MAIN_MENU && prevState.currentScreen == AppScreen.GAME) {
            saveGame(updatedState)
        }
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
        if (current.currentScreen == AppScreen.GAME) {
            val updatedState = _gameState.updateAndGet { state ->
                state.copy(
                    gold = state.gold + 5000,
                    kitchelinStars = state.kitchelinStars + 1
                )
            }
            saveGame(updatedState)
        } else {
            val base = gameStateRepository.loadGameState() ?: return
            val cheated = base.copy(
                gold = base.gold + 5000,
                kitchelinStars = base.kitchelinStars + 1
            )
            saveGame(cheated)
        }
    }

    fun resumeGame() {
        val savedState = gameStateRepository.loadGameState()
        if (savedState != null) {
            val endPos = savedState.endPosition
            val finalState = if (endPos != null) {
                savedState.copy(pathDistances = Pathfinding.calculateAllDistances(endPos, getBlockedCoordinates(savedState.hexes), savedState.hexes.keys))
            } else savedState
            updateBoostCache(finalState)
            _gameState.value = finalState
        }
    }

    fun resetGame() {
        gameStateRepository.deleteGameState()
        val dimensions = getLevelDimensions(1)
        val (hexes, startPos, endPos) = MapGenerator.generateRandomVerticalMap(width = dimensions.first, height = dimensions.second, random = random)

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

            val newState = GameState(
                currentScreen = AppScreen.GAME,
                hexes = hexes,
                startPosition = startPos,
                endPosition = endPos,
                gold = 500,
                score = 0,
                activeTutorial = tutorialToShow,
                currentLevel = 1,
                health = 10,
                kitchelinStars = 0,
                currentWave = 0,
                gameSpeed = 1.0f,
                simulationTimeMs = 0L,
                pathDistances = Pathfinding.calculateAllDistances(endPos, getBlockedCoordinates(hexes), hexes.keys)
            )
            _gameState.value = newState
            updateBoostCache(newState)
            saveGame(newState)
        }
    }

    fun selectStall(stall: Stall) {
        _gameState.update { it.copy(selectedStallType = stall, lastSoldStall = null) }
    }

    fun saveGame(state: GameState = _gameState.value) {
        saveChannel.trySend(state)
    }

    fun increaseGameSpeed() {
        _gameState.update { it.copy(gameSpeed = (it.gameSpeed + 0.5f).coerceAtMost(3.0f)) }
    }

    fun decreaseGameSpeed() {
        _gameState.update { it.copy(gameSpeed = (it.gameSpeed - 0.5f).coerceAtLeast(0.5f)) }
    }

    fun startWave() {
        val currentState = _gameState.value
        if (currentState.waveActive || currentState.activeTutorial != null) return

        val hasBkt = currentState.hexes.values.any { it.stall?.stallType == StallType.BAK_KUT_TEH && it.stall.disabledWaves == 0 }
        val (buffType, toast) = if (hasBkt) {
            if (random.nextBoolean()) {
                BktBuffType.MEATY to "Meaty!"
            } else {
                BktBuffType.HERBAL to "Herbal!"
            }
        } else {
            currentState.bktBuffType to null
        }

        val updatedState = _gameState.updateAndGet {
            it.copy(
                goldEarnedThisWave = 0,
                showBonusMessage = false,
                lastSoldStall = null,
                bktBuffType = buffType,
                bktToastMessage = toast
            )
        }
        updateBoostCache(updatedState)

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

        val updatedState = _gameState.updateAndGet {
            val currentTime = it.simulationTimeMs
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
        saveGame(updatedState)
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
                budget *= 1.14 // Boss wave budget jump (1.07 * 1.07)
            } else if ((i - 1) % 10 == 0) {
                budget *= 1.0 // Plateau after boss wave
            } else {
                // Important to keep in sync so the level doesn't go on forever
                budget *= 1.07 // 0.01 more than the customers increase by
            }
        }

        val enemyList = mutableListOf<EnemyType>()
        var remainingBudget = budget

        val maxTierIndex = minOf((wave - 1) / 2, enemyTiers.size - 1)
        var allowedTiers = enemyTiers.subList(0, maxTierIndex + 1)

        // Only allow Delivery Riders in boss waves until level 20
        if (wave <= 21 && wave % 10 != 0) {
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

    private class EngineUpdateBatch {
        var healthLoss: Int = 0
        val updatedHexes = mutableMapOf<AxialCoordinate, HexTile>()
    }

    /**
     * Updates game state by advancing spawning, movement, and combat.
     *
     * @param realTimeMs Current real time in milliseconds.
     */
    internal fun updateGame(realTimeMs: Long) {
        var starAwardedOutside = false
        var bonusAwardedOutside = 0
        var hapticRequested = false
        var gameOverState: GameState? = null

        var shouldSave = false
        val finalUpdatedState = _gameState.updateAndGet { state ->
            if (state.currentScreen != AppScreen.GAME || state.activeTutorial != null) return@updateAndGet state

            val delta = (32 * state.gameSpeed).toLong()
            val currentTimeMs = state.simulationTimeMs + delta
            val batch = EngineUpdateBatch()

            // 0. Expired Transients
            val puddles = if (state.puddles.any { currentTimeMs - it.spawnTimeMs >= it.durationMs }) {
                state.puddles.filter { currentTimeMs - it.spawnTimeMs < it.durationMs }
            } else state.puddles

            val effects = if (state.visualEffects.any { currentTimeMs - it.startTimeMs >= it.durationMs }) {
                state.visualEffects.filter { currentTimeMs - it.startTimeMs < it.durationMs }
            } else state.visualEffects

            var newState = state.copy(puddles = puddles, visualEffects = effects, simulationTimeMs = currentTimeMs)

            // 1. Spawning
            newState = handleSpawning(newState, currentTimeMs)

            // 2. Enemy Pipeline (Consolidated Movement and Transients)
            val puddleSpatialIndex = SpatialIndex(newState.puddles) { it.position }
            val updatedEnemies = handleEnemyPipeline(newState, currentTimeMs, puddleSpatialIndex, batch, delta)

            // Apply batch updates from pipeline
            val hexesAfterPipeline = if (batch.updatedHexes.isNotEmpty()) {
                newState.hexes + batch.updatedHexes
            } else newState.hexes

            newState = newState.copy(
                enemies = updatedEnemies,
                health = maxOf(0, newState.health - batch.healthLoss),
                hexes = hexesAfterPipeline
            )

            // 3. Prepare Engine Data (Spatial Index)
            val enemySpatialIndex = SpatialIndex(newState.enemies) { it.position }

            // 4. Stall Firing
            newState = handleStallFiring(newState, currentTimeMs, enemySpatialIndex)

            // 5. Projectile Movement and Collision
            val (projectilesState, hitHaptic) = handleProjectiles(newState, currentTimeMs, enemySpatialIndex, delta)
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
                            atmGold += stallDef.passiveIncome

                            // calculateStatBoost uses the updated hexes, so re-enabled BKTs are counted
                            val boostResult = calculateStatBoost(coord, updatedHexes, newState.bktBuffType)
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

                val isGraduating = newState.currentWave == 50

                newState = newState.copy(
                    waveActive = false,
                    isBossWave = false,
                    kitchelinStars = nextStars,
                    hexes = updatedHexes,
                    gold = newState.gold + bonusBudget + atmGold,
                    lastWaveBonusGold = bonusBudget,
                    showBonusMessage = bonusBudget > 0,
                    activeBudgetBonuses = 0,
                    visualEffects = newState.visualEffects + atmEffects,
                    showGraduationOverlay = isGraduating
                )
                updateBoostCache(newState)
                shouldSave = true
            }

            // 6. Game over check
            if (newState.health <= 0 && state.health > 0) {
                gameOverState = newState
            }

            newState
        }

        if (shouldSave) {
            saveGame(finalUpdatedState)
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

    private fun handleSpawning(state: GameState, currentTimeMs: Long): GameState {
        val spawnInterval = 1000L
        if (state.waveActive && state.enemiesToSpawn > 0 && currentTimeMs - state.lastSpawnTimeMs >= spawnInterval && state.hexes.isNotEmpty() && state.enemiesToSpawnList.isNotEmpty()) {
            val type = state.enemiesToSpawnList.first()

            // One Tiger Mom on board limit
            if (type == EnemyType.TIGER_MOM && state.enemies.any { it.type == EnemyType.TIGER_MOM }) {
                // Delay spawning by resetting lastSpawnTimeMs to try again next tick
                return state.copy(lastSpawnTimeMs = currentTimeMs - (spawnInterval / 2))
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
     * Optimized consolidated pipeline for processing all enemy logic:
     * - Status updates (freeze, speed boost)
     * - Grab/Hold mechanics (Tray Return Uncle)
     * - Special behaviors (Tourist stalling, Tiger Mom buffing)
     * - Movement and pathing
     * - AoE/Puddle effects
     *
     * @return List of updated enemies.
     */
    private fun handleEnemyPipeline(
        state: GameState,
        currentTimeMs: Long,
        puddleSpatialIndex: SpatialIndex<StickyPuddle>,
        batch: EngineUpdateBatch,
        delta: Long
    ): List<Enemy> {
        val stallCoordMap = if (state.hexes.values.any { it.stall?.heldEnemyId != null }) {
            state.hexes.values.filter { it.stall?.heldEnemyId != null }
                .associate { it.stall!!.heldEnemyId!! to it.coordinate }
        } else emptyMap()

        val buffActions = mutableListOf<Pair<String, String>>() // TigerMomId, TargetEnemyId

        val initialUpdatedEnemies = state.enemies.mapNotNull { enemy ->
            if (enemy.isDead) return@mapNotNull null

            var currentEnemy = enemy

            // 1. Handle Held Enemies (Transients)
            val stallCoord = stallCoordMap[currentEnemy.id]
            if (stallCoord != null) {
                val tile = state.hexes[stallCoord]!!
                val stall = tile.stall!!

                if (currentTimeMs >= stall.releaseTimeMs) {
                    // Release enemy logic
                    currentEnemy = releaseEnemy(currentEnemy, stallCoord, state.hexes, state.endPosition)
                    batch.updatedHexes[stallCoord] = tile.copy(stall = stall.copy(heldEnemyId = null))
                    // Continue to move in the same frame
                } else {
                    // Maintain hold
                    return@mapNotNull if (!currentEnemy.isGrabbed || currentEnemy.position.q != stallCoord.q.toFloat() || currentEnemy.position.r != stallCoord.r.toFloat()) {
                        currentEnemy.copy(isGrabbed = true, position = PreciseAxialCoordinate(stallCoord.q.toFloat(), stallCoord.r.toFloat()))
                    } else currentEnemy
                }
            }

            // 2. Process Active Enemies
            val enemyDef = EnemyRegistry.get(currentEnemy.type)
            var freezeDuration = maxOf(0, currentEnemy.freezeDurationMs - delta)
            var speedBoostDuration = maxOf(0, currentEnemy.speedBoostDurationMs - delta)

            val behaviorUpdatedEnemy = enemyDef.updateSpecialBehavior(currentEnemy, currentTimeMs, delta)
            val isStopped = behaviorUpdatedEnemy.isStopped
            val stopDurationMs = behaviorUpdatedEnemy.stopDurationMs
            val lastStopMs = behaviorUpdatedEnemy.lastStopMs

            if (isStopped || freezeDuration > 0) {
                return@mapNotNull currentEnemy.copy(
                    isStopped = isStopped, stopDurationMs = stopDurationMs, lastStopMs = lastStopMs,
                    freezeDurationMs = freezeDuration, speedBoostDurationMs = speedBoostDuration
                )
            }

            // 3. Movement and Puddles
            var speedMultiplier = 1.0f
            val currentHex = GridUtils.hexRound(currentEnemy.position.q, currentEnemy.position.r)
            if (state.hexes[currentHex]?.isPermanentlyWet == true) {
                speedMultiplier = enemyDef.getPuddleSlowMultiplier()
            }

            val nearbyPuddles = puddleSpatialIndex.findNearby(currentEnemy.position, 0.8f)
            if (nearbyPuddles.isNotEmpty()) {
                speedMultiplier = enemyDef.getPuddleSlowMultiplier()
                nearbyPuddles.forEach { puddle ->
                    if (puddle.sourceStallCoord != null && puddle.sourceStallId != null) {
                        val tile = state.hexes[puddle.sourceStallCoord]
                        if (tile?.stall?.id == puddle.sourceStallId) {
                            val currentUpdated = batch.updatedHexes[puddle.sourceStallCoord] ?: tile
                            batch.updatedHexes[puddle.sourceStallCoord] = currentUpdated.copy(
                                stall = currentUpdated.stall!!.copy(uniqueTargetIds = currentUpdated.stall.uniqueTargetIds + currentEnemy.id)
                            )
                        }
                    }
                }
            }

            if (speedBoostDuration > 0) speedMultiplier *= 1.5f
            val effectiveSpeed = currentEnemy.baseSpeed * speedMultiplier

            val targetIndex = currentEnemy.currentPathIndex + 1
            if (targetIndex >= currentEnemy.path.size) {
                batch.healthLoss++
                return@mapNotNull null
            }

            val target = currentEnemy.path[targetIndex]
            val targetPrecise = PreciseAxialCoordinate(target.q.toFloat(), target.r.toFloat())
            val dq = targetPrecise.q - currentEnemy.position.q
            val dr = targetPrecise.r - currentEnemy.position.r
            val dist = GridUtils.axialDistance(currentEnemy.position, targetPrecise)

            val newIsFacingLeft = if (targetPrecise.q + targetPrecise.r / 2f != currentEnemy.position.q + currentEnemy.position.r / 2f) {
                targetPrecise.q + targetPrecise.r / 2f < currentEnemy.position.q + currentEnemy.position.r / 2f
            } else currentEnemy.isFacingLeft

            var nextEnemy = if (dist < effectiveSpeed * state.gameSpeed) {
                currentEnemy.copy(
                    position = targetPrecise, currentPathIndex = targetIndex, currentSpeed = effectiveSpeed,
                    isStopped = isStopped, stopDurationMs = stopDurationMs, lastStopMs = lastStopMs,
                    freezeDurationMs = freezeDuration, speedBoostDurationMs = speedBoostDuration,
                    animationTimeMs = currentEnemy.animationTimeMs + delta, isFacingLeft = newIsFacingLeft
                )
            } else {
                currentEnemy.copy(
                    position = PreciseAxialCoordinate(currentEnemy.position.q + (dq / dist) * effectiveSpeed * state.gameSpeed, currentEnemy.position.r + (dr / dist) * effectiveSpeed * state.gameSpeed),
                    currentSpeed = effectiveSpeed, isStopped = isStopped, stopDurationMs = stopDurationMs, lastStopMs = lastStopMs,
                    freezeDurationMs = freezeDuration, speedBoostDurationMs = speedBoostDuration,
                    animationTimeMs = currentEnemy.animationTimeMs + delta, isFacingLeft = newIsFacingLeft
                )
            }

            // 4. Tiger Mom Activation
            if (nextEnemy.type == EnemyType.TIGER_MOM && !nextEnemy.hasActivatedBuff && nextEnemy.currentPathIndex > currentEnemy.currentPathIndex) {
                if (random.nextFloat() < 0.125f) {
                    val targetEnemy = state.enemies
                        .filter { it.id != nextEnemy.id && it.buffs.none { b -> b.type == BuffType.ARMOR } && !it.isDead && !it.isGrabbed }
                        .minByOrNull { GridUtils.axialDistance(nextEnemy.position, it.position) }

                    if (targetEnemy != null) {
                        buffActions.add(nextEnemy.id to targetEnemy.id)
                        nextEnemy = nextEnemy.copy(isStopped = true, stopDurationMs = 999999L, hasActivatedBuff = true, buffingTargetId = targetEnemy.id)
                    }
                }
            }

            nextEnemy
        }

        // Apply new buffs from this tick
        val enemiesWithNewBuffs = initialUpdatedEnemies.map { e ->
            buffActions.find { it.second == e.id }?.let {
                e.copy(buffs = e.buffs + Buff(BuffType.ARMOR, it.first, 0.9f))
            } ?: e
        }

        // Final comprehensive cleanup pass
        return cleanupEnemyBuffs(enemiesWithNewBuffs)
    }

    /**
     * Handles the firing logic for all stalls on the board.
     * Efficiently filters for active stalls and determines targets based on their targeting mode.
     *
     * @param state Current game state.
     * @param currentTimeMs Current game time in milliseconds.
     * @return Updated game state with new projectiles, puddles, and stall states.
     */
    private fun handleStallFiring(
        state: GameState,
        currentTimeMs: Long,
        enemySpatialIndex: SpatialIndex<Enemy>
    ): GameState {
        val firingStalls = state.hexes.entries.filter { (coord, tile) ->
            val stall = tile.stall
            if (stall == null || stall.fireRateMs <= 0 || stall.disabledWaves > 0 || stall.heldEnemyId != null) return@filter false

            val boostResult = stallBoosts[coord] ?: return@filter false
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

        val bktUpdates = mutableMapOf<AxialCoordinate, MutableSet<String>>()

        firingStalls.forEach { (coord, tile) ->
            val stall = tile.stall!!
            val stallDef = StallRegistry.get(stall.stallType)

            val target = stallDef.behavior.selectTarget(
                stall, coord, enemySpatialIndex, obstructions, newlyGrabbedEnemyIds, state.pathDistances
            ) ?: return@forEach

            val boostResult = stallBoosts[coord]!!
            val damageBoost = boostResult.damageMultiplier
            val durationBoost = boostResult.rateMultiplier

            // Collect Bak Kut Teh updates for adjacency tracking
            boostResult.providerCoords.forEach { providerCoord ->
                bktUpdates.getOrPut(providerCoord) { mutableSetOf() }.add(stall.id)
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

        // Apply collected Bak Kut Teh updates in a batch
        bktUpdates.forEach { (providerCoord, consumerIds) ->
            updatedHexes[providerCoord]?.let { tileWithBkt ->
                tileWithBkt.stall?.let { bktStall ->
                    val newTargets = bktStall.uniqueTargetIds + consumerIds
                    if (newTargets.size > bktStall.uniqueTargetIds.size) {
                        updatedHexes[providerCoord] = tileWithBkt.copy(
                            stall = bktStall.copy(uniqueTargetIds = newTargets)
                        )
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
    private fun handleProjectiles(
        state: GameState,
        currentTimeMs: Long,
        enemySpatialIndex: SpatialIndex<Enemy>,
        delta: Long
    ): Pair<GameState, Boolean> {
        val finalProjectiles = mutableListOf<Projectile>()
        // mapOf(EnemyId to listOf(Pair(Projectile, DistanceFromImpact)))
        val hitEnemiesDetails = mutableMapOf<String, MutableList<Pair<Projectile, Float>>>()
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
            val effectiveSpeed = proj.speed * state.gameSpeed

            if (dist < effectiveSpeed) {
                // Visual Effect for AoE
                if (proj.aoeRadius > 0 && proj.sourceStallType != null) {
                    val stallDef = StallRegistry.get(proj.sourceStallType)
                    newVisualEffects.add(VisualEffect(
                        id = UUID.randomUUID().toString(),
                        position = targetPos,
                        color = stallDef.visualEffectColor ?: proj.color.copy(alpha = 0.5f),
                        startTimeMs = currentTimeMs,
                        durationMs = stallDef.visualEffectDuration,
                        type = stallDef.visualEffectType,
                        radius = proj.aoeRadius,
                        sourceStallType = proj.sourceStallType
                    ))
                }

                // Collect hits - Direct target first
                if (proj.targetEnemyId != null && enemyLookup.containsKey(proj.targetEnemyId) && !enemyLookup[proj.targetEnemyId]!!.isGrabbed) {
                    hitEnemiesDetails.getOrPut(proj.targetEnemyId) { mutableListOf() }.add(proj to 0f)
                }

                // Collect AoE hits
                if (proj.aoeRadius > 0) {
                    enemySpatialIndex.findNearby(targetPos, proj.aoeRadius).forEach { enemy ->
                        if (enemy.isGrabbed || enemy.id == proj.targetEnemyId) return@forEach
                        val distToImpact = GridUtils.axialDistance(enemy.position, targetPos)
                        hitEnemiesDetails.getOrPut(enemy.id) { mutableListOf() }.add(proj to distToImpact)
                    }
                }
            } else {
                // Keep moving
                finalProjectiles.add(proj.copy(
                    lastPosition = proj.position,
                    position = PreciseAxialCoordinate(
                        proj.position.q + (dq / dist) * effectiveSpeed,
                        proj.position.r + (dr / dist) * effectiveSpeed
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

            hits.forEach { (proj, dist) ->
                if (currentHealth <= 0f) return@forEach

                var damage = proj.damage

                // Apply Durian AoE damage falloff
                if (proj.sourceStallType == StallType.DURIAN && proj.aoeRadius > 0) {
                    val ratio = (dist / proj.aoeRadius).coerceIn(0f, 1f)
                    // damage at edge (ratio=1) is 25% of center (ratio=0)
                    // Damage = centerDamage * (1 - ratio * 0.75)
                    damage *= (1f - ratio * 0.75f)
                }
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
                            val isKill = currentHealth <= 0f
                            val newKills = if (isKill && !stall.stallType.isUtility) stall.kills + 1 else stall.kills
                            val newUniqueTargets = stall.uniqueTargetIds + enemy.id
                            if (newKills != stall.kills || newUniqueTargets.size != stall.uniqueTargetIds.size) {
                                updatedHexes[coord] = updatedHexes[coord]!!.copy(
                                    stall = stall.copy(
                                        uniqueTargetIds = newUniqueTargets,
                                        kills = newKills
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (currentHealth <= 0f) { // Consider dead if health rounds to 0
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

        val fullyUpdatedEnemies = cleanupEnemyBuffs(finalEnemies)

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

    fun graduateToNextLevel() {
        val dimensions = getLevelDimensions(_gameState.value.currentLevel + 1)
        val (hexes, startPos, endPos) = MapGenerator.generateRandomVerticalMap(width = dimensions.first, height = dimensions.second, random = random)

        val updatedState = _gameState.updateAndGet { state ->
            val nextLevel = state.currentLevel + 1
            state.copy(
                currentLevel = nextLevel,
                currentWave = 0,
                gold = 500 + (state.gold * 0.1f).toInt(),
                health = 10,
                kitchelinStars = state.kitchelinStars,
                hexes = hexes,
                startPosition = startPos,
                endPosition = endPos,
                enemies = emptyList(),
                projectiles = emptyList(),
                puddles = emptyList(),
                visualEffects = emptyList(),
                showGraduationOverlay = false,
                waveActive = false,
                goldEarnedThisWave = 0,
                score = state.score,
                selectedBoardStall = null,
                selectedStallType = null,
                lastSoldStall = null,
                bktToastMessage = null,
                isRemovePillarModeActive = false,
                isOutdoorPuddleModeActive = false,
                freeSpecificUpgrades = 0,
                activeBudgetBonuses = 0,
                showUpgradeOverlay = false,
                showStarActionOverlay = false,
                activeTutorial = null, // Maybe keep or clear? user said "Fresh everything".
                gameSpeed = 1.0f,
                simulationTimeMs = 0L,
                pathDistances = Pathfinding.calculateAllDistances(endPos, getBlockedCoordinates(hexes), hexes.keys)
            )
        }
        saveGame(updatedState)
    }

    /**
     * Validates if a stall can be placed at the given coordinate.
     * Checks for enemy proximity, path blocking, and Tray Return Uncle rules.
     *
     * @return The updated set of blocked coordinates if valid, null otherwise.
     */
    private fun validateStallPlacement(
        coord: AxialCoordinate,
        stallToPlace: Stall,
        state: GameState
    ): Set<AxialCoordinate>? {
        val tile = state.hexes[coord] ?: return null

        // 1. Prevent building on or immediately in front of enemies
        val isEnemyNear = state.enemies.any { enemy ->
            val currentTarget = enemy.path.getOrNull(enemy.currentPathIndex + 1)
            GridUtils.hexRound(enemy.position.q, enemy.position.r) == coord || currentTarget == coord
        }
        if (isEnemyNear) return null

        val blocked = getBlockedCoordinates(state.hexes) + coord
        val startPos = state.startPosition ?: return null
        val endPos = state.endPosition ?: return null

        // 2. Check "last empty space" rule for Tray Return Uncle
        val unclesToCheck = state.hexes.entries
            .filter { it.value.stall?.stallType == StallType.TRAY_RETURN_UNCLE }
            .map { it.key }
            .toMutableList()
        if (stallToPlace.stallType == StallType.TRAY_RETURN_UNCLE) unclesToCheck.add(coord)

        val violatesTrayUncleRule = unclesToCheck.any { uncleCoord ->
            GridUtils.getNeighbors(uncleCoord).none { neighbor ->
                val neighborTile = state.hexes[neighbor] ?: return@none false
                val isWalkable = (neighborTile.type == TileType.FLOOR || neighborTile.type == TileType.DRAIN) && !blocked.contains(neighbor)
                val isFixedWalkable = neighborTile.type == TileType.START || neighborTile.type == TileType.GOAL_TABLE
                isWalkable || isFixedWalkable
            }
        }
        if (violatesTrayUncleRule) return null

        // 3. Check if main path is still possible
        val startPath = Pathfinding.findPath(startPos, endPos, blocked, state.hexes.keys)
        if (startPath == null) return null

        // 4. Check if all existing enemies can still find a path
        val canRepathAll = state.enemies.all { enemy ->
            // Optimization: Only re-path if the new stall actually blocks their current path
            val pathRemainder = enemy.path.subList(enemy.currentPathIndex, enemy.path.size)
            if (!pathRemainder.contains(coord)) return@all true

            val currentTarget = enemy.path.getOrNull(enemy.currentPathIndex + 1) ?: endPos
            Pathfinding.findPath(currentTarget, endPos, blocked, state.hexes.keys) != null
        }
        if (!canRepathAll) return null

        return blocked
    }

    fun onCellClick(coord: AxialCoordinate) {
        val currentState = _gameState.value
        val tile = currentState.hexes[coord] ?: return

        if (currentState.isRemovePillarModeActive) {
            handleRemovePillarClick(coord, tile)
            return
        }

        if (currentState.isOutdoorPuddleModeActive) {
            handleOutdoorPuddleClick(coord)
            return
        }

        if (tile.stall != null || currentState.selectedStallType == null) {
            handleStallSelection(coord, tile)
        } else {
            handleBuildStall(coord)
        }
    }

    private fun handleStallSelection(coord: AxialCoordinate, tile: HexTile) {
        if (tile.stall != null) {
            _gameState.update { it.copy(selectedBoardStall = coord, selectedStallType = null, lastSoldStall = null) }
        } else {
            _gameState.update { it.copy(selectedBoardStall = null, selectedStallType = null) }
        }
    }

    private fun handleBuildStall(coord: AxialCoordinate) {
        val prevState = _gameState.value
        val updatedState = _gameState.updateAndGet { state ->
            val tile = state.hexes[coord] ?: return@updateAndGet state
            val stallToPlace = state.selectedStallType ?: return@updateAndGet state

            if (state.gold >= stallToPlace.cost && (tile.type == TileType.FLOOR || tile.type == TileType.DRAIN)) {
                val blocked = validateStallPlacement(coord, stallToPlace, state)

                if (blocked != null) {
                    val newHexes = state.hexes.toMutableMap()
                    newHexes[coord] = tile.copy(
                        stall = stallToPlace.copy(id = UUID.randomUUID().toString()),
                        isPermanentlyWet = false
                    )

                    val updatedEnemies = recalculateEnemyPaths(state, blocked, newHexes)
                    val endPos = state.endPosition!!
                    state.copy(
                        hexes = newHexes,
                        gold = state.gold - stallToPlace.cost,
                        enemies = updatedEnemies,
                        lastSoldStall = null,
                        pathDistances = Pathfinding.calculateAllDistances(endPos, blocked, newHexes.keys)
                    )
                } else state
            } else state
        }
        if (updatedState !== prevState) {
            updateBoostCache(updatedState)
            saveGame(updatedState)
        }
    }

    private fun handleOutdoorPuddleClick(coord: AxialCoordinate) {
        val chain = getOutdoorPuddleChain(coord)
        if (chain.size == 4) {
            applyOutdoorPuddles(chain)
        }
    }

    private fun handleRemovePillarClick(coord: AxialCoordinate, tile: HexTile) {
        if (tile.type == TileType.PILLAR) {
            removePillar(coord)
        }
    }

    fun sellStall() {
        val prevState = _gameState.value
        val updatedState = _gameState.updateAndGet { state ->
            val coord = state.selectedBoardStall ?: return@updateAndGet state
            val tile = state.hexes[coord] ?: return@updateAndGet state
            val stall = tile.stall ?: return@updateAndGet state

            val refund = (stall.totalInvestment * 0.5f).toInt()
            val newHexes = state.hexes.toMutableMap()
            newHexes[coord] = tile.copy(stall = null)

            val blocked = getBlockedCoordinates(newHexes)

            var updatedEnemies = state.enemies
            if (stall.heldEnemyId != null) {
                updatedEnemies = updatedEnemies.map { enemy ->
                    if (enemy.id == stall.heldEnemyId) {
                        releaseEnemy(enemy, coord, newHexes, state.endPosition)
                    } else enemy
                }
            }
            updatedEnemies = recalculateEnemyPaths(state.copy(enemies = updatedEnemies), blocked, newHexes)
            val endPos = state.endPosition!!

            state.copy(
                hexes = newHexes,
                gold = state.gold + refund,
                enemies = updatedEnemies,
                selectedBoardStall = null,
                lastSoldStall = coord to stall.copy(heldEnemyId = null, releaseTimeMs = 0L),
                pathDistances = Pathfinding.calculateAllDistances(endPos, blocked, newHexes.keys)
            )
        }
        if (updatedState !== prevState) {
            updateBoostCache(updatedState)
            saveGame(updatedState)
        }
    }

    fun undoSell() {
        val prevState = _gameState.value
        val updatedState = _gameState.updateAndGet { state ->
            val (coord, stall) = state.lastSoldStall ?: return@updateAndGet state
            val tile = state.hexes[coord] ?: return@updateAndGet state

            if (tile.stall != null) {
                return@updateAndGet state.copy(lastSoldStall = null)
            }

            val refund = (stall.totalInvestment * 0.5f).toInt()
            if (state.gold < refund) return@updateAndGet state

            val blocked = validateStallPlacement(coord, stall, state) ?: return@updateAndGet state
            val newHexes = state.hexes.toMutableMap()
            newHexes[coord] = tile.copy(stall = stall)

            val updatedEnemies = recalculateEnemyPaths(state, blocked, newHexes)
            val endPos = state.endPosition!!
            state.copy(
                hexes = newHexes,
                gold = state.gold - refund,
                enemies = updatedEnemies,
                lastSoldStall = null,
                pathDistances = Pathfinding.calculateAllDistances(endPos, blocked, newHexes.keys)
            )
        }
        if (updatedState !== prevState) {
            updateBoostCache(updatedState)
            saveGame(updatedState)
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
        val prevState = _gameState.value
        val updatedState = _gameState.updateAndGet {
            if (it.kitchelinStars > 0) {
                it.copy(
                    kitchelinStars = it.kitchelinStars - 1,
                    activeBudgetBonuses = it.activeBudgetBonuses + 1,
                    showStarActionOverlay = false
                )
            } else it
        }
        if (updatedState !== prevState) {
            saveGame(updatedState)
        }
    }

    fun restoreHealth() {
        val prevState = _gameState.value
        val updatedState = _gameState.updateAndGet {
            if (it.kitchelinStars > 0 && it.health < 10) {
                it.copy(
                    kitchelinStars = it.kitchelinStars - 1,
                    health = it.health + 1,
                    showStarActionOverlay = false
                )
            } else it
        }
        if (updatedState !== prevState) {
            saveGame(updatedState)
        }
    }

    fun chooseFreeUpgrade() {
        val prevState = _gameState.value
        val updatedState = _gameState.updateAndGet {
            if (it.kitchelinStars > 0) {
                it.copy(
                    kitchelinStars = it.kitchelinStars - 1,
                    freeSpecificUpgrades = it.freeSpecificUpgrades + 1,
                    showStarActionOverlay = false
                )
            } else it
        }
        if (updatedState !== prevState) {
            saveGame(updatedState)
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
        val prevState = _gameState.value
        val updatedState = _gameState.updateAndGet { state ->
            if (state.kitchelinStars < 2) return@updateAndGet state

            val newHexes = state.hexes.toMutableMap()
            chain.forEach { coord ->
                newHexes[coord] = newHexes[coord]!!.copy(isPermanentlyWet = true)
            }

            state.copy(
                kitchelinStars = state.kitchelinStars - 2,
                hexes = newHexes,
                isOutdoorPuddleModeActive = false
            )
        }
        if (updatedState !== prevState) {
            saveGame(updatedState)
            triggerHaptic()
        }
    }

    private fun removePillar(coord: AxialCoordinate) {
        val currentTime = System.currentTimeMillis()
        val prevState = _gameState.value
        val updatedState = _gameState.updateAndGet { state ->
            if (state.kitchelinStars > 0 && state.hexes[coord]?.type == TileType.PILLAR) {
                val newHexes = state.hexes.toMutableMap()
                newHexes[coord] = state.hexes[coord]!!.copy(type = TileType.FLOOR)

                val blocked = getBlockedCoordinates(newHexes)
                val updatedEnemies = recalculateEnemyPaths(state.copy(hexes = newHexes), blocked, newHexes)
                val endPos = state.endPosition!!

                state.copy(
                    kitchelinStars = state.kitchelinStars - 1,
                    hexes = newHexes,
                    enemies = updatedEnemies,
                    isRemovePillarModeActive = false,
                    lastShakeTimeMs = currentTime,
                    pathDistances = Pathfinding.calculateAllDistances(endPos, blocked, newHexes.keys)
                )
            } else state
        }
        if (updatedState !== prevState) {
            updateBoostCache(updatedState)
            saveGame(updatedState)
            triggerHaptic()
        }
    }

    private fun applyUpgrade(isSpecific: Boolean, specificStat: String? = null) {
        val prevState = _gameState.value
        val updatedState = _gameState.updateAndGet { state ->
            val coord = state.selectedBoardStall ?: return@updateAndGet state
            val tile = state.hexes[coord] ?: return@updateAndGet state
            val stall = tile.stall ?: return@updateAndGet state

            val hasFreeUpgrade = state.freeSpecificUpgrades > 0
            val finalUpgradeCost = StallUpgradeManager.calculateUpgradeCost(stall, isSpecific, hasFreeUpgrade)

            if (state.gold >= finalUpgradeCost) {
                val availableStats = StallUpgradeManager.getAvailableUpgradeStats(stall)
                if (availableStats.isEmpty()) return@updateAndGet state
                val statToUpgrade = if (isSpecific && specificStat != null) {
                    if (!availableStats.contains(specificStat)) return@updateAndGet state
                    specificStat
                } else {
                    availableStats.random(this@MainViewModel.random)
                }

                val updatedStall = StallUpgradeManager.applyUpgrade(stall, statToUpgrade, finalUpgradeCost, isSpecific)

                val newHexes = state.hexes.toMutableMap()
                newHexes[coord] = tile.copy(stall = updatedStall)

                state.copy(
                    hexes = newHexes,
                    gold = state.gold - finalUpgradeCost,
                    freeSpecificUpgrades = if (isSpecific && hasFreeUpgrade) state.freeSpecificUpgrades - 1 else state.freeSpecificUpgrades
                )
            } else state
        }
        if (updatedState !== prevState) {
            updateBoostCache(updatedState)
            saveGame(updatedState)
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
        val updatedState = _gameState.updateAndGet {
            it.copy(hexes = newHexes, lastSoldStall = null)
        }
        saveGame(updatedState)
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

    /**
     * Ensures all enemy buffs are valid based on the current state of their sources.
     * Removes ARMOR buffs if the source Tiger Mom is dead, no longer targeting the enemy, or is grabbed.
     * Also clears buffingTargetId and stops the stalling behavior if the target no longer exists.
     */
    private fun cleanupEnemyBuffs(enemies: List<Enemy>): List<Enemy> {
        val enemyLookup = enemies.associateBy { it.id }
        return enemies.map { enemy ->
            // 1. Clean up ARMOR buffs
            val validBuffs = enemy.buffs.filter { buff ->
                if (buff.type == BuffType.ARMOR) {
                    val source = enemyLookup[buff.sourceId]
                    // Source must exist, still be targeting this enemy, and NOT be grabbed
                    source != null && source.buffingTargetId == enemy.id && !source.isGrabbed
                } else true
            }

            // 2. Clean up own buffingTargetId if target is gone/dead or we are grabbed
            var updatedBuffingTargetId = enemy.buffingTargetId
            var isStopped = enemy.isStopped
            var stopDurationMs = enemy.stopDurationMs

            if (updatedBuffingTargetId != null) {
                val target = enemyLookup[updatedBuffingTargetId]
                val shouldClear = target == null || target.isDead || enemy.isGrabbed
                if (shouldClear) {
                    updatedBuffingTargetId = null
                    if (enemy.type == EnemyType.TIGER_MOM) {
                        isStopped = false
                        stopDurationMs = 0L
                    }
                }
            }

            if (validBuffs.size != enemy.buffs.size || updatedBuffingTargetId != enemy.buffingTargetId) {
                enemy.copy(
                    buffs = validBuffs,
                    buffingTargetId = updatedBuffingTargetId,
                    isStopped = isStopped,
                    stopDurationMs = stopDurationMs
                )
            } else enemy
        }
    }

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
