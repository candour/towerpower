# Hawker Rush Tower Statistics

This document provides a breakdown of the base statistics and upgrade scaling for all towers (stalls) in Hawker Rush.

## Tower Statistics Breakdown

| Stall Type | Base Damage | Fire Rate (ms) | Shots / Sec | Base DPS | Range (hexes) | Special / AOE Properties | Base Cost |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: |
| **Chicken Rice** | 15 | 700 | 1.43 | 21.43 | 4.0 | High single-target DPS | $100 |
| **Teh Tarik** | 0 | 1000 | 1.00 | 0.00 | 3.0 | 40% Slow (3s puddle duration) | $150 |
| **Satay** | 30 | 1500 | 0.67 | 20.00 | 2.5 | AOE Radius: 1.0 (Gas Cloud) | $200 |
| **Ice Kachang** | 2 | 1500 | 0.67 | 1.33 | 3.5 | 1.5s Freeze duration | $250 |
| **Durian** | 25 | 2000 | 0.50 | 12.50 | 3.0 | AOE Radius: 1.0 | $300 |

*Note: Teh Tarik has a "damage" stat of 10 in the code, but its actual in-game behavior currently deals 0 damage as it only creates slowing puddles.*

---

## Upgrade Scaling Estimates

Upgrades are chosen randomly from three categories when an upgrade is purchased. Each upgrade costs the base price of the tower.

### 1. Damage & Range
- **Damage (Standard):** Increases by approximately **20% + 1**.
- **Damage (Chicken Rice):** Increases by approximately **30% + 2** (specialized).
- **Range:** Increases by **+0.5 hexes**.

### 2. Fire Rate
- **Shots Per Second:** Increases by approximately **11.1%** (achieved by reducing the time between shots by 10%).

### 3. Special Effects
- **AOE Radius (Satay/Durian):** Increases by **+0.2 units**.
- **Slowing Duration (Teh Tarik):** Increases by **+500ms**.
- **Freeze Duration (Ice Kachang):** Increases by **+300ms**.

---

## Selling
- Towers can be sold for **50% of the total investment** (base cost + all upgrade costs).
