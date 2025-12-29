package dev.philippedr.islandvalue.hologram;

import dev.philippedr.islandvalue.PhilippeDrIslandValuePlugin;
import dev.philippedr.islandvalue.superiorskyblock.SuperiorSkyblock2Connector;
import dev.philippedr.islandvalue.superiorskyblock.SuperiorSkyblock2IslandValueCheck;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player island value hologram shown at island home + 3 blocks.
 *
 * Implementation notes:
 * - Uses a per-player TextDisplay entity hidden from all other players.
 * - Uses billboard mode to always face the viewer.
 * - Updates are cached per-island and refreshed only for islands with active viewers.
 */
public final class HolographicValue implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final PhilippeDrIslandValuePlugin plugin;
    private final SuperiorSkyblock2Connector connector;

    private final Map<UUID, HoloState> holoByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, IslandCache> cacheByIsland = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyIslands = ConcurrentHashMap.newKeySet();

    private BukkitTask viewerTask;
    private BukkitTask refreshTask;

    public HolographicValue(PhilippeDrIslandValuePlugin plugin, SuperiorSkyblock2Connector connector) {
        this.plugin = plugin;
        this.connector = connector;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerSuperiorWorthHook();

        // Keep holograms created/removed as players move around.
        this.viewerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickViewers, 20L, 20L);
        // Refresh text for islands with active viewers (rate-limited and cached).
        this.refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshActiveIslands, 40L, 40L);
    }

    public void stop() {
        if (viewerTask != null) viewerTask.cancel();
        if (refreshTask != null) refreshTask.cancel();

        for (HoloState state : holoByViewer.values()) {
            destroy(state);
        }
        holoByViewer.clear();
        cacheByIsland.clear();
        dirtyIslands.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Ensure already-existing holograms stay private.
        Player joined = event.getPlayer();
        for (Map.Entry<UUID, HoloState> e : holoByViewer.entrySet()) {
            HoloState state = e.getValue();
            if (state == null || state.display == null) continue;
            if (Objects.equals(joined.getUniqueId(), e.getKey())) continue;
            joined.hideEntity(plugin, state.display);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeViewer(event.getPlayer().getUniqueId());
    }

    private void tickViewers() {
        if (!connector.isAvailable()) {
            // SuperiorSkyblock2 not present; remove any existing holograms.
            for (UUID viewerId : new ArrayList<>(holoByViewer.keySet())) {
                removeViewer(viewerId);
            }
            return;
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updateViewer(viewer);
        }
    }

    private void updateViewer(Player viewer) {
        Object island = connector.getIslandAt(viewer.getLocation());
        if (island == null) {
            removeViewer(viewer.getUniqueId());
            return;
        }

        UUID islandId = connector.getIslandUuid(island);
        if (islandId == null) {
            removeViewer(viewer.getUniqueId());
            return;
        }

        Location home = connector.getIslandHomeForWorld(island, viewer.getWorld());
        if (home == null || home.getWorld() == null) {
            removeViewer(viewer.getUniqueId());
            return;
        }

        Location holoLoc = home.clone().add(0.0, 3.0, 0.0);
        HoloState existing = holoByViewer.get(viewer.getUniqueId());

        if (existing == null || existing.display == null || existing.display.isDead()) {
            TextDisplay display = spawnPrivateDisplay(viewer, holoLoc);
            if (display == null) return;

            HoloState state = new HoloState(viewer.getUniqueId(), islandId, holoLoc, display);
            holoByViewer.put(viewer.getUniqueId(), state);
            dirtyIslands.add(islandId);
            return;
        }

        // Island changed.
        if (!Objects.equals(existing.islandId, islandId)) {
            destroy(existing);
            TextDisplay display = spawnPrivateDisplay(viewer, holoLoc);
            if (display == null) return;

            existing.islandId = islandId;
            existing.baseLocation = holoLoc;
            existing.display = display;
            holoByViewer.put(viewer.getUniqueId(), existing);
            dirtyIslands.add(islandId);
            return;
        }

        // Home location moved.
        if (existing.baseLocation == null || existing.baseLocation.distanceSquared(holoLoc) > 0.25) {
            existing.baseLocation = holoLoc;
            existing.display.teleport(holoLoc);
        }
    }

    private TextDisplay spawnPrivateDisplay(Player viewer, Location holoLoc) {
        World world = holoLoc.getWorld();
        if (world == null) return null;

        TextDisplay display = (TextDisplay) world.spawnEntity(holoLoc, EntityType.TEXT_DISPLAY);

        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.setPersistent(false);
        display.setDefaultBackground(false);
        display.setLineWidth(240);

        display.text(LEGACY.deserialize("\u00a7b\u00a7lISLAND VALUE\n\u00a77Loading..."));

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (Objects.equals(other.getUniqueId(), viewer.getUniqueId())) {
                other.showEntity(plugin, display);
            } else {
                other.hideEntity(plugin, display);
            }
        }

        return display;
    }

    private void refreshActiveIslands() {
        if (!connector.isAvailable()) return;

        // Determine islands that currently have viewers.
        Map<UUID, List<HoloState>> viewersByIsland = new HashMap<>();
        for (HoloState state : holoByViewer.values()) {
            if (state == null || state.display == null || state.display.isDead()) continue;
            viewersByIsland.computeIfAbsent(state.islandId, __ -> new ArrayList<>()).add(state);
        }

        long nowMs = System.currentTimeMillis();

        for (Map.Entry<UUID, List<HoloState>> entry : viewersByIsland.entrySet()) {
            UUID islandId = entry.getKey();

            IslandCache cache = cacheByIsland.get(islandId);
            boolean needsRefresh = cache == null || dirtyIslands.remove(islandId) || (nowMs - cache.lastUpdatedMs) > 10_000;
            if (needsRefresh) {
                cache = buildCacheForIsland(islandId, nowMs);
                if (cache != null) {
                    cacheByIsland.put(islandId, cache);
                }
            }

            if (cache == null) continue;

            Component text = cache.renderedText;
            for (HoloState viewerState : entry.getValue()) {
                if (viewerState.display == null || viewerState.display.isDead()) continue;
                if (!Objects.equals(viewerState.lastText, text)) {
                    viewerState.display.text(text);
                    viewerState.lastText = text;
                }
            }
        }
    }

    private IslandCache buildCacheForIsland(UUID islandId, long nowMs) {
        Object island = connector.getIslandByUuid(islandId);
        if (island == null) return null;

        String ownerName = connector.getIslandOwnerName(island);
        if (ownerName == null || ownerName.isBlank()) ownerName = "Unknown";

        Integer worthRank = connector.getIslandWorthRank(island);

        BigDecimal worth = connector.getIslandWorth(island);

        List<BlockLine> top = computeTopWorthBlocks(island, 5);

        StringBuilder sb = new StringBuilder();
        sb.append("\u00a7b\u00a7lISLAND VALUE\n");
        if (worthRank != null) {
            if (worthRank == 1) {
                sb.append("\u00a76Rank: \u00a7e#1\u00a76 (Top)\n");
            } else {
                sb.append("\u00a76Rank: \u00a7e#").append(worthRank).append("\n");
            }
        }
        sb.append("\u00a77Owner: \u00a7f").append(ownerName).append("\n");
        if (worth != null) {
            sb.append("\u00a77Worth: \u00a7a").append(SuperiorSkyblock2IslandValueCheck.formatDecimal(worth)).append("\n");
        } else {
            sb.append("\u00a77Worth: \u00a7cN/A\n");
        }

        if (!top.isEmpty()) {
            sb.append("\u00a77Top blocks:\n");
            int idx = 1;
            for (BlockLine line : top) {
                boolean isTop = idx == 1;
                sb.append("\u00a78  ").append(idx).append(") ");
                sb.append(isTop ? "\u00a7e\u2605 " : "");
                sb.append("\u00a7f").append(line.key);
                sb.append("\u00a77 x\u00a7f").append(line.amount);
                sb.append("\u00a78  (\u00a7f").append(SuperiorSkyblock2IslandValueCheck.formatDecimal(line.worthEach)).append("\u00a78)");
                sb.append("\u00a77 = \u00a7a").append(SuperiorSkyblock2IslandValueCheck.formatDecimal(line.worthTotal));
                sb.append("\n");
                idx++;
            }
        } else {
            sb.append("\u00a78No block breakdown available\n");
        }

        return new IslandCache(nowMs, LEGACY.deserialize(sb.toString()));
    }

    private List<BlockLine> computeTopWorthBlocks(Object island, int limit) {
        Map<Object, BigInteger> counts = connector.getIslandBlockCounts(island);
        if (counts.isEmpty()) return List.of();

        BigDecimal worthCap = connector.getIslandWorth(island);
        if (worthCap == null || worthCap.signum() <= 0) return List.of();

        // Hide derived global aggregation keys when per-subkey entries exist.
        Set<String> globalsWithSubKeys = new HashSet<>();
        for (Map.Entry<Object, BigInteger> entry : counts.entrySet()) {
            Object keyObj = entry.getKey();
            BigInteger amount = entry.getValue();
            if (keyObj == null || amount == null || amount.signum() <= 0) continue;
            String global = connector.getKeyGlobalPart(keyObj);
            String sub = connector.getKeySubPart(keyObj);
            if (global == null || global.isEmpty()) continue;
            if (sub != null && !sub.isEmpty()) globalsWithSubKeys.add(global);
        }

        List<BlockLine> out = new ArrayList<>();
        for (Map.Entry<Object, BigInteger> entry : counts.entrySet()) {
            Object keyObj = entry.getKey();
            BigInteger amount = entry.getValue();
            if (keyObj == null || amount == null || amount.signum() <= 0) continue;

            String global = connector.getKeyGlobalPart(keyObj);
            String sub = connector.getKeySubPart(keyObj);
            if (global != null && !global.isEmpty() && (sub == null || sub.isEmpty()) && globalsWithSubKeys.contains(global)) {
                continue;
            }

            BigDecimal worthEach = connector.getBlockWorth(keyObj);
            if (worthEach == null || worthEach.signum() <= 0) continue;

            BigDecimal amountDec;
            try {
                amountDec = new BigDecimal(amount);
            } catch (NumberFormatException ex) {
                continue;
            }

            BigDecimal worthTotal = worthEach.multiply(amountDec);
            if (worthTotal.signum() <= 0) continue;

            out.add(new BlockLine(connector.formatKey(keyObj), amount, worthEach, worthTotal));
        }

        out.sort(
            Comparator.comparing((BlockLine b) -> b.worthEach).reversed()
                .thenComparing((BlockLine b) -> b.worthTotal, Comparator.reverseOrder())
        );

        out = clampToWorthCap(out, worthCap);
        if (limit > 0 && out.size() > limit) return out.subList(0, limit);
        return out;
    }

    private static List<BlockLine> clampToWorthCap(List<BlockLine> sortedLines, BigDecimal worthCap) {
        if (sortedLines == null || sortedLines.isEmpty()) return List.of();
        if (worthCap == null || worthCap.signum() <= 0) return List.of();

        BigDecimal remaining = worthCap;
        List<BlockLine> out = new ArrayList<>();

        for (BlockLine line : sortedLines) {
            if (line == null) continue;
            if (remaining.signum() <= 0) break;

            if (line.worthEach == null || line.worthTotal == null || line.amount == null) continue;
            if (line.worthEach.signum() <= 0 || line.worthTotal.signum() <= 0 || line.amount.signum() <= 0) continue;

            if (line.worthTotal.compareTo(remaining) <= 0) {
                out.add(line);
                remaining = remaining.subtract(line.worthTotal);
                continue;
            }

            BigDecimal amountDec = remaining.divide(line.worthEach, 0, RoundingMode.DOWN);
            BigInteger amountToShow;
            try {
                amountToShow = amountDec.toBigIntegerExact();
            } catch (ArithmeticException ex) {
                amountToShow = amountDec.toBigInteger();
            }

            if (amountToShow.signum() <= 0) {
                continue;
            }
            if (amountToShow.compareTo(line.amount) > 0) {
                amountToShow = line.amount;
            }

            BigDecimal worthTotalToShow = line.worthEach.multiply(new BigDecimal(amountToShow));
            if (worthTotalToShow.signum() <= 0) continue;
            if (worthTotalToShow.compareTo(remaining) > 0) continue;

            out.add(new BlockLine(line.key, amountToShow, line.worthEach, worthTotalToShow));
            remaining = remaining.subtract(worthTotalToShow);
        }

        return out;
    }

    private void removeViewer(UUID viewerId) {
        HoloState state = holoByViewer.remove(viewerId);
        if (state != null) destroy(state);
    }

    private void destroy(HoloState state) {
        try {
            if (state.display != null && !state.display.isDead()) {
                state.display.remove();
            }
        } catch (Throwable ignored) {
        }
    }

    private void registerSuperiorWorthHook() {
        // Mark cache dirty on island worth recalculation.
        try {
            Class<?> eventClass = Class.forName("com.bgsoftware.superiorskyblock.api.events.IslandWorthCalculatedEvent");

            Bukkit.getPluginManager().registerEvent(
                    eventClass.asSubclass(Event.class),
                    this,
                    EventPriority.MONITOR,
                    new WorthEventExecutor(),
                    plugin,
                    true
            );
        } catch (Throwable ignored) {
            // SuperiorSkyblock2 not installed or API changed.
        }
    }

    private final class WorthEventExecutor implements EventExecutor {
        @Override
        public void execute(Listener listener, Event event) {
            try {
                // IslandEvent#getIsland
                Object island = event.getClass().getMethod("getIsland").invoke(event);
                UUID islandId = connector.getIslandUuid(island);
                if (islandId != null) dirtyIslands.add(islandId);
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class HoloState {
        final UUID viewerId;
        UUID islandId;
        Location baseLocation;
        TextDisplay display;
        Component lastText;

        HoloState(UUID viewerId, UUID islandId, Location baseLocation, TextDisplay display) {
            this.viewerId = viewerId;
            this.islandId = islandId;
            this.baseLocation = baseLocation;
            this.display = display;
        }
    }

    private record IslandCache(long lastUpdatedMs, Component renderedText) {
    }

    private static final class BlockLine {
        final String key;
        final BigInteger amount;
        final BigDecimal worthEach;
        final BigDecimal worthTotal;

        private BlockLine(String key, BigInteger amount, BigDecimal worthEach, BigDecimal worthTotal) {
            this.key = key;
            this.amount = amount;
            this.worthEach = worthEach;
            this.worthTotal = worthTotal;
        }
    }
}
