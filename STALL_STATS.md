# Hawker Rush Stall Statistics

This document provides a breakdown of the base statistics and upgrade scaling for all stalls in Hawker Rush.

## Stall Statistics Breakdown

| Stall Type | Base Damage | Fire Rate (ms) | Shots / Sec | Base DPS | Range (hexes) | Special / AOE Properties | Base Cost |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: |
| **Chicken Rice** | 10 | 500 | 2.00 | 20.00 | 4.0 | High single-target DPS | $100 |
| **Teh Tarik** | 0 | 1000 | 1.00 | 0.00 | 3.0 | 40% Slow (3s puddle duration) | $150 |
| **Satay** | 30 | 1500 | 0.67 | 20.00 | 2.5 | AOE Radius: 1.0 (Gas Cloud) | $200 |
| **Ice Kachang** | 0 | 1500 | 0.67 | 0.00 | 3.5 | 0.5s Freeze duration | $250 |
| **Durian** | 150 | 2000 | 0.50 | 75.00 | 3.0 | AOE Radius: 1.0 | $300 |
| **Tray Return Uncle** | 0 | 15000 | 0.07 | 0.00 | 1.1 | Grabs/Holds customers for 2s | $450 |
| **ATM** | 0 | N/A | 0.00 | 0.00 | 0.0 | Provides $100 every wave | $1000 |

---

## Targeting Options

Stalls can be configured to target customers using different strategies. You can cycle through these modes by selecting a placed stall.

- **FIRST:** Targets the customer that is furthest along their path (closest to the goal).
- **CLOSEST:** Targets the customer that is physically closest to the stall.
- **STRONGEST:** Targets the customer with the highest current health.
- **WEAKEST:** Targets the customer with the lowest current health.

---

## Upgrade Scaling

Upgrades are chosen randomly from three categories when an upgrade is purchased. Scaling is primarily **multiplicative** for damage and **additive** for other stats.

### Upgrade Cost
The cost of each upgrade increases linearly based on the stall's base price:
- **1st Upgrade:** 30% of base price
- **2nd Upgrade:** 40% of base price
- **3rd Upgrade:** 50% of base price
- (Increase by 10% for each subsequent upgrade)

### 1. Damage & Range
- **Damage:** Multiplies current damage by **1.15x** per level (rounded).
- **Range:** +0.5 hexes per level.

### 2. Fire Rate
- **Fire Rate (Standard):** Reduces cooldown by 10% of the base fire rate per level (minimum cooldown: 50ms).
- **Fire Rate (Chicken Rice):** Reduces cooldown by **15ms** per level (minimum cooldown: 200ms).
- **Fire Rate (Durian):** Reduces cooldown by **50ms** per level (minimum cooldown: 1000ms).
- **Fire Rate (Satay):** Reduces cooldown by **25ms** per level (minimum cooldown: 750ms).
- **Grab Rate (Tray Return Uncle):** Reduces cooldown by 100ms per level (minimum cooldown: 10s).

### 3. Special Effects
- **AOE Radius (Satay/Durian):** +0.2 units per level.
- **Slowing Duration (Teh Tarik):** +500ms per level.
- **Freeze Duration (Ice Kachang):** +100ms per level.
- **Cleaning Time (Tray Return Uncle):** +100ms per level (maximum duration: 4s).

---

## Selling
- Stalls can be sold for **50% of the total investment** (base cost + all upgrade costs).

---

## Average Stall Comparison Tables

The following tables show the statistics for each stall after receiving a set number of upgrades, with upgrades distributed evenly across the stall's available categories (round-robin). Total cost includes the base price plus the cumulative cost of all upgrades.

