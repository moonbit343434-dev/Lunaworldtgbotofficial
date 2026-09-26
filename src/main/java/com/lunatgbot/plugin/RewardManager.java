package com.lunatgbot.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Управляет системой наград с шансами из config.yml
 */
public class RewardManager {

    private final LunaTGBot plugin;
    private final List<RewardEntry> rewards = new ArrayList<>();
    private int totalWeight = 0;
    private final Random random = new Random();

    public RewardManager(LunaTGBot plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        rewards.clear();
        totalWeight = 0;

        List<?> list = plugin.getConfig().getList("rewards");
        if (list == null || list.isEmpty()) {
            // Дефолтные награды если конфиг пустой
            rewards.add(new RewardEntry(40, 500));
            rewards.add(new RewardEntry(25, 899));
            rewards.add(new RewardEntry(20, 1000));
            rewards.add(new RewardEntry(10, 1400));
            rewards.add(new RewardEntry(5,  5000));
        } else {
            for (Object obj : list) {
                if (obj instanceof java.util.Map) {
                    java.util.Map<?, ?> map = (java.util.Map<?, ?>) obj;
                    int chance = 10;
                    double amount = 100;
                    if (map.get("chance") instanceof Number)
                        chance = ((Number) map.get("chance")).intValue();
                    if (map.get("amount") instanceof Number)
                        amount = ((Number) map.get("amount")).doubleValue();
                    rewards.add(new RewardEntry(chance, amount));
                }
            }
        }

        for (RewardEntry e : rewards) totalWeight += e.chance;
        plugin.getLogger().info("Загружено " + rewards.size() + " вариантов награды (total weight=" + totalWeight + ")");
    }

    /** Возвращает случайную награду согласно шансам */
    public double rollReward() {
        if (rewards.isEmpty()) return 100;
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (RewardEntry e : rewards) {
            cumulative += e.chance;
            if (roll < cumulative) return e.amount;
        }
        return rewards.get(rewards.size() - 1).amount;
    }

    public int getTotalVariants() { return rewards.size(); }

    public static class RewardEntry {
        public final int chance;
        public final double amount;
        public RewardEntry(int chance, double amount) {
            this.chance = chance;
            this.amount = amount;
        }
    }
}
