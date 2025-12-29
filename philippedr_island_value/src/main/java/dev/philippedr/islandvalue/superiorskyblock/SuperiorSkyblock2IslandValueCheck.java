package dev.philippedr.islandvalue.superiorskyblock;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

public final class SuperiorSkyblock2IslandValueCheck {

    private final SuperiorSkyblock2Connector connector;

    public SuperiorSkyblock2IslandValueCheck(SuperiorSkyblock2Connector connector) {
        this.connector = connector;
    }

    public record IslandWorthDetails(BigDecimal worth, BigDecimal rawWorth, BigDecimal bonusWorth) {
    }

    public record BlockWorthLine(String key, BigInteger amount, BigDecimal worthEach, BigDecimal worthTotal) {
    }

    public Optional<BigDecimal> getIslandWorthByUsername(String username) {
        if (!connector.isAvailable()) return Optional.empty();

        UUID uuid = resolveUuid(username);
        if (uuid == null) return Optional.empty();

        return Optional.ofNullable(connector.getIslandWorth(uuid));
    }

    public Optional<IslandWorthDetails> getIslandWorthDetailsByUsername(String username) {
        if (!connector.isAvailable()) return Optional.empty();

        UUID uuid = resolveUuid(username);
        if (uuid == null) return Optional.empty();

        BigDecimal worth = connector.getIslandWorth(uuid);
        if (worth == null) return Optional.empty();

        BigDecimal rawWorth = connector.getIslandRawWorth(uuid);
        BigDecimal bonusWorth = connector.getIslandBonusWorth(uuid);

        return Optional.of(new IslandWorthDetails(worth, rawWorth, bonusWorth));
    }

    public Optional<List<BlockWorthLine>> getTopWorthBlocksByUsername(String username, int limit) {
        if (!connector.isAvailable()) return Optional.empty();

        UUID uuid = resolveUuid(username);
        if (uuid == null) return Optional.empty();

        BigDecimal worthCap = connector.getIslandWorth(uuid);
        if (worthCap == null || worthCap.signum() <= 0) return Optional.of(List.of());

        Map<Object, BigInteger> counts = connector.getIslandBlockCounts(uuid);
        if (counts.isEmpty()) return Optional.of(List.of());

        // SuperiorSkyblock2 keeps additional derived keys (global/limit aggregations) in the counts map.
        // Those are useful for SS2 internals, but they can make a breakdown show entries that aren't
        // actually part of the worth sum for the concrete valued keys.
        //
        // If we have any sub-keys for a given global key, we hide the plain global entry.
        Set<String> globalsWithSubKeys = new HashSet<>();
        for (Map.Entry<Object, BigInteger> entry : counts.entrySet()) {
            Object keyObj = entry.getKey();
            BigInteger amount = entry.getValue();
            if (keyObj == null || amount == null || amount.signum() <= 0) continue;

            String global = connector.getKeyGlobalPart(keyObj);
            String sub = connector.getKeySubPart(keyObj);
            if (global == null || global.isEmpty()) continue;
            if (sub != null && !sub.isEmpty()) {
                globalsWithSubKeys.add(global);
            }
        }

        List<BlockWorthLine> lines = new ArrayList<>();
        for (Map.Entry<Object, BigInteger> entry : counts.entrySet()) {
            Object keyObj = entry.getKey();
            BigInteger amount = entry.getValue();
            if (keyObj == null || amount == null) continue;
            if (amount.signum() <= 0) continue;

            String global = connector.getKeyGlobalPart(keyObj);
            String sub = connector.getKeySubPart(keyObj);
            if (global != null && !global.isEmpty() && (sub == null || sub.isEmpty()) && globalsWithSubKeys.contains(global)) {
                continue;
            }

            BigDecimal worthEach = connector.getBlockWorth(keyObj);
            if (worthEach == null) continue;
            if (worthEach.signum() <= 0) continue;

            BigDecimal amountDec;
            try {
                amountDec = new BigDecimal(amount);
            } catch (NumberFormatException ex) {
                continue;
            }

            BigDecimal worthTotal = worthEach.multiply(amountDec);
            if (worthTotal.signum() <= 0) continue;

            lines.add(new BlockWorthLine(
                    connector.formatKey(keyObj),
                    amount,
                    worthEach,
                    worthTotal
            ));
        }

        lines.sort(
            Comparator.comparing(BlockWorthLine::worthEach).reversed()
                .thenComparing(BlockWorthLine::worthTotal, Comparator.reverseOrder())
        );

        lines = clampToWorthCap(lines, worthCap);
        if (limit > 0 && lines.size() > limit) {
            lines = lines.subList(0, limit);
        }

        return Optional.of(lines);
    }

    private static List<BlockWorthLine> clampToWorthCap(List<BlockWorthLine> sortedLines, BigDecimal worthCap) {
        if (sortedLines == null || sortedLines.isEmpty()) return List.of();
        if (worthCap == null || worthCap.signum() <= 0) return List.of();

        BigDecimal remaining = worthCap;
        List<BlockWorthLine> out = new ArrayList<>();

        for (BlockWorthLine line : sortedLines) {
            if (line == null) continue;
            if (remaining.signum() <= 0) break;

            BigDecimal worthEach = line.worthEach();
            BigDecimal worthTotal = line.worthTotal();
            BigInteger amount = line.amount();
            if (worthEach == null || worthTotal == null || amount == null) continue;
            if (worthEach.signum() <= 0 || worthTotal.signum() <= 0 || amount.signum() <= 0) continue;

            if (worthTotal.compareTo(remaining) <= 0) {
                out.add(line);
                remaining = remaining.subtract(worthTotal);
                continue;
            }

            // Partial fit: show only the portion that can still contribute.
            BigDecimal amountDec = remaining.divide(worthEach, 0, RoundingMode.DOWN);
            BigInteger amountToShow;
            try {
                amountToShow = amountDec.toBigIntegerExact();
            } catch (ArithmeticException ex) {
                amountToShow = amountDec.toBigInteger();
            }

            if (amountToShow.signum() <= 0) {
                continue;
            }
            if (amountToShow.compareTo(amount) > 0) {
                amountToShow = amount;
            }

            BigDecimal worthTotalToShow = worthEach.multiply(new BigDecimal(amountToShow));
            if (worthTotalToShow.signum() <= 0) continue;
            if (worthTotalToShow.compareTo(remaining) > 0) continue;

            out.add(new BlockWorthLine(line.key(), amountToShow, worthEach, worthTotalToShow));
            remaining = remaining.subtract(worthTotalToShow);
        }

        return out;
    }

    public static String formatDecimal(BigDecimal value) {
        if (value == null) return "?";
        BigDecimal normalized = value.stripTrailingZeros();
        // Avoid scientific notation for large/small values.
        String plain = normalized.toPlainString();
        // If it has too many decimals, clamp to 2dp for readability.
        int dot = plain.indexOf('.');
        if (dot >= 0 && plain.length() - dot - 1 > 2) {
            return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }
        return plain;
    }

    private static UUID resolveUuid(String username) {
        Player online = Bukkit.getPlayerExact(username);
        if (online != null) return online.getUniqueId();

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(username);
        if (cached != null && cached.hasPlayedBefore()) return cached.getUniqueId();

        return null;
    }
}
