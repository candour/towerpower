# Hawker Rush Customer Statistics

This document provides a breakdown of the base statistics, scaling, and special behaviors for all customers (enemies) in Hawker Rush.

## Customer Statistics Table

| Customer Type | Base HP | Base Speed | Reward | Special Behavior / Role |
| :--- |:-------:| :---: | :---: | :--- |
| **Salaryman** |   45    | 0.08 | $10 | Fast-paced, low health. |
| **Tourist** |   90    | 0.04 | $20 | Stops for 2s every 8s to take pictures. |
| **Auntie** |   135   | 0.03 | $30 | Tanky, slow-moving veteran. |
| **Delivery Rider** |   270   | 0.06 | $60 | Boss. Highly affected by puddles (0.2x speed multiplier). |
| **Tiger Mom** |   60    | 0.05 | $40 | Support. Buffs nearby allies with 90% armor. (Max 1 on board). |

## Health Scaling

Customer Health (HP) increases exponentially with each wave to increase difficulty. The formula used is:

**Current HP = Base HP × 1.07<sup>(Wave - 1)</sup>**

*Example (Auntie at Wave 10): 150 × 1.07<sup>9</sup> ≈ 354 HP*

## Customer Behaviors

### Salaryman
The standard fast customer. They don't have much health but can quickly overwhelm your defenses if not handled.

### Tourist
Stops periodically to take photos. While they have more HP than Salarymen, their frequent stops make them vulnerable to sustained fire.

### Auntie
The primary "tank" customer. Slow and deliberate, requiring concentrated damage to take down.

### Delivery Rider (Boss)
Appears as a boss. They have massive health pools. While normally fast, they are very cautious on wet surfaces; if they hit a Teh Tarik puddle, they slow down much more than other customers.

### Tiger Mom
A unique support customer that appears in later waves. She will occasionally stop and lecture nearby customers, granting them a powerful **ARMOR** buff that reduces incoming damage by 90%. This buff is removed if she is fed (defeated) or grabbed by a Tray Return Uncle.
