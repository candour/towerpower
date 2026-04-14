# Hawker Rush Game Plan

## Overview
This document outlines the architecture and implementation progress of the Stall Defense game.

## Current Architecture
- **UI Framework**: Jetpack Compose
- **Pattern**: MVVM (Model-View-ViewModel)
- **State Management**: Kotlin Flow and `collectAsState`

### Layout Components
1. **GameBoard (Top 75%)**:
    - A custom `Canvas` within scrollable containers.
    - Renders a grid-based map with configurable start and end points.
    - Handles cell selection for stall placement.
    - Uses a 20dp border around the entire game area.
2. **GameControlPanel (Bottom 25%)**:
    - Displays player statistics (Health, Gold).
    - Contains a stall selector (up to 6 types).
    - Includes a "Start Wave" button (currently static).

### Data Models
- `GameState`: Root state containing grid, currency, health, and current selection.
- `GridCell`: Represents a single tile on the map (Empty, Path, Start, End) and its occupancy.
- `Stall`: Defines stall attributes like cost, damage, range, and color.
- `Enemy`: Defines enemy attributes and current position.

## Progress
- [x] Initial Project Setup
- [x] Orientation Lock (Portrait)
- [x] Basic UI Layout (75/25 split)
- [x] Grid System with Stall Placement Logic
- [x] Game Stats Display (Gold/Health)
- [x] Stall Selection System

## Future Roadmap
1. **Dynamic Pathing**: Implement A* or Dijkstra's algorithm to calculate enemy paths when stalls are placed.
2. **Enemy Spawning**: Implement wave logic and enemy movement.
3. **Combat Engine**: Stall range detection and projectile logic.
4. **Game Loop**: A continuous update loop for real-time movement and actions.
5. **Assets**: Replace geometric shapes with actual game sprites and animations.
