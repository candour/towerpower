# Hawker Rush Stall Statistics

This document provides a breakdown of the base statistics and upgrade scaling for all stalls in Hawker Rush.

## Stall Statistics Breakdown

| Stall Type | Base Damage / Boost | Fire Rate (ms) | Shots / Sec | Base DPS | Range (hexes) | Allowed Upgrades | Base Cost |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: |
| **Chicken Rice** | 10 | 500 | 2.00 | 20.00 | 4.0 | Damage, Range, Rate | $100 |
| **Teh Tarik** | 0 | 1000 | 1.00 | 0.00 | 3.0 | Range, Rate, Duration | $150 |
| **Satay** | 30 | 1500 | 0.67 | 20.00 | 2.5 | Damage, Range, Rate, Radius | $200 |
| **Ice Kachang** | 0 | 1500 | 0.67 | 0.00 | 3.5 | Range, Rate, Effect | $250 |
| **Durian** | 150 | 2000 | 0.50 | 75.00 | 3.0 | Damage, Range, Rate, Radius | $300 |
| **Tray Return Uncle** | 0 | 15000 | 0.07 | 0.00 | 1.1 | Rate, Duration | $250 |
| **ATM** | 0 | N/A | 0.00 | 0.00 | 0.0 | None | $1000 |
| **Bak Kut Teh** | 10% | N/A | 0.00 | 0.00 | 1.1 | Boost | $300 |

---

## Targeting Options

Stalls can be configured to target customers using different strategies. You can cycle through these modes by selecting a placed stall.

- **FIRST:** Targets the customer that is furthest along their path (closest to the goal).
- **CLOSEST:** Targets the customer that is physically closest to the stall.
- **STRONGEST:** Targets the customer with the highest current health.
- **WEAKEST:** Targets the customer with the lowest current health.

---

## Upgrade Scaling

Upgrades are chosen randomly from available categories when an upgrade is purchased. Scaling is primarily **multiplicative** for damage and **additive** for other stats. Every 10th upgrade for a specific stat provides a **Milestone Boost**.

### Upgrade Cost
The cost of each upgrade increases linearly based on the stall's base price:
- **Index N Upgrade:** `Base Cost * (0.2 + N * 0.1)` (rounded)
- **Specific Upgrade:** Double the base upgrade cost.

### 1. Damage & Range
- **Damage (Standard):** Multiplies current damage by **1.15x** per level. Milestone: **1.25x** multiplier.
- **Damage (Chicken Rice):** +6.0 damage per level. Milestone: **1.25x** multiplier.
- **Range:** +0.5 hexes per level. Milestone: **1.25x** multiplier.

### 2. Fire Rate
- **Fire Rate (Standard):** Reduces cooldown by 10% of the base fire rate per level. Milestone: **0.75x** multiplier.
- **Fire Rate (Chicken Rice):** Reduces cooldown by **15ms** per level. Milestone: **0.75x** multiplier (Min: 200ms).
- **Fire Rate (Durian):** Reduces cooldown by **50ms** per level. Milestone: **0.75x** multiplier (Min: 1000ms).
- **Fire Rate (Satay):** Reduces cooldown by **25ms** per level. Milestone: **0.75x** multiplier (Min: 750ms).
- **Grab Rate (Tray Return Uncle):** Reduces cooldown by **100ms** per level. Milestone: **0.75x** multiplier (Min: 10s).

### 3. Special Effects
- **AOE Radius (Satay/Durian):** +0.2 units per level. Milestone: **1.25x** multiplier.
- **Slowing Duration (Teh Tarik):** +500ms per level. Milestone: **1.25x** multiplier.
- **Freeze Duration (Ice Kachang):** +100ms per level. Milestone: **1.25x** multiplier.
- **Cleaning Time (Tray Return Uncle):** +100ms per level. Milestone: **1.25x** multiplier (Max: 4s).
- **Boost Percentage (Bak Kut Teh):** +10% per level. Milestone: **1.25x** multiplier.

---

## Selling
- Stalls can be sold for **50% of the total investment** (base cost + all upgrade costs).

---

## Average Stall Comparison Tables

The following tables show the statistics for each stall after receiving a set number of upgrades, with upgrades distributed evenly across the stall's available categories (round-robin). Total cost includes the base price plus the cumulative cost of all upgrades.

