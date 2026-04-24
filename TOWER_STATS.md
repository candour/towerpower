# Hawker Rush Tower Statistics

This document provides a breakdown of the base statistics and upgrade scaling for all towers (stalls) in Hawker Rush.

## Tower Statistics Breakdown

| Stall Type | Base Damage | Fire Rate (ms) | Shots / Sec | Base DPS | Range (hexes) | Special / AOE Properties | Base Cost |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: |
| **Chicken Rice** | 15 | 700 | 1.43 | 21.43 | 4.0 | High single-target DPS | $100 |
| **Teh Tarik** | 0 | 1000 | 1.00 | 0.00 | 3.0 | 40% Slow (3s puddle duration) | $150 |
| **Satay** | 20 | 1500 | 0.67 | 13.33 | 2.5 | AOE Radius: 1.0 (Gas Cloud) | $200 |
| **Ice Kachang** | 0 | 1500 | 0.67 | 0.00 | 3.5 | 0.5s Freeze duration | $250 |
| **Durian** | 120 | 2000 | 0.50 | 60.00 | 3.0 | AOE Radius: 1.0 | $300 |
| **Tray Return Uncle** | 0 | 15000 | 0.07 | 0.00 | 1.1 | Grabs/Holds enemies for 2s | $450 |


---

## Upgrade Scaling

Upgrades are chosen randomly from three categories when an upgrade is purchased. Scaling is **additive** based on the tower's base stats.

### Upgrade Cost
The cost of each upgrade increases linearly based on the tower's base price:
- **1st Upgrade:** 30% of base price
- **2nd Upgrade:** 40% of base price
- **3rd Upgrade:** 50% of base price
- (Increase by 10% or 1000 basis points for each subsequent upgrade)

### 1. Damage & Range
- **Damage (Standard):** +20% of base damage + 1 per level.
- **Damage (Chicken Rice):** +30% of base damage + 2 per level.
- **Range:** +0.5 hexes per level.

### 2. Fire Rate
- **Fire Rate:** Reduces cooldown by 10% of the base fire rate per level (minimum cooldown: 100ms).
- **Grab Rate (Tray Return Uncle):** Reduces cooldown by 100ms per level (minimum cooldown: 10s).

### 3. Special Effects
- **AOE Radius (Satay/Durian):** +0.2 units per level.
- **Slowing Duration (Teh Tarik):** +500ms per level.
- **Freeze Duration (Ice Kachang):** +100ms per level.
- **Cleaning Time (Tray Return Uncle):** +100ms per level (maximum duration: 4s).

---

## Selling
- Towers can be sold for **50% of the total investment** (base cost + all upgrade costs).
