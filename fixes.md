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
