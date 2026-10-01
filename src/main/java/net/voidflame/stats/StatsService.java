package net.voidflame.stats;

import net.voidflame.core.api.MatchResultService;
import net.voidflame.core.api.PartyMatchResultService;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.List;

public final class StatsService implements MatchResultService, PartyMatchResultService {
    public record PlayerStats(long wins, long losses, long kills, long deaths, long streak, long bestStreak, double elo) {}
    public record RankedPlayer(UUID uuid, String name, PlayerStats stats) {}

    private final VoidFlameStatsPlugin plugin;
    private final ConcurrentHashMap<UUID, PlayerStats> cache = new ConcurrentHashMap<>();
    private final Set<UUID> processedMatches = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, long[]> kitStatsCache = new ConcurrentHashMap<>();

    public StatsService(VoidFlameStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerStats getCached(UUID uuid) {
        return cache.getOrDefault(uuid, new PlayerStats(0, 0, 0, 0, 0, 0, plugin.getConfig().getDouble("match-results.initial-elo", 1000.0)));
    }

    public void load(UUID uuid) {
        for (String kit : List.of("sword","axe","uhc","mace","smp","spear_mace","crystal","netherite_pot")) {
            String key = "kitstats:" + uuid + ":" + kit;
            plugin.get(key).thenAccept(raw -> {
                long wins = 0, losses = 0;
                if (raw != null) {
                    String[] parts = raw.split(",", -1);
                    try {
                        if (parts.length == 2) {
                            wins = Math.max(0, Long.parseLong(parts[0]));
                            losses = Math.max(0, Long.parseLong(parts[1]));
                        }
                    } catch (NumberFormatException ignored) {}
                }
                kitStatsCache.put(key, new long[]{wins, losses});
            });
        }

        plugin.get("player:" + uuid).thenAccept(value -> {
            if (value == null || value.isBlank()) return;
            String[] p = value.split(",");
            if (p.length != 6 && p.length != 7) return;
            try {
                cache.put(uuid, new PlayerStats(
                        Long.parseLong(p[0]), Long.parseLong(p[1]), Long.parseLong(p[2]),
                        Long.parseLong(p[3]), Long.parseLong(p[4]), p.length == 7 ? Long.parseLong(p[5]) : Long.parseLong(p[4]), Double.parseDouble(p[p.length == 7 ? 6 : 5])
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
        Object lock = playerLocks.computeIfAbsent(uuid, ignored -> new Object());
        synchronized (lock) {
            PlayerStats old = getCached(uuid);
            set(uuid, new PlayerStats(
                    old.wins() + 1, old.losses(), old.kills() + (kill ? 1 : 0),
                    old.deaths(), old.streak() + 1, Math.max(old.bestStreak(), old.streak() + 1),
                    old.elo() + plugin.getConfig().getDouble("match-results.win-elo-change", 15.0)
            ));
        }
    }

    public void recordLoss(UUID uuid) {
        Object lock = playerLocks.computeIfAbsent(uuid, ignored -> new Object());
        synchronized (lock) {
            PlayerStats old = getCached(uuid);
            set(uuid, new PlayerStats(
                    old.wins(), old.losses() + 1, old.kills(), old.deaths() + 1,
                    0, old.bestStreak(), Math.max(0, old.elo() - plugin.getConfig().getDouble("match-results.loss-elo-change", 15.0))
            ));
        }
    }

    public void recordDraw(UUID uuid) {
        Object lock = playerLocks.computeIfAbsent(uuid, ignored -> new Object());
        synchronized (lock) {
            PlayerStats old = getCached(uuid);
            set(uuid, new PlayerStats(
                    old.wins(), old.losses(), old.kills(), old.deaths(),
                    old.streak(), old.bestStreak(), old.elo()
            ));
        }
    }

    @Override
    public void record(PartyMatchResultService.PartyMatchResult result) {
        if (result.matchId() == null || result.players().isEmpty()) return;
        if (plugin.getConfig().getBoolean("match-results.duplicate-protection", true)) {
            if (!processedMatches.add(result.matchId())) return;
            plugin.get("processed:" + result.matchId()).thenAccept(marker -> {
                if (marker != null) {
                    processedMatches.remove(result.matchId());
                    return;
                }
                plugin.storage().database().update(
                        "INSERT OR IGNORE INTO module_data(module, data_key, data_value, updated_at) VALUES ('stats', ?, ?, ?)",
                        "processed:" + result.matchId(),
                        Long.toString(System.currentTimeMillis()),
                        java.time.Instant.now().toString()
                ).thenAccept(inserted -> {
                    if (inserted == 1) recordPartyResult(result);
                    else processedMatches.remove(result.matchId());
                }).exceptionally(error -> {
                    processedMatches.remove(result.matchId());
                    plugin.getLogger().warning("Could not atomically mark party match " + result.matchId() + ": " + error.getMessage());
                    return null;
                });
            });
            return;
        }
        recordPartyResult(result);
    }

    private void recordPartyResult(PartyMatchResultService.PartyMatchResult result) {
        Set<UUID> winners = Set.copyOf(result.winners());
        for (UUID player : result.players()) {
            if (player == null) continue;
            if (winners.contains(player)) {
                recordWin(player, plugin.getConfig().getBoolean("match-results.party-winner-counts-as-kill", false));
                if (plugin.getConfig().getBoolean("match-results.kit-stats-enabled", true) && result.kit() != null) {
                    recordKitResult(player, result.kit(), true);
                }
            } else {
                recordLoss(player);
                if (plugin.getConfig().getBoolean("match-results.kit-stats-enabled", true) && result.kit() != null) {
                    recordKitResult(player, result.kit(), false);
                }
            }
            if (plugin.getConfig().getBoolean("match-results.history-enabled", true)) {
                plugin.put("history:" + player + ":" + System.currentTimeMillis() + ":" + result.matchId(),
                        (winners.contains(player) ? "WIN" : "LOSS") + "|" + result.kit() + "|" + result.mode()
                                + "|" + result.arena() + "|" + result.durationMs());
            }
        }
    }

    @Override
    public void record(MatchResultService.MatchResult result) {
        if (result.matchId() == null) return;
        if (plugin.getConfig().getBoolean("match-results.duplicate-protection", true)) {
            if (!processedMatches.add(result.matchId())) return;
            plugin.get("processed:" + result.matchId()).thenAccept(marker -> {
                if (marker != null) {
                    processedMatches.remove(result.matchId());
                    return;
                }
                plugin.getStorage().database().update(
                        "INSERT OR IGNORE INTO module_data(module, data_key, data_value, updated_at) VALUES ('stats', ?, ?, ?)",
                        "processed:" + result.matchId(),
                        Long.toString(System.currentTimeMillis()),
                        java.time.Instant.now().toString()
                ).thenAccept(inserted -> {
                    if (inserted == 1) recordMatchAndHistory(result);
                    else processedMatches.remove(result.matchId());
                }).exceptionally(error -> {
                    processedMatches.remove(result.matchId());
                    plugin.getLogger().warning("Could not atomically mark match " + result.matchId() + ": " + error.getMessage());
                    return null;
                });
            });
            return;
        }
        recordMatchAndHistory(result);
    }

    private void recordMatchAndHistory(MatchResultService.MatchResult result) {
        if (result.winner() == null) {
            if (result.playerA() != null) recordDraw(result.playerA());
            if (result.playerB() != null) recordDraw(result.playerB());
        } else {
            recordMatch(result.winner(), result.loser());
        }
        if (plugin.getConfig().getBoolean("match-results.kit-stats-enabled", true) && result.kit() != null) {
            recordKitResult(result.winner(), result.kit(), true);
            recordKitResult(result.loser(), result.kit(), false);
        }
        String timestamp = Long.toString(System.currentTimeMillis());
        String winner = result.winner() == null ? "" : result.winner().toString();
        String loser = result.loser() == null ? "" : result.loser().toString();
        String base = timestamp + ":" + result.matchId();
        if (result.playerA() != null) plugin.put("history:" + result.playerA() + ":" + base,
                encodeHistory(result, result.playerA(), winner, loser));
        if (result.playerB() != null) plugin.put("history:" + result.playerB() + ":" + base,
                encodeHistory(result, result.playerB(), winner, loser));
        if (result.kit() != null) {
            if (result.winner() != null) plugin.put("kit:" + result.winner() + ":" + result.kit() + ":wins",
                    Long.toString(getCached(result.winner()).wins()));
            if (result.loser() != null) plugin.put("kit:" + result.loser() + ":" + result.kit() + ":losses",
                    Long.toString(getCached(result.loser()).losses()));
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

    public CompletableFuture<String> getKitStats(UUID player, String kit) {
        return plugin.get("kitstats:" + player + ":" + kit.toLowerCase(Locale.ROOT));
    }

    private void recordKitResult(UUID player, String kit, boolean win) {
        if (player == null || kit == null) return;
        String key = "kitstats:" + player + ":" + kit.toLowerCase(Locale.ROOT);
        long[] counters = kitStatsCache.computeIfAbsent(key, ignored -> new long[]{0, 0});
        if (win) counters[0]++; else counters[1]++;
        plugin.put(key, counters[0] + "," + counters[1]);
    }

    public CompletableFuture<List<RankedPlayer>> top(int limit) {
        return plugin.queryTop(Math.max(1, Math.min(50, limit)));
    }

    private static String encode(PlayerStats s) {
        return s.wins() + "," + s.losses() + "," + s.kills() + "," +
                s.deaths() + "," + s.streak() + "," + s.bestStreak() + "," + s.elo();
    }
}
