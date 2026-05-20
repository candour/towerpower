# Bug Fixes and Improvements

## Grid Logic Centralization
- Created `GridUtils.kt` to centralize axial coordinate math.
- Moved `NEIGHBOR_OFFSETS`, `getNeighbors`, and `axialDistance` (overloaded for `AxialCoordinate` and `PreciseAxialCoordinate`) to `GridUtils.kt`.
- Updated `MainViewModel.kt`, `MapGenerator.kt`, and `Pathfinding.kt` to use the unified utility methods, eliminating redundant code.

## Pathfinding Optimization
- Rewrote `Pathfinding.kt` from scratch using an idiomatic Kotlin A* implementation.
- Improved search efficiency and path reconstruction using `mutableListOf().asReversed()`.
- Integrated with `GridUtils` for all neighbor and distance calculations.

## Game Engine Efficiency
- Refactored `handleEnemyMovement` in `MainViewModel.kt` to consolidate puddle-based logic.
- Reduced the number of iterations over `state.puddles` and redundant `axialDistance` calls within the customer movement loop.
- Streamlined status duration updates (freeze, speed boost) using `Math.max`.
# Hawker Rush: Bug Fixes

This document tracks identified and resolved bugs in the Hawker Rush codebase.

| ID | Date | Description | Status |
| :--- | :--- | :--- | :--- |
| FIX-001 | 2025-05-15 | Updated `calculateStatBoost` in `MainViewModel.kt` to accept a hex map instead of `GameState`. This ensures that Bak Kut Teh stalls re-enabled during wave completion correctly provide bonuses to adjacent ATMs in the same wave. | Resolved |
| FIX-002 | 2025-05-20 | Fixed a bug where multiple Tray Return Uncles could grab the same enemy in a single tick. Introduced `newlyGrabbedEnemyIds` to track and prevent duplicate targeting within `handleStallFiring`. | Resolved |
| REF-001 | 2025-05-16 | Refactored `StallUpgradeManager.kt` to optimize stat calculation by introducing a cache and replacing iterative loops with idiomatic `fold` operations. This improves efficiency and maintainability of the upgrade logic. | Resolved |
| REF-002 | 2025-05-22 | Rewrote `MapGenerator.kt` to use a deterministic "path-first" algorithm, eliminating the inefficient `while(true)` brute-force loop. Introduced `offsetToAxial` utility and added `Random` injection for better testability. | Resolved |
| FIX-003 | 2025-05-24 | Fixed ATM passive income logic in `MainViewModel.kt` to ensure income is only collected if the ATM was enabled during the wave. Improved iteration safety by using `toList()` during wave completion. | Resolved |
| REF-003 | 2025-05-25 | Refactored `handleStallFiring` and `handleProjectiles` in `MainViewModel.kt` for improved performance. Introduced enemy lookup maps, optimized AoE collision detection, and consolidated state updates to reduce redundant iterations and state copies. | Resolved |
| FIX-004 | 2025-05-26 | Fixed Tiger Mom buff cleanup delay in `MainViewModel.kt` and deduplicated `availableStats` in `UpgradeOverlay.kt`. Removed a redundant null check in `onCellClick`. | Resolved |
| FIX-005 | 2025-05-27 | Refactored health and damage to `Float` across models and registries to prevent precision loss in combat. Improved rendering z-sorting in `GameBoard.kt` to ensure projectiles and overhead effects (gas, money) always render above world entities. Added percentage health display to enemies. | Resolved |
| REF-004 | 2025-05-27 | Refactored `GameBoard.kt` to use a layered rendering architecture. Optimized performance by eliminating per-frame allocations through direct drawing, in-place sorting, and cached timestamps. Implemented accurate hex rounding for grid taps and fixed pillar removal animations. | Resolved |
| REF-005 | 2025-05-28 | Refactored `generateEnemyList` in `MainViewModel.kt` for improved maintainability and efficiency. Replaced procedural logic for early waves with a data-driven configuration map and optimized the budget-based generation loop by pre-filtering affordable enemy tiers. | Resolved |
| FIX-006 | 2025-05-30 | Fixed the 'Tray Return Uncle' placement check in `MainViewModel.kt` to include `DRAIN` tiles as valid walkable hexes for enemy release, ensuring consistency with the `releaseEnemy` logic. | Resolved |
| REF-006 | 2025-06-02 | Refactored `MainViewModel.kt` game engine for efficiency. Consolidated transient updates (puddles, visual effects, held enemies) into a single pass to reduce `GameState` copying. Optimized Line-of-Sight by pre-filtering obstructions and centralizing geometry logic in `GridUtils.kt`. | Resolved |
| FIX-007 | 2025-06-05 | Improved stall firing logic in `Registry.kt` by replacing `Math.round` with `GridUtils.hexRound` for accurate `TEH_TARIK` drain detection, removed hardcoded values for `SATAY`, and corrected `SATAY` rotation for better isometric alignment. | Resolved |
| REF-007 | 2025-06-07 | Refactored `Registry.kt` to use a polymorphic behavior model for stalls and enemies. Introduced `StallBehavior` and `EnemyBehavior` interfaces, replaced type-based `when` switches with delegated behavior calls, and simplified `MainViewModel.kt` interactions with the registry. | Resolved |
| FIX-008 | 2025-06-10 | Fixed a "teleportation" bug where using `undoSell` on a `TRAY_RETURN_UNCLE` would restore its held enemy state, causing it to prematurely release (and thus teleport) the previously held enemy back to the Uncle's vicinity. | Resolved |
| REF-008 | 2025-06-12 | Refactored `handleStallFiring` and `StallBehavior` to optimize enemy targeting. Introduced pre-sorted enemy lists and encapsulated targeting logic in behaviors, reducing $O(S \times E)$ Line-of-Sight checks to $O(S \times \text{small constant})$. | Resolved |
| FIX-009 | 2025-06-15 | Fixed Bak Kut Teh wave buff logic in `MainViewModel.kt` to ignore disabled stalls. Corrected Bak Kut Teh base boost and upgrade scaling to 20% to align with `STALL_STATS.md` and `AGENTS.md`. | Resolved |
| REF-009 | 2025-06-18 | Refactored the core game engine pipeline in `MainViewModel.kt` for improved efficiency. Introduced a `SpatialIndex` utility to optimize proximity-based lookups for puddles and projectile AoE, reducing complexity from O(N*M) to O(N). | Resolved |
| FIX-010 | 2025-06-20 | Corrected Bak Kut Teh boost percentage to 10% across code and documentation, removed incorrect ATM income buffing, and updated tutorial descriptions. | Resolved |
| FIX-011 | 2025-06-22 | Corrected the enemy death threshold in `MainViewModel.kt` from `1.0f` to `0f` to ensure precise health tracking and prevent premature deaths. | Resolved |
| REF-010 | 2025-06-25 | Refactored `StallUpgradeManager.kt` to use a declarative `StatScaler` strategy pattern. Optimized efficiency by moving constant calculations out of iteration loops and simplifying the core `calculateValue` logic. | Resolved |
| FIX-012 | 2025-06-28 | Fixed a bug where players could place stalls on or immediately in front of moving enemies, causing them to clip through the new structures. Implemented placement checks in `MainViewModel.onCellClick`. | Resolved |
| REF-011 | 2026-07-01 | Refactored the game engine pipeline and `StallBehavior` targeting logic for improved performance and efficiency. Introduced pre-calculation of engine data, optimized $O(1)$ targeting via `SpatialIndex`, and consolidated state updates to minimize memory overhead. | Resolved |
| FIX-013 | 2026-07-05 | Fixed a bug in `MainViewModel.undoSell` where stalls could be restored on tiles occupied by or targeted by enemies. | Resolved |
| FIX-014 | 2026-05-20 | Fixed a bug where `undoSell` did not enforce pathing or Tray Return Uncle rules, allowing players to block the main path or leave Uncles without neighbors. Centralized validation into `validateStallPlacement`. | Resolved |
| REF-012 | 2026-05-18 | Refactored the enemy processing pipeline in `MainViewModel.kt` to consolidate movement and transient logic into `handleEnemyPipeline`. Introduced `EngineUpdateBatch` for efficient state updates and optimized "held enemy" tracking to eliminate redundant grid-wide iterations. | Resolved |
| REF-013 | 2026-07-10 | Refactored `MainViewModel.kt` stall interaction logic (onCellClick, sellStall, undoSell) for better modularity and atomicity. Optimized `validateStallPlacement` by skipping redundant A* pathfinding for enemies whose paths are not intersected by new stalls. | Resolved |
