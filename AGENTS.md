# Hawker Rush: Agent Instructions

This document provides a comprehensive guide for AI agents working on the Hawker Rush project.

## Project Overview
**Hawker Rush** is a tower defense game built for Android using Jetpack Compose and MVVM architecture. Players place food stalls (towers) to defend against waves of enemies in a hawker center setting.

## Architecture
- **Pattern:** MVVM (Model-View-ViewModel).
- **UI:** Jetpack Compose for all screens and game rendering.
- **State Management:** `GameState` held in `MainViewModel`, backed by Kotlin Flows.
- **Persistence:**
  - `SettingsRepository`: Uses Android DataStore for global settings (Haptics, High Scores).
  - `GameStateRepository`: Manages `gamestate.json` via GSON for auto-saving after each wave.
- **Rendering:** Custom `Canvas` implementation in `GameBoard.kt`.

## Key Game Mechanics

### Coordinate System (The Hex Grid)
- **Type:** Pointy-topped hexagonal grid using Axial coordinates (q, r).
- **Aspect Ratio:** 101:91.
- **Continuous Transformation:** Use the continuous axial-to-screen formula to avoid jerky movement:
  - `x = (q + r / 2f) * hexWidth`
  - `y = r * hexHeight * 0.69f`
- **Pathfinding:** Implemented in `Pathfinding.kt` using A*. Enemies recalculate paths immediately when stalls are placed or sold.

### Rendering & Depth
- **zOrder Groups:**
  - `0`: Floor
  - `1`: Edges/Puddles
  - `2`: Standard entities (Stalls, Enemies, Projectiles)
  - `3+`: Overlays (>= 10)
- **Sorting Logic:** Within a zOrder group, sort by `r` (row), then `zOrder`, then `q` (column) to ensure correct isometric depth.

### Stalls & Upgrades
- **Stall Types:** Teh Tarik (Slow), Satay (AOE), Chicken Rice (Single Target), Durian (High Damage/Slow Fire), Ice Kachang (Freeze).
- **Upgrades:** Additive scaling based on base stats. Costs increase linearly (+10% of base cost per level).
- **Selling:** Provides a 50% refund of the total investment (base cost + upgrades).
- **Targeting:** Supports FIRST, CLOSEST, STRONGEST, and WEAKEST strategies.
- **Legendary Names:** Stalls receive a 'legendary' suffix when their first upgrade category hits Level 10, and a 'legendary' prefix when a second, different category hits Level 10. These names are managed via `LegendaryNames.kt`.

### Enemies & Difficulty
- **Variants:** Salaryman (Fast), Tourist (Stops), Auntie (Tank), Delivery Rider (Boss).
- **Spawning:** Uses a Difficulty Budget system. HP increases by 10% per wave (`BaseHP * 1.1^(W-1)`).
- **Boss Waves:** Occur every 10 levels. Trigger a 2-second 'BOSS WAVE' UI overlay.
- **Tutorial System:** Tracks seen entities (enemies, etc.) globally in `Settings.shownTutorials`. Triggers during `MainViewModel.startWave()` if new types appear, pausing the game by setting `GameState.activeTutorial` and requiring dismissal via `MainViewModel.dismissTutorial()`.

## Asset Management

### Sprite Sheets
- `app/src/main/res/drawable/sprite_sheet.png`: Main assets.
- `app/src/main/res/drawable/stalls.png`: Tall stall assets.
- `app/src/main/res/drawable-nodpi/buttons.png`: Menu and UI buttons.
- `app/src/main/res/drawable-nodpi/enemies.png`: Animated enemy sprites.

### Important Asset Rules
- **DPI Scaling:** Always place sprite-based assets with hardcoded pixel coordinates in `drawable-nodpi` to prevent Android's automatic scaling.
- **Resource Naming:** Use only lowercase letters (a-z), numbers (0-9), and underscores.
- **Anchoring:**
  - Stalls/Pillars: Bottom-center at hex center (`Offset(0.5f, 0.8f)`).
  - Enemies: Feet (bottom-center) at hex center (`Offset(0.5f, 1.0f)`).
  - Goal Tables: Bottom-center.

### Enemy Animation
- Enemies cycle through 3 frames every 1.5s (500ms per frame).
- Animation only progresses while the enemy is moving.
- Facing is determined by horizontal movement; flip horizontally if `isFacingLeft` is true.

## Tooling
- **Background Removal:** Use `python3 tools/remove_bg.py <input> <output> --color R G B --tolerance <value>` to clean up assets.
- **Sprite Extraction:** `tools/extract_sprites.py` exists but is specialized for 256x256 grids; use with caution.

## Build and Test
- **Build:** `./gradlew assembleDebug`
- **Unit Tests:** `./gradlew test` (Note: `MainViewModel` tests require mocking `application.applicationContext`).
- **Instrumented Tests:** `./gradlew connectedAndroidTest`

## Agent "Gotchas" & Conventions
- **AGENTS.md Maintenance:** You **MUST** update this file whenever you introduce new core mechanics, architectural changes, or complex "gotchas".
- **Visual Consistency:** All action buttons must use `SpriteButton` and reference `buttons.png`.
- **Haptics:** Always trigger haptics via `viewModel.triggerHaptic()` which respects user settings.
- **Navigation:** Screen transitions are managed in `MainActivity` using `AnimatedContent`. Use the grouped 'MENU' state for smooth transitions between Loading and Main Menu.
- **Memory vs. Reality:** While this file provides context, always treat the current codebase as the ultimate source of truth.
