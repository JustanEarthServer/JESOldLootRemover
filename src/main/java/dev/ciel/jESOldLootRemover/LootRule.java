package dev.ciel.jESOldLootRemover;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

record LootRule(Map<Material, Integer> max, Map<Material, Integer> chance) {

    static final LootRule NONE = new LootRule(Map.of(), Map.of());

    private static final Map<String, Material> MAX_KEYS = Map.of(
            "netherite-scrap-max", Material.NETHERITE_SCRAP,
            "netherite-ingot-max", Material.NETHERITE_INGOT,
            "gold-ingot-max", Material.GOLD_INGOT,
            "gold-block-max", Material.GOLD_BLOCK);

    private static final Map<String, Material> CHANCE_KEYS = Map.of(
            "ancient-debris-chance", Material.ANCIENT_DEBRIS,
            "upgrade-template-chance", Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);

    static LootRule read(ConfigurationSection section, LootRule parent) {
        Map<Material, Integer> max = new EnumMap<>(Material.class);
        Map<Material, Integer> chance = new EnumMap<>(Material.class);
        max.putAll(parent.max);
        chance.putAll(parent.chance);
        if (section != null) {
            MAX_KEYS.forEach((key, type) -> {
                if (section.isInt(key)) max.put(type, section.getInt(key));
            });
            CHANCE_KEYS.forEach((key, type) -> {
                if (section.isInt(key) || section.isDouble(key)) chance.put(type, (int) Math.round(section.getDouble(key) * 100));
            });
        }
        return new LootRule(max, chance);
    }

    boolean apply(List<ItemStack> loot) {
        boolean changed = false;
        Map<Material, Integer> used = new EnumMap<>(Material.class);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        ListIterator<ItemStack> it = loot.listIterator();
        while (it.hasNext()) {
            ItemStack item = it.next();
            if (item == null) continue;
            Material type = item.getType();
            int amount = item.getAmount();

            Integer odds = chance.get(type);
            if (odds != null && odds < 10_000) {
                int kept = 0;
                for (int i = 0; i < amount; i++) {
                    if (random.nextInt(10_000) < odds) kept++;
                }
                amount = kept;
            }
            Integer limit = max.get(type);
            if (limit != null && limit >= 0) {
                int taken = used.getOrDefault(type, 0);
                amount = Math.min(amount, Math.max(0, limit - taken));
                used.put(type, taken + amount);
            }

            if (amount == item.getAmount()) continue;
            changed = true;
            if (amount <= 0) {
                it.remove();
            } else {
                item.setAmount(amount);
            }
        }
        return changed;
    }
}
