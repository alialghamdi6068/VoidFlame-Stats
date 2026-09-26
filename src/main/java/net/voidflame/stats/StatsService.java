package net.voidflame.stats;

import net.voidflame.core.api.MatchResultService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

public final class StatsService implements MatchResultService {
    public record PlayerStats(long wins, long losses, long kills, long deaths, long streak, double elo) {}
    public record RankedPlayer(UUID uuid, String name, PlayerStats stats) {}

    private final VoidFlameStatsPlugin plugin;
    private final ConcurrentHashMap<UUID, PlayerStats> cache = new ConcurrentHashMap<>();

    public StatsService(VoidFlameStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerStats getCached(UUID uuid) {
        return cache.getOrDefault(uuid, new PlayerStats(0, 0, 0, 0, 0, 1000));
    }

    public void load(UUID uuid) {
        plugin.get("player:" + uuid).thenAccept(value -> {
            if (value == null || value.isBlank()) return;
            String[] p = value.split(",");
            if (p.length != 6) return;
            try {
                cache.put(uuid, new PlayerStats(
                        Long.parseLong(p[0]), Long.parseLong(p[1]), Long.parseLong(p[2]),
                        Long.parseLong(p[3]), Long.parseLong(p[4]), Double.parseDouble(p[5])
                ));
            } catch (NumberFormatException ignored) {
            }
        });
    }

    public void set(UUID uuid, PlayerStats stats) {
        cache.put(uuid, stats);
        plugin.put("player:" + uuid, encode(stats));
    }

    public void recordWin(UUID uuid, boolean kill) {
        PlayerStats old = getCached(uuid);
        set(uuid, new PlayerStats(
                old.wins() + 1, old.losses(), old.kills() + (kill ? 1 : 0),
                old.deaths(), old.streak() + 1, old.elo() + 15
        ));
    }

    public void recordLoss(UUID uuid) {
        PlayerStats old = getCached(uuid);
        set(uuid, new PlayerStats(
                old.wins(), old.losses() + 1, old.kills(), old.deaths() + 1,
                0, Math.max(0, old.elo() - 15)
        ));
    }

    public void recordDraw(UUID uuid) {
        PlayerStats old = getCached(uuid);
        set(uuid, new PlayerStats(
                old.wins(), old.losses(), old.kills(), old.deaths(),
                old.streak(), old.elo()
        ));
    }

    @Override
    public void record(MatchResultService.MatchResult result) {
        recordMatch(result.winner(), result.loser());
        String timestamp = Long.toString(System.currentTimeMillis());
        String winner = result.winner() == null ? "" : result.winner().toString();
        String loser = result.loser() == null ? "" : result.loser().toString();
        String base = timestamp + ":" + result.matchId();
        if (result.playerA() != null) plugin.put("history:" + result.playerA(), base,
                encodeHistory(result, result.playerA(), winner, loser));
        if (result.playerB() != null) plugin.put("history:" + result.playerB(), base,
                encodeHistory(result, result.playerB(), winner, loser));
        if (result.kit() != null) {
            if (result.winner() != null) plugin.put("kit:" + result.winner() + ":" + result.kit(),
                    "wins", Long.toString(getCached(result.winner()).wins()));
            if (result.loser() != null) plugin.put("kit:" + result.loser() + ":" + result.kit(),
                    "losses", Long.toString(getCached(result.loser()).losses()));
        }
    }

    private String encodeHistory(MatchResultService.MatchResult result, UUID player, String winner, String loser) {
        String outcome = player.equals(result.winner()) ? "WIN" : player.equals(result.loser()) ? "LOSS" : "DRAW";
        return outcome + "|" + result.kit() + "|" + result.mode() + "|" + result.arena() + "|" + result.durationMs();
    }

    public double winLossRatio(UUID uuid) {
        PlayerStats s = getCached(uuid);
        return s.losses() == 0 ? s.wins() : (double) s.wins() / s.losses();
    }

    public void recordMatch(UUID winner, UUID loser) {
        if (winner == null) {
            if (loser != null) recordDraw(loser);
            return;
        }
        if (loser == null) {
            recordWin(winner, false);
            return;
        }
        recordWin(winner, true);
        recordLoss(loser);
    }

    public CompletableFuture<List<RankedPlayer>> top(int limit) {
        return plugin.queryTop(Math.max(1, Math.min(50, limit)));
    }

    private static String encode(PlayerStats s) {
        return s.wins() + "," + s.losses() + "," + s.kills() + "," +
                s.deaths() + "," + s.streak() + "," + s.elo();
    }
}
