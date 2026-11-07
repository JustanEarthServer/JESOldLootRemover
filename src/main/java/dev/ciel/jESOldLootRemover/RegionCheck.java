package dev.ciel.jESOldLootRemover;

import org.bukkit.Location;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

final class RegionCheck {

    private final Object container;
    private final Method adapt;
    private final Method createQuery;
    private final Method applicableRegions;
    private final Method getId;

    private RegionCheck(Object container, Method adapt, Method createQuery, Method applicableRegions, Method getId) {
        this.container = container;
        this.adapt = adapt;
        this.createQuery = createQuery;
        this.applicableRegions = applicableRegions;
        this.getId = getId;
    }

    static RegionCheck create(ClassLoader loader, Logger logger) {
        try {
            Class<?> worldGuard = Class.forName("com.sk89q.worldguard.WorldGuard", true, loader);
            Method getPlatform = worldGuard.getMethod("getPlatform");
            Method getContainer = getPlatform.getReturnType().getMethod("getRegionContainer");
            Object container = getContainer.invoke(getPlatform.invoke(worldGuard.getMethod("getInstance").invoke(null)));
            Method createQuery = getContainer.getReturnType().getMethod("createQuery");
            Class<?> weLocation = Class.forName("com.sk89q.worldedit.util.Location", true, loader);
            Method adapt = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, loader).getMethod("adapt", Location.class);
            Method applicable = createQuery.getReturnType().getMethod("getApplicableRegions", weLocation);
            Method getId = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion", true, loader).getMethod("getId");
            return new RegionCheck(container, adapt, createQuery, applicable, getId);
        } catch (ReflectiveOperationException | LinkageError e) {
            logger.warning("WorldGuard hook failed, ignored-regions will not work: " + e);
            return null;
        }
    }

    boolean inAny(Location location, Set<String> regions) {
        if (regions.isEmpty()) return false;
        try {
            Object query = createQuery.invoke(container);
            Object set = applicableRegions.invoke(query, adapt.invoke(null, location));
            for (Object region : (Iterable<?>) set) {
                if (regions.contains(((String) getId.invoke(region)).toLowerCase(Locale.ROOT))) return true;
            }
            return false;
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }
}
