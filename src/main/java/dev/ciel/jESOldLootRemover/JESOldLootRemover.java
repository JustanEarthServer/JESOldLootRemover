package dev.ciel.jESOldLootRemover;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class JESOldLootRemover extends JavaPlugin {

    private CoreProtectAPI coreProtect;
    private RegionCheck regions;
    private ContainerListener listener;
    private long cutoffMillis;
    private Set<String> ignoredRegions = Set.of();
    private Set<String> ignoredWorlds = Set.of();

    @Override
    public void onEnable() {
        Plugin cp = getServer().getPluginManager().getPlugin("CoreProtect");
        if (cp == null || !cp.isEnabled() || !(cp instanceof CoreProtect core)
                || !core.getAPI().isEnabled() || core.getAPI().APIVersion() < 10) {
            getLogger().severe("CoreProtect API v10+ not found, disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        coreProtect = core.getAPI();
        if (getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            regions = RegionCheck.create(getClass().getClassLoader(), getLogger());
        }

        saveDefaultConfig();
        loadSettings();
        listener = new ContainerListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void loadSettings() {
        reloadConfig();
        Object raw = getConfig().get("cutoff-date", "2026-11-01");
        String date = String.valueOf(raw);
        try {
            cutoffMillis = raw instanceof Date d
                    ? d.toInstant().atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    : LocalDate.parse(date.trim()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            getLogger().warning("Invalid cutoff-date '" + date + "', using 2026-11-01.");
            cutoffMillis = LocalDate.of(2026, 11, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        ignoredRegions = lower(getConfig().getStringList("ignored-regions"));
        ignoredWorlds = lower(getConfig().getStringList("ignored-worlds"));
    }

    private static Set<String> lower(List<String> list) {
        Set<String> set = new HashSet<>();
        for (String s : list) set.add(s.toLowerCase(Locale.ROOT));
        return set;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            if (listener != null) listener.clearCache();
            sender.sendMessage(message("reloaded"));
            return true;
        }
        return false;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        return args.length == 1 && "reload".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("reload") : List.of();
    }

    Component message(String key, String... replacements) {
        String text = getConfig().getString("messages." + key, "");
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    CoreProtectAPI coreProtect() {
        return coreProtect;
    }

    RegionCheck regions() {
        return regions;
    }

    long cutoffMillis() {
        return cutoffMillis;
    }

    Set<String> ignoredRegions() {
        return ignoredRegions;
    }

    Set<String> ignoredWorlds() {
        return ignoredWorlds;
    }
}
