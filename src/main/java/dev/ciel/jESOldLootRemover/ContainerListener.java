package dev.ciel.jESOldLootRemover;

import net.coreprotect.CoreProtectAPI;
import net.coreprotect.database.Database;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class ContainerListener implements Listener {

    private final JESOldLootRemover plugin;
    private final NamespacedKey safeKey;
    private final Set<Block> pending = new HashSet<>();
    private final Set<Block> ignored = new HashSet<>();

    ContainerListener(JESOldLootRemover plugin) {
        this.plugin = plugin;
        this.safeKey = new NamespacedKey(plugin, "safe");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Block block = blockOf(event.getInventory().getHolder(false));
        if (block == null) return;
        List<Block> unchecked = unchecked(block);
        if (unchecked.isEmpty()) return;
        event.setCancelled(true);
        check(unchecked, player.getUniqueId(), "opened by " + player.getName());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        List<Block> unchecked = unchecked(event.getBlock());
        if (unchecked.isEmpty()) return;
        event.setCancelled(true);
        check(unchecked, null, "broken by " + event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        Block block = blockOf(event.getSource().getHolder(false));
        if (block == null) return;
        List<Block> unchecked = unchecked(block);
        if (unchecked.isEmpty()) return;
        event.setCancelled(true);
        check(unchecked, null, "hopper");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        List<Block> unchecked = unchecked(event.getBlock());
        if (unchecked.isEmpty()) return;
        event.setCancelled(true);
        check(unchecked, null, "dispenser");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filterExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getState(false) instanceof Container) {
            Bukkit.getScheduler().runTask(plugin, () -> markSafe(block));
        }
    }

    private void filterExplosion(List<Block> blocks) {
        List<Block> found = new ArrayList<>();
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            List<Block> unchecked = unchecked(it.next());
            if (unchecked.isEmpty()) continue;
            it.remove();
            for (Block b : unchecked) {
                if (!found.contains(b)) found.add(b);
            }
        }
        if (!found.isEmpty()) check(found, null, "explosion");
    }

    private static Block blockOf(InventoryHolder holder) {
        if (holder instanceof DoubleChest chest && chest.getLeftSide(false) instanceof Chest left) {
            return left.getBlock();
        }
        if (holder instanceof Container container) return container.getBlock();
        return null;
    }

    private List<Block> unchecked(Block block) {
        if (!(block.getState(false) instanceof Container container)) return List.of();
        List<Block> parts = new ArrayList<>(2);
        if (container instanceof Chest chest && chest.getInventory().getHolder(false) instanceof DoubleChest dc
                && dc.getLeftSide(false) instanceof Chest left && dc.getRightSide(false) instanceof Chest right) {
            parts.add(left.getBlock());
            parts.add(right.getBlock());
        } else {
            parts.add(block);
        }
        parts.removeIf(this::isSafe);
        if (parts.isEmpty()) return parts;

        if (ignored.contains(block)) return List.of();
        RegionCheck regions = plugin.regions();
        if (plugin.ignoredWorlds().contains(block.getWorld().getName().toLowerCase(Locale.ROOT))
                || (regions != null && regions.inAny(block.getLocation(), plugin.ignoredRegions()))) {
            if (ignored.size() > 50_000) ignored.clear();
            ignored.add(block);
            return List.of();
        }
        return parts;
    }

    void clearCache() {
        ignored.clear();
    }

    private boolean isSafe(Block block) {
        return block.getState(false) instanceof Container container
                && container.getPersistentDataContainer().has(safeKey, PersistentDataType.BYTE);
    }

    private void markSafe(Block block) {
        if (block.getState(false) instanceof Container container) {
            container.getPersistentDataContainer().set(safeKey, PersistentDataType.BYTE, (byte) 1);
            container.update(true, false);
        }
    }

    private void check(List<Block> blocks, UUID opener, String cause) {
        List<Block> targets = new ArrayList<>(blocks.size());
        List<Material> types = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            if (pending.add(block)) {
                targets.add(block);
                types.add(block.getType());
            }
        }
        if (targets.isEmpty()) return;

        long cutoff = plugin.cutoffMillis();
        CoreProtectAPI api = plugin.coreProtect();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Boolean[] results = new Boolean[targets.size()];
            if (databaseOnline()) {
                for (int i = 0; i < targets.size(); i++) {
                    results[i] = placedAfter(api, targets.get(i), types.get(i), cutoff);
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> finish(targets, types, results, opener, cause));
        });
    }

    private boolean databaseOnline() {
        try (Connection connection = Database.getConnection(false, 1000)) {
            if (connection != null && connection.isValid(2)) return true;
        } catch (Throwable e) {
        }
        plugin.getLogger().warning("CoreProtect database unavailable, skipping container check.");
        return false;
    }

    private static Boolean placedAfter(CoreProtectAPI api, Block block, Material type, long cutoff) {
        long now = System.currentTimeMillis();
        if (cutoff >= now) return false;
        int seconds = (int) Math.min(Integer.MAX_VALUE, (now - cutoff) / 1000L + 1L);
        List<String[]> rows;
        try {
            rows = api.blockLookup(block, seconds);
        } catch (Exception e) {
            return null;
        }
        if (rows == null) return null;
        for (String[] row : rows) {
            CoreProtectAPI.ParseResult result = api.parseResult(row);
            if (result.getActionId() == 1 && result.getType() == type && result.getTimestamp() >= cutoff) {
                return true;
            }
        }
        return false;
    }

    private void finish(List<Block> blocks, List<Material> types, Boolean[] results, UUID opener, String cause) {
        boolean allSafe = true;
        boolean removed = false;
        Block reopen = null;
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (results[i] == null) {
                allSafe = false;
                Bukkit.getScheduler().runTaskLater(plugin, () -> pending.remove(block), 600L);
                continue;
            }
            pending.remove(block);
            if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                    || block.getType() != types.get(i)) {
                allSafe = false;
                continue;
            }
            if (results[i]) {
                markSafe(block);
                reopen = block;
            } else {
                allSafe = false;
                removed = true;
                wipe(block, cause);
            }
        }

        Player player = opener == null ? null : Bukkit.getPlayer(opener);
        if (player == null) return;
        if (removed) {
            player.sendMessage(plugin.message("removed"));
        } else if (allSafe && reopen != null && player.getWorld().equals(reopen.getWorld())
                && player.getLocation().distanceSquared(reopen.getLocation().add(0.5, 0.5, 0.5)) <= 64
                && reopen.getState(false) instanceof Container container) {
            player.openInventory(container.getInventory());
        }
    }

    private void wipe(Block block, String cause) {
        BlockState state = block.getState(false);
        if (state instanceof Chest chest) {
            chest.getBlockInventory().clear();
        } else if (state instanceof Container container) {
            container.getInventory().clear();
        }
        String type = block.getType().name();
        block.setType(Material.AIR, false);

        Component notice = plugin.message("notify",
                "%type%", type,
                "%world%", block.getWorld().getName(),
                "%x%", String.valueOf(block.getX()),
                "%y%", String.valueOf(block.getY()),
                "%z%", String.valueOf(block.getZ()),
                "%cause%", cause);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("oldlootremover.notify")) online.sendMessage(notice);
        }
        Bukkit.getConsoleSender().sendMessage(notice);
    }
}
