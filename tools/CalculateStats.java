
import java.util.*;
import java.lang.Math;

public class CalculateStats {

    enum StallType {
        TEH_TARIK, SATAY, CHICKEN_RICE, DURIAN, ICE_KACHANG, TRAY_RETURN_UNCLE, ATM, BAK_KUT_TEH
    }

    static class StallDef {
        StallType type;
        String name;
        int cost;
        double damage;
        double fireRateMs;
        double range;
        double aoeRadius;
        double durationMs;
        double effectMs;

        StallDef(StallType type, String name, int cost, double damage, double fireRateMs, double range, double aoeRadius, double durationMs, double effectMs) {
            this.type = type;
            this.name = name;
            this.cost = cost;
            this.damage = damage;
            this.fireRateMs = fireRateMs;
            this.range = range;
            this.aoeRadius = aoeRadius;
            this.durationMs = durationMs;
            this.effectMs = effectMs;
        }
    }

    static Map<StallType, StallDef> registry = new HashMap<>();
    static {
        registry.put(StallType.TEH_TARIK, new StallDef(StallType.TEH_TARIK, "Teh Tarik", 150, 0.0, 1000.0, 3.0, 0.0, 3000.0, 0.0));
        registry.put(StallType.SATAY, new StallDef(StallType.SATAY, "Satay", 200, 30.0, 1500.0, 2.5, 2.0, 0.0, 0.0));
        registry.put(StallType.CHICKEN_RICE, new StallDef(StallType.CHICKEN_RICE, "Chicken Rice", 100, 10.0, 500.0, 4.0, 0.0, 0.0, 0.0));
        registry.put(StallType.DURIAN, new StallDef(StallType.DURIAN, "Durian", 300, 150.0, 2000.0, 3.0, 1.0, 0.0, 0.0));
        registry.put(StallType.ICE_KACHANG, new StallDef(StallType.ICE_KACHANG, "Ice Kachang", 250, 0.0, 1500.0, 3.5, 0.0, 0.0, 500.0));
        registry.put(StallType.TRAY_RETURN_UNCLE, new StallDef(StallType.TRAY_RETURN_UNCLE, "Tray Return Uncle", 250, 0.0, 15000.0, 1.1, 0.0, 2000.0, 0.0));
        registry.put(StallType.BAK_KUT_TEH, new StallDef(StallType.BAK_KUT_TEH, "Bak Kut Teh", 300, 20.0, 0.0, 1.1, 0.0, 0.0, 0.0));
    }

    static double calculateValue(String statName, double baseValue, int level, StallType stallType) {
        double current = baseValue;
        StallDef baseStall = registry.get(stallType);

        for (int l = 1; l <= level; l++) {
            boolean isMilestone = l % 10 == 0;
            switch (statName) {
                case "Damage":
                    if (stallType == StallType.CHICKEN_RICE && baseStall.cost == 100) {
                        current += 6.0;
                    } else {
                        current = Math.round(current * 1.15);
                    }
                    if (isMilestone) current = Math.round(current * 1.25);
                    break;
                case "Range":
                    current += 0.5;
                    if (isMilestone) current *= 1.25;
                    current = Math.round(current * 10) / 10.0;
                    break;
                case "Rate":
                    double rateReduction;
                    double floor;
                    switch (stallType) {
                        case TRAY_RETURN_UNCLE: rateReduction = 100.0; floor = 10000.0; break;
                        case CHICKEN_RICE: rateReduction = 15.0; floor = 200.0; break;
                        case DURIAN: rateReduction = 50.0; floor = 1000.0; break;
                        case SATAY: rateReduction = 25.0; floor = 750.0; break;
                        default: rateReduction = baseValue * 0.1; floor = 50.0; break;
                    }

                    if (current > floor) {
                        double potentialRate = current - rateReduction;
                        if (isMilestone) potentialRate = Math.round(potentialRate * 0.75);
                        current = Math.max(floor, potentialRate);
                    }
                    break;
                case "Radius":
                    current += 0.2;
                    if (isMilestone) current *= 1.25;
                    current = Math.round(current * 10) / 10.0;
                    break;
                case "Duration":
                    double increment = (stallType == StallType.TRAY_RETURN_UNCLE) ? 100.0 : 500.0;
                    double cap = (stallType == StallType.TRAY_RETURN_UNCLE) ? 4000.0 : Double.MAX_VALUE;

                    current = Math.min(cap, current + increment);
                    if (isMilestone) current = Math.min(cap, Math.round(current * 1.25));
                    break;
                case "Effect":
                    current += 100.0;
                    if (isMilestone) current = Math.round(current * 1.25);
                    break;
                case "Boost":
                    current += 20.0;
                    if (isMilestone) current = Math.round(current * 1.25);
                    break;
            }
        }
        return current;
    }