### Level 24 Average Stall Comparison
| Stall Type | Damage | Fire Rate (ms) | Shots / Sec | DPS | Range (hexes) | Special / AOE Properties | Total Cost | DPS / $ |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: | :---: |
| **Teh Tarik** | 0 | 200 | 5.00 | 0.00 | 7.0 | 40% Slow (7.0s puddle duration) | $5,370 | 0.0000 |
| **Satay** | 70 | 1350 | 0.74 | 51.85 | 5.5 | AOE Radius: 3.2 (Gas Cloud) | $7,160 | 0.0072 |
| **Chicken Rice** | 58 | 380 | 2.63 | 152.63 | 8.0 | High single-target DPS | $3,580 | 0.0426 |
| **Durian** | 347 | 1700 | 0.59 | 204.12 | 6.0 | AOE Radius: 2.2 | $10,740 | 0.0190 |
| **Ice Kachang** | 0 | 300 | 3.33 | 0.00 | 7.5 | 1.3s Freeze duration | $8,950 | 0.0000 |
| **Tray Return Uncle** | 0 | 10300 | 0.10 | 0.00 | 1.1 | Grabs/Holds customers for 3.95s | $8,950 | 0.0000 |
| **Bak Kut Teh** | 0 | 0 | 0.00 | 0.00 | 1.1 | Boosts adjacent stalls' stats by 337% | $10,740 | 0.0000 |

### Level 36 Average Stall Comparison
| Stall Type | Damage | Fire Rate (ms) | Shots / Sec | DPS | Range (hexes) | Special / AOE Properties | Total Cost | DPS / $ |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: | :---: |
| **Teh Tarik** | 0 | 50 | 20.00 | 0.00 | 11.0 | 40% Slow (11.0s puddle duration) | $11,220 | 0.0000 |
| **Satay** | 107 | 1275 | 0.78 | 83.92 | 7.0 | AOE Radius: 3.8 (Gas Cloud) | $14,960 | 0.0056 |
| **Chicken Rice** | 100 | 233 | 4.29 | 429.18 | 12.3 | High single-target DPS | $7,480 | 0.0574 |
| **Durian** | 528 | 1550 | 0.65 | 340.65 | 7.5 | AOE Radius: 2.8 | $22,440 | 0.0152 |
| **Ice Kachang** | 0 | 50 | 20.00 | 0.00 | 11.6 | 2.1s Freeze duration | $18,700 | 0.0000 |
| **Tray Return Uncle** | 0 | 10000 | 0.10 | 0.00 | 1.1 | Grabs/Holds customers for 4.00s | $18,700 | 0.0000 |
| **Bak Kut Teh** | 0 | 0 | 0.00 | 0.00 | 1.1 | Boosts adjacent stalls' stats by 557% | $22,440 | 0.0000 |

### Level 48 Average Stall Comparison
| Stall Type | Damage | Fire Rate (ms) | Shots / Sec | DPS | Range (hexes) | Special / AOE Properties | Total Cost | DPS / $ |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: | :---: |
| **Teh Tarik** | 0 | 50 | 20.00 | 0.00 | 13.0 | 40% Slow (13.0s puddle duration) | $19,230 | 0.0000 |
| **Satay** | 204 | 888 | 1.13 | 229.73 | 10.4 | AOE Radius: 5.4 (Gas Cloud) | $25,640 | 0.0090 |
| **Chicken Rice** | 124 | 200 | 5.00 | 620.00 | 14.3 | High single-target DPS | $12,820 | 0.0484 |
| **Durian** | 1004 | 1025 | 0.98 | 979.51 | 11.0 | AOE Radius: 4.2 | $38,460 | 0.0255 |
| **Ice Kachang** | 0 | 50 | 20.00 | 0.00 | 13.6 | 2.5s Freeze duration | $32,050 | 0.0000 |
| **Tray Return Uncle** | 0 | 10000 | 0.10 | 0.00 | 1.1 | Grabs/Holds customers for 4.00s | $32,050 | 0.0000 |
| **Bak Kut Teh** | 0 | 0 | 0.00 | 0.00 | 1.1 | Boosts adjacent stalls' stats by 825% | $38,460 | 0.0000 |

*Note: DPS is calculated for single-target impact. AOE stalls (Satay/Durian) deal damage to all enemies within their radius. Total Cost includes base price and the cumulative cost of upgrades.*

### Upgrade Derivation (How these numbers were reached)

To ensure a fair "average" comparison, upgrades were distributed across the available categories for each stall as follows:

- **Standard Distribution:** Upgrades were allocated evenly across the available categories (e.g., 3 categories for Chicken Rice means each gets 1/3 of the total levels).
- **Milestone Boosts:** Every 10th level in a specific stat applies a multiplier (1.25x for most stats, 0.75x for Fire Rate).
- **Total Investment:** Calculated as `Base Cost + Sum(Base Cost * (0.2 + Index * 0.1))`.
