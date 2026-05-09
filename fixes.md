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
