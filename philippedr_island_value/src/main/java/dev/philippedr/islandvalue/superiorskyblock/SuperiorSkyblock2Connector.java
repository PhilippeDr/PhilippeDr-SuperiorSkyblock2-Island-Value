package dev.philippedr.islandvalue.superiorskyblock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SuperiorSkyblock2Connector {

    private static final String PLUGIN_NAME = "SuperiorSkyblock2";
    private static final String API_CLASS = "com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI";
    private static final String KEY_CLASS = "com.bgsoftware.superiorskyblock.api.key.Key";
    private static final String ISLAND_CLASS = "com.bgsoftware.superiorskyblock.api.island.Island";
    private static final String SORTING_TYPE_CLASS = "com.bgsoftware.superiorskyblock.api.island.SortingType";

    public boolean isAvailable() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled()) return false;

        try {
            Class.forName(API_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public Object getIslandAt(Location location) {
        if (location == null) return null;
        try {
            Class<?> api = Class.forName(API_CLASS);
            Method getIslandAt = api.getMethod("getIslandAt", Location.class);
            return getIslandAt.invoke(null, location);
        } catch (Throwable t) {
            return null;
        }
    }

    public Object getIslandByUuid(UUID islandUuid) {
        if (islandUuid == null) return null;
        try {
            Class<?> api = Class.forName(API_CLASS);
            Method getIslandByUUID = api.getMethod("getIslandByUUID", UUID.class);
            return getIslandByUUID.invoke(null, islandUuid);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Returns the island's 1-based rank on the worth leaderboard, or null if unavailable.
     */
    public Integer getIslandWorthRank(UUID islandUuid) {
        if (islandUuid == null) return null;
        return getIslandWorthRank(getIslandByUuid(islandUuid));
    }

    /**
     * Returns the island's 1-based rank on the worth leaderboard, or null if unavailable.
     */
    public Integer getIslandWorthRank(Object island) {
        if (island == null) return null;

        try {
            Class<?> api = Class.forName(API_CLASS);
            Method getGrid = api.getMethod("getGrid");
            Object grid = getGrid.invoke(null);
            if (grid == null) return null;

            Class<?> sortingTypeClass = Class.forName(SORTING_TYPE_CLASS);
            Method getByName = sortingTypeClass.getMethod("getByName", String.class);
            Object worthSorting = getByName.invoke(null, "WORTH");
            if (worthSorting == null) return null;

            Class<?> islandType = Class.forName(ISLAND_CLASS);
            Method getIslandPosition = grid.getClass().getMethod("getIslandPosition", islandType, sortingTypeClass);
            Object posObj = getIslandPosition.invoke(grid, island, worthSorting);
            if (!(posObj instanceof Integer pos)) return null;
            if (pos < 0) return null;
            return pos + 1;
        } catch (Throwable t) {
            return null;
        }
    }

    public UUID getIslandUuid(Object island) {
        if (island == null) return null;
        try {
            Method getUniqueId = island.getClass().getMethod("getUniqueId");
            Object uuid = getUniqueId.invoke(island);
            return (uuid instanceof UUID u) ? u : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public String getIslandOwnerName(Object island) {
        if (island == null) return null;
        try {
            Method getOwner = island.getClass().getMethod("getOwner");
            Object owner = getOwner.invoke(island);
            if (owner == null) return null;

            // SuperiorPlayer usually has getName().
            Method getName = owner.getClass().getMethod("getName");
            Object name = getName.invoke(owner);
            return name == null ? null : name.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    public Location getIslandHomeForWorld(Object island, World world) {
        if (island == null || world == null) return null;
        try {
            Method getHomes = island.getClass().getMethod("getIslandHomesAsDimensions");
            Object homesObj = getHomes.invoke(island);
            if (!(homesObj instanceof Map<?, ?> homes)) return null;

            for (Object locObj : homes.values()) {
                if (!(locObj instanceof Location loc)) continue;
                if (loc.getWorld() == null) continue;
                if (Objects.equals(loc.getWorld().getUID(), world.getUID())) return loc;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    public BigDecimal getIslandWorth(Object island) {
        if (island == null) return null;
        try {
            Method getWorth = island.getClass().getMethod("getWorth");
            Object worth = getWorth.invoke(island);
            return (worth instanceof BigDecimal bd) ? bd : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public BigDecimal getIslandRawWorth(Object island) {
        if (island == null) return null;
        try {
            Method getRawWorth = island.getClass().getMethod("getRawWorth");
            Object rawWorth = getRawWorth.invoke(island);
            return (rawWorth instanceof BigDecimal bd) ? bd : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public BigDecimal getIslandBonusWorth(Object island) {
        if (island == null) return null;
        try {
            Method getBonusWorth = island.getClass().getMethod("getBonusWorth");
            Object bonusWorth = getBonusWorth.invoke(island);
            return (bonusWorth instanceof BigDecimal bd) ? bd : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public BigDecimal getIslandRawWorth(UUID playerUuid) {
        return getIslandRawWorth(getIslandForPlayer(playerUuid));
    }

    public BigDecimal getIslandBonusWorth(UUID playerUuid) {
        return getIslandBonusWorth(getIslandForPlayer(playerUuid));
    }

    /**
     * Returns island worth for the given player UUID, or null if not available / no island.
     */
    public BigDecimal getIslandWorth(UUID playerUuid) {
        return getIslandWorth(getIslandForPlayer(playerUuid));
    }

    /**
     * Returns the island's block counts map (Key -> BigInteger count), or empty map if unavailable.
     */
    @SuppressWarnings("unchecked")
    public Map<Object, BigInteger> getIslandBlockCounts(UUID playerUuid) {
        return getIslandBlockCounts(getIslandForPlayer(playerUuid));
    }

    /**
     * Returns the island's block counts map (Key -> BigInteger count), or empty map if unavailable.
     */
    @SuppressWarnings("unchecked")
    public Map<Object, BigInteger> getIslandBlockCounts(Object island) {
        if (island == null) return Collections.emptyMap();

        try {
            Method getBlockCounts = island.getClass().getMethod("getBlockCountsAsBigInteger");
            Object blockCounts = getBlockCounts.invoke(island);
            if (blockCounts instanceof Map<?, ?> m) {
                return (Map<Object, BigInteger>) m;
            }
            return Collections.emptyMap();
        } catch (Throwable t) {
            return Collections.emptyMap();
        }
    }

    public BigDecimal getBlockWorth(Object key) {
        if (key == null) return null;

        try {
            Class<?> api = Class.forName(API_CLASS);
            Method getBlockValues = api.getMethod("getBlockValues");
            Object blockValuesManager = getBlockValues.invoke(null);
            if (blockValuesManager == null) return null;

            Class<?> keyType = Class.forName(KEY_CLASS);
            Method getBlockWorth = blockValuesManager.getClass().getMethod("getBlockWorth", keyType);
            Object worth = getBlockWorth.invoke(blockValuesManager, key);
            return (worth instanceof BigDecimal bd) ? bd : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public String getKeyGlobalPart(Object key) {
        if (key == null) return "";
        try {
            Method getGlobalKey = key.getClass().getMethod("getGlobalKey");
            Object global = getGlobalKey.invoke(key);
            return global == null ? "" : global.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    public String getKeySubPart(Object key) {
        if (key == null) return "";
        try {
            Method getSubKey = key.getClass().getMethod("getSubKey");
            Object sub = getSubKey.invoke(key);
            return sub == null ? "" : sub.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    public String formatKey(Object key) {
        if (key == null) return "unknown";

        try {
            Object global = getKeyGlobalPart(key);
            Object sub = getKeySubPart(key);
            String g = global == null ? "" : global.toString();
            String s = sub == null ? "" : sub.toString();
            if (s.isEmpty()) return g;
            if (g.isEmpty()) return s;
            String combined = g + ":" + s;
            return combined.toUpperCase(Locale.ENGLISH);
        } catch (Throwable t) {
            return key.toString();
        }
    }

    private Object getIslandForPlayer(UUID playerUuid) {
        try {
            Class<?> api = Class.forName(API_CLASS);
            Method getPlayer = api.getMethod("getPlayer", UUID.class);
            Object superiorPlayer = getPlayer.invoke(null, playerUuid);
            if (superiorPlayer == null) return null;

            Method getIsland = superiorPlayer.getClass().getMethod("getIsland");
            return getIsland.invoke(superiorPlayer);
        } catch (Throwable t) {
            return null;
        }
    }
}
