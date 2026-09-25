package net.voidflame.stats;

import org.bukkit.plugin.ServicePriority;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StatsService {
    public record PlayerStats(long wins, long losses, long kills, long deaths, long streak, double elo) {}

    private final VoidFlameStatsPlugin plugin;
    private final ConcurrentHashMap<UUID, PlayerStats> cache = new ConcurrentHashMap<>();

    public StatsService(VoidFlameStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerStats getCached(UUID uuid) {
        return cache.getOrDefault(uuid, new PlayerStats(0, 0, 0, 0, 0, 1000));
    }

    public void set(UUID uuid, PlayerStats stats) {
        cache.put(uuid, stats);
        plugin.put("player:" + uuid, encode(stats));
    }

    public void recordWin(UUID uuid, boolean kill) {
        PlayerStats old = getCached(uuid);
        set(uuid, new PlayerStats(old.wins() + 1, old.losses(), old.kills() + (kill ? 1 : 0),
                old.deaths(), old.streak() + 1, old.elo() + 15));
    }

    public void recordLoss(UUID uuid) {
        PlayerStats old = getCached(uuid);
        set(uuid, new PlayerStats(old.wins(), old.losses() + 1, old.kills(), old.deaths() + 1,
                0, Math.max(0, old.elo() - 15)));
    }

    private static String encode(PlayerStats s) {
        return s.wins() + "," + s.losses() + "," + s.kills() + "," + s.deaths() + "," + s.streak() + "," + s.elo();
    }
}
