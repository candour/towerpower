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

    fun resumeGame() {
        val savedState = gameStateRepository.loadGameState()
        if (savedState != null) {
            _gameState.value = savedState
        }
    }

    fun resetGame() {
        gameStateRepository.deleteGameState()
        val (hexes, startPos, endPos) = MapGenerator.generateRandomVerticalMap(width = 8, height = 16, random = random)
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

        var attempts = 0
        while (remainingBudget > 0 && attempts < 100) {
            val type = allowedTiers[random.nextInt(allowedTiers.size)]

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

        return enemyList.shuffled(random)
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
        var hapticRequested = false
        var gameOverState: GameState? = null

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
                            val (boost, providers) = calculateStatBoost(coord, updatedHexes)
                            atmGold += (stallDef.passiveIncome * boost).toInt()

                            // Update Bak Kut Teh stats
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

            var freezeDuration = maxOf(0, enemy.freezeDurationMs - 32)
            var speedBoostDuration = maxOf(0, enemy.speedBoostDurationMs - 32)

            val behaviorUpdatedEnemy = enemyDef.updateSpecialBehavior(enemy, currentTimeMs)
            var isStopped = behaviorUpdatedEnemy.isStopped
            var stopDurationMs = behaviorUpdatedEnemy.stopDurationMs
            var lastStopMs = behaviorUpdatedEnemy.lastStopMs

            var speedMultiplier = 1.0f
            state.puddles.forEach { puddle ->
                if (GridUtils.axialDistance(enemy.position, puddle.position) < 0.8) {
                    speedMultiplier = enemyDef.getPuddleSlowMultiplier(enemy.type)
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
            stall != null && stall.fireRateMs > 0 && stall.disabledWaves == 0 &&
                    stall.heldEnemyId == null && (currentTimeMs - stall.lastFiredMs) >= stall.fireRateMs
        }

        if (firingStalls.isEmpty() || state.enemies.isEmpty()) return state

        val newProjectiles = state.projectiles.toMutableList()
        val newPuddles = state.puddles.toMutableList()
        val updatedHexes = state.hexes.toMutableMap()
        val newlyGrabbedEnemyIds = mutableSetOf<String>()

        firingStalls.forEach { (coord, tile) ->
            val stall = tile.stall!!
            val stallPos = PreciseAxialCoordinate(coord.q.toFloat(), coord.r.toFloat())

            // Find all potential targets within range that are not already grabbed
            val potentialTargets = state.enemies.filter { enemy ->
                !enemy.isGrabbed && enemy.id !in newlyGrabbedEnemyIds &&
                        GridUtils.axialDistance(enemy.position, stallPos) <= stall.range &&
                        (!stall.isBlockable || !isLineOfSightBlocked(coord, enemy.position, state.hexes))
            }

            val target = when (stall.targetMode) {
                TargetMode.FIRST -> potentialTargets.maxByOrNull { it.currentPathIndex }
                TargetMode.CLOSEST -> potentialTargets.minByOrNull { GridUtils.axialDistance(it.position, stallPos) }
                TargetMode.STRONGEST -> potentialTargets.maxByOrNull { it.health }
                TargetMode.WEAKEST -> potentialTargets.minByOrNull { it.health }
            } ?: return@forEach

            val (boost, providers) = calculateStatBoost(coord, state.hexes)

            // Update Bak Kut Teh stats for adjacency tracking
            providers.forEach { providerCoord ->
                updatedHexes[providerCoord]?.let { tileWithBkt ->
                    tileWithBkt.stall?.let { bktStall ->
                        updatedHexes[providerCoord] = tileWithBkt.copy(
                            stall = bktStall.copy(uniqueTargetIds = bktStall.uniqueTargetIds + stall.id)
                        )
                    }
                }
            }

            if (stall.stallType == StallType.TRAY_RETURN_UNCLE) {
                newlyGrabbedEnemyIds.add(target.id)
                val boostedDuration = (stall.effectDurationMs * boost).toLong()
                updatedHexes[coord] = tile.copy(
                    stall = stall.copy(
                        lastFiredMs = currentTimeMs,
                        heldEnemyId = target.id,
                        releaseTimeMs = currentTimeMs + boostedDuration,
                        uniqueTargetIds = stall.uniqueTargetIds + target.id
                    )
                )
            } else {
                val stallDef = StallRegistry.get(stall.stallType)
                // Temporarily boost stall for fire calculation
                val boostedStall = if (boost > 1.0f) {
                    stall.copy(
                        damage = (stall.damage * boost).toInt(),
                        effectDurationMs = (stall.effectDurationMs * boost).toLong(),
                        freezeDurationMs = (stall.freezeDurationMs * boost).toLong()
                    )
                } else stall

                val fireResult = stallDef.fire(boostedStall, coord, target, currentTimeMs, state.hexes)
                var updatedStall = (fireResult as? FireResult.NewProjectile)?.updatedStall ?: stall

                // Reset boosted stats but keep firing metadata (rotation, lastFiredMs)
                updatedStall = updatedStall.copy(
                    lastFiredMs = currentTimeMs,
                    damage = stall.damage,
                    effectDurationMs = stall.effectDurationMs,
                    freezeDurationMs = stall.freezeDurationMs
                )

                when (fireResult) {
                    is FireResult.NewProjectile -> newProjectiles.add(fireResult.projectile)
                    is FireResult.NewPuddle -> newPuddles.add(fireResult.puddle)
                }
                updatedHexes[coord] = tile.copy(stall = updatedStall)
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

            var currentHealth = enemy.health.toFloat()
            var maxFreezeDuration = enemy.freezeDurationMs
            var speedBoostDuration = enemy.speedBoostDurationMs

            hits.forEach { proj ->
                if (currentHealth <= 0) return@forEach

                var damage = proj.damage.toFloat()
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
                    health = currentHealth.toInt(),
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

        if (tile.stall != null) {
            // Select existing stall
            _gameState.update { it.copy(selectedBoardStall = coord, selectedStallType = null) }
        } else if (currentState.selectedStallType != null) {
            // Place new stall
            val stallToPlace = currentState.selectedStallType
            if (currentState.gold >= stallToPlace.cost && tile.type == TileType.FLOOR) {
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
                        it != coord && (neighborTile.type == TileType.FLOOR && !blocked.contains(it) ||
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

    fun enterRemovePillarMode() {
        _gameState.update { it.copy(isRemovePillarModeActive = true, showStarActionOverlay = false) }
    }

    fun exitRemovePillarMode() {
        _gameState.update { it.copy(isRemovePillarModeActive = false) }
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
            it.stall != null || it.type is TileType.Obstruction || it.type == TileType.GOAL_TABLE || it.type.name.startsWith("EDGE_")
        }.map { it.coordinate }.toSet()
    }

    private fun isLineOfSightBlocked(
        stallCoord: AxialCoordinate,
        enemyPos: PreciseAxialCoordinate,
        hexes: Map<AxialCoordinate, HexTile>
    ): Boolean {
        val obstructions = hexes.values.filter { it.type is TileType.Obstruction }
        if (obstructions.isEmpty()) return false

        // Convert to a consistent 2D space for circle-segment intersection
        val yFactor = (91f / 101f) * 0.69f

        val x1 = stallCoord.q + stallCoord.r / 2f
        val y1 = stallCoord.r * yFactor

        val x2 = enemyPos.q + enemyPos.r / 2f
        val y2 = enemyPos.r * yFactor

        val radius = 0.25f // Blocked area is half diameter (0.5), so radius is 0.25

        for (obs in obstructions) {
            val pc = obs.coordinate
            val px = pc.q + pc.r / 2f
            val py = pc.r * yFactor

            if (lineIntersectsCircle(x1, y1, x2, y2, px, py, radius)) {
                return true
            }
        }
        return false
    }

    private fun lineIntersectsCircle(x1: Float, y1: Float, x2: Float, y2: Float, cx: Float, cy: Float, r: Float): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1

        val fx = x1 - cx
        val fy = y1 - cy

        val a = dx * dx + dy * dy
        if (a < 0.0001f) return false // Essentially same point

        val b = 2 * (fx * dx + fy * dy)
        val c = (fx * fx + fy * fy) - r * r

        var discriminant = b * b - 4 * a * c
        if (discriminant < 0) {
            return false
        } else {
            discriminant = Math.sqrt(discriminant.toDouble()).toFloat()
            val t1 = (-b - discriminant) / (2 * a)
            val t2 = (-b + discriminant) / (2 * a)

            if ((t1 >= 0 && t1 <= 1) || (t2 >= 0 && t2 <= 1)) {
                return true
            }
            if (t1 < 0 && t2 > 1) return true
        }
        return false
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

    data class BoostResult(val multiplier: Float, val providerCoords: List<AxialCoordinate>)

    private fun calculateStatBoost(coord: AxialCoordinate, hexes: Map<AxialCoordinate, HexTile>): BoostResult {
        val adjacentCoords = GridUtils.getNeighbors(coord)
        var totalBoostPercent = 0
        val providers = mutableListOf<AxialCoordinate>()
        adjacentCoords.forEach { adj ->
            val stall = hexes[adj]?.stall
            if (stall != null && stall.stallType == StallType.BAK_KUT_TEH && stall.disabledWaves == 0) {
                totalBoostPercent += stall.damage // Bak Kut Teh damage field stores its current boost %
                providers.add(adj)
            }
        }
        return BoostResult(1.0f + (totalBoostPercent / 100f), providers)
    }
}