### Level 24 Average Stall Comparison
| Stall Type | Damage | Fire Rate (ms) | Shots / Sec | DPS | Range (hexes) | Special / AOE Properties | Total Cost | DPS / $ |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: | :---: |
| **Chicken Rice** | 71 | 380 | 2.63 | 186.84 | 6.0 | High single-target DPS | $3,580 | 0.0522 |
| **Teh Tarik** | 0 | 200 | 5.00 | 0.00 | 7.0 | 40% Slow (7s puddle duration) | $5,370 | 0.0000 |
| **Satay** | 53 | 1300 | 0.77 | 40.77 | 4.5 | AOE Radius: 2.6 (Gas Cloud) | $7,160 | 0.0057 |
| **Ice Kachang** | 0 | 300 | 3.33 | 0.00 | 7.5 | 1.3s Freeze duration | $8,950 | 0.0000 |
| **Durian** | 263 | 1600 | 0.63 | 164.38 | 5.0 | AOE Radius: 2.6 | $10,740 | 0.0153 |
| **Tray Return Uncle** | 0 | 14200 | 0.07 | 0.00 | 5.1 | Grabs/Holds customers for 2.80s | $16,110 | 0.0000 |

### Level 36 Average Stall Comparison
| Stall Type | Damage | Fire Rate (ms) | Shots / Sec | DPS | Range (hexes) | Special / AOE Properties | Total Cost | DPS / $ |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: | :---: |
| **Chicken Rice** | 164 | 233 | 4.29 | 703.86 | 7.0 | High single-target DPS | $7,480 | 0.0941 |
| **Teh Tarik** | 0 | 50 | 20.00 | 0.00 | 11.0 | 40% Slow (11s puddle duration) | $11,220 | 0.0000 |
| **Satay** | 70 | 888 | 1.13 | 78.83 | 5.5 | AOE Radius: 4.4 (Gas Cloud) | $14,960 | 0.0053 |
| **Ice Kachang** | 0 | 50 | 20.00 | 0.00 | 11.6 | 2.1s Freeze duration | $18,700 | 0.0000 |
| **Durian** | 347 | 1025 | 0.98 | 338.54 | 6.0 | AOE Radius: 4.4 | $22,440 | 0.0151 |
| **Tray Return Uncle** | 0 | 10300 | 0.10 | 0.00 | 8.6 | Grabs/Holds customers for 3.95s | $33,660 | 0.0000 |

### Level 48 Average Stall Comparison
| Stall Type | Damage | Fire Rate (ms) | Shots / Sec | DPS | Range (hexes) | Special / AOE Properties | Total Cost | DPS / $ |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: | :---: |
| **Chicken Rice** | 475 | 200 | 5.00 | 2375.00 | 8.0 | High single-target DPS | $12,820 | 0.1853 |
| **Teh Tarik** | 0 | 50 | 20.00 | 0.00 | 13.0 | 40% Slow (13s puddle duration) | $19,230 | 0.0000 |
| **Satay** | 93 | 788 | 1.27 | 118.02 | 6.5 | AOE Radius: 5.2 (Gas Cloud) | $25,640 | 0.0046 |
| **Ice Kachang** | 0 | 50 | 20.00 | 0.00 | 13.6 | 2.5s Freeze duration | $32,050 | 0.0000 |
| **Durian** | 459 | 1000 | 1.00 | 459.00 | 7.0 | AOE Radius: 5.2 | $38,460 | 0.0119 |
| **Tray Return Uncle** | 0 | 10000 | 0.10 | 0.00 | 10.6 | Grabs/Holds customers for 4.00s | $57,690 | 0.0000 |

*Note: DPS is calculated for single-target impact. AOE stalls (Satay/Durian) deal damage to all enemies within their radius. Total Cost includes base price and the cumulative cost of upgrades.*

### Upgrade Derivation (How these numbers were reached)

To ensure a fair "average" comparison, 24 upgrades were distributed across the available categories for each stall as follows:

- **Standard Distribution:** 8 upgrades were allocated to each of the three categories:
    - **Category 0 (Utility/Basic):** Split 4/4 between Damage and Range (if applicable), or 8 into Range for utility stalls.
    - **Category 1 (Fire Rate):** 8 upgrades into Fire Rate (or Grab Rate).
    - **Category 2 (Specialization):** 8 upgrades into the stall's specific special stat (AOE Radius, Slow/Freeze Duration, or extra Damage for Chicken Rice).
- **Milestone Boosts:** Statistics include the +25% bonus applied at Level 10 for any individual stat that reached or exceeded that level (e.g., Chicken Rice Damage L12, Tray Return Uncle stats L12).
- **Total Investment:** Calculated as `Base Cost + (Base Cost * 34.8)`, reflecting the linear price progression of 24 upgrades.