    static List<String> getAvailableStats(StallType type) {
        switch (type) {
            case TRAY_RETURN_UNCLE: return Arrays.asList("Rate", "Duration");
            case BAK_KUT_TEH: return Arrays.asList("Boost");
            case TEH_TARIK: return Arrays.asList("Range", "Rate", "Duration");
            case ICE_KACHANG: return Arrays.asList("Range", "Rate", "Effect");
            case SATAY:
            case DURIAN: return Arrays.asList("Damage", "Range", "Rate", "Radius");
            case CHICKEN_RICE: return Arrays.asList("Damage", "Range", "Rate");
            default: return new ArrayList<>();
        }
    }

    static int calculateTotalCost(int baseCost, int upgradeCount) {
        int total = baseCost;
        for (int i = 1; i <= upgradeCount; i++) {
            total += Math.round(baseCost * (0.2f + i * 0.1f));
        }
        return total;
    }

    public static void main(String[] args) {
        int[] levels = {24, 36, 48};
        for (int lvl : levels) {
            System.out.println("### Level " + lvl + " Average Stall Comparison");
            System.out.println("| Stall Type | Damage | Fire Rate (ms) | Shots / Sec | DPS | Range (hexes) | Special / AOE Properties | Total Cost | DPS / $ |");
            System.out.println("| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: | :---: |");

            for (StallType type : StallType.values()) {
                if (type == StallType.ATM) continue;
                StallDef def = registry.get(type);
                List<String> stats = getAvailableStats(type);
                int upgradesPerStat = lvl / stats.size();
                int remainder = lvl % stats.size();

                Map<String, Integer> upgradeMap = new HashMap<>();
                for (int i = 0; i < stats.size(); i++) {
                    upgradeMap.put(stats.get(i), upgradesPerStat + (i < remainder ? 1 : 0));
                }

                double finalDamage = (type == StallType.BAK_KUT_TEH) ? 0.0 : calculateValue("Damage", def.damage, upgradeMap.getOrDefault("Damage", 0), type);
                double finalBoost = (type == StallType.BAK_KUT_TEH) ? calculateValue("Boost", def.damage, upgradeMap.getOrDefault("Boost", 0), type) : 0.0;
                double finalRate = (def.fireRateMs > 0) ? calculateValue("Rate", def.fireRateMs, upgradeMap.getOrDefault("Rate", 0), type) : 0.0;
                double finalRange = calculateValue("Range", def.range, upgradeMap.getOrDefault("Range", 0), type);
                double finalRadius = calculateValue("Radius", def.aoeRadius, upgradeMap.getOrDefault("Radius", 0), type);
                double finalDuration = calculateValue("Duration", def.durationMs, upgradeMap.getOrDefault("Duration", 0), type);
                double finalEffect = calculateValue("Effect", def.effectMs, upgradeMap.getOrDefault("Effect", 0), type);

                double shotsPerSec = (finalRate > 0) ? 1000.0 / finalRate : 0.0;
                double dps = finalDamage * shotsPerSec;
                int totalCost = calculateTotalCost(def.cost, lvl);
                double dpsPerDollar = (totalCost > 0) ? dps / totalCost : 0.0;

                String special = "";
                switch (type) {
                    case TEH_TARIK: special = "40% Slow (" + String.format("%.1f", finalDuration / 1000.0) + "s puddle duration)"; break;
                    case SATAY: special = "AOE Radius: " + String.format("%.1f", finalRadius) + " (Gas Cloud)"; break;
                    case DURIAN: special = "AOE Radius: " + String.format("%.1f", finalRadius); break;
                    case ICE_KACHANG: special = String.format("%.1f", finalEffect / 1000.0) + "s Freeze duration"; break;
                    case TRAY_RETURN_UNCLE: special = "Grabs/Holds customers for " + String.format("%.2f", finalDuration / 1000.0) + "s"; break;
                    case BAK_KUT_TEH: special = "Boosts adjacent stalls' stats by " + (int)finalBoost + "%"; break;
                    case CHICKEN_RICE: special = "High single-target DPS"; break;
                }

                System.out.println(String.format("| **%s** | %d | %d | %.2f | %.2f | %.1f | %s | $%s | %.4f |",
                        def.name, (int)finalDamage, (int)finalRate, shotsPerSec, dps, finalRange, special, String.format("%,d", totalCost), dpsPerDollar));
            }
            System.out.println();
        }
    }
}
