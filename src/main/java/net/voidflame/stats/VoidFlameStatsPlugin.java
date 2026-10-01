package net.voidflame.stats;

import net.voidflame.core.storage.StorageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;

public final class VoidFlameStatsPlugin extends JavaPlugin implements Listener {
    private StorageService storage;
    private StatsService stats;

    @Override public void onEnable() {
        saveDefaultConfig();
        var registration=getServer().getServicesManager().getRegistration(StorageService.class);
        if(registration==null || (storage=registration.getProvider())==null){getLogger().severe("VoidFlame-Core storage service is unavailable.");getServer().getPluginManager().disablePlugin(this);return;}
        stats=new StatsService(this);
        if (getCommand("stats") != null) getCommand("stats").setExecutor(this);
        getServer().getServicesManager().register(StatsService.class,stats,this,ServicePriority.Normal);
        getServer().getServicesManager().register(net.voidflame.core.api.MatchResultService.class, stats, this, ServicePriority.Normal);
        getServer().getServicesManager().register(net.voidflame.core.api.PartyMatchResultService.class, stats, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(this,this);
        getLogger().info("VoidFlame-Stats enabled with persistent W/L/K/D/streak/ELO storage.");
    }

    public CompletableFuture<Void> put(String key,String value){return storage.put("stats",key,value);}

    public CompletableFuture<List<StatsService.RankedPlayer>> queryTop(int limit) {
        return storage.query("SELECT data_key,data_value FROM module_data WHERE module=? AND data_key LIKE 'player:%' ORDER BY data_key ASC", "stats")
            .thenApply(rows -> rows.stream().map(row -> {
                String key=String.valueOf(row.get("data_key"));
                String raw=String.valueOf(row.get("data_value"));
                try {
                    UUID id=UUID.fromString(key.substring("player:".length()));
                    String[] p=raw.split(",");
                    if(p.length!=6 && p.length!=7) return null;
                    StatsService.PlayerStats s=new StatsService.PlayerStats(Long.parseLong(p[0]),Long.parseLong(p[1]),Long.parseLong(p[2]),Long.parseLong(p[3]),Long.parseLong(p[4]),p.length==7?Long.parseLong(p[5]):Long.parseLong(p[4]),Double.parseDouble(p[p.length==7?6:5]));
                    Player online=getServer().getPlayer(id);
                    return new StatsService.RankedPlayer(id,online==null?id.toString():online.getName(),s);
                } catch(Exception ignored){return null;}
            }).filter(java.util.Objects::nonNull).sorted(java.util.Comparator.comparingDouble((StatsService.RankedPlayer p)->p.stats().elo()).reversed()).limit(limit).toList());
    }


    public CompletableFuture<String> get(String key){return storage.get("stats",key);}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)) return true;
        if (command.getName().equalsIgnoreCase("history")) { showHistory(player); return true; }
        if (command.getName().equalsIgnoreCase("leaderboard")) { showLeaderboard(player); return true; }
        if(!command.getName().equalsIgnoreCase("stats")) return true;
        if(args.length>0 && args[0].equalsIgnoreCase("top")){
            if (!player.hasPermission("voidflame.leaderboard")) { player.sendMessage("§cNo permission."); return true; }
            stats.top(10).thenAccept(rows->getServer().getScheduler().runTask(this,()->{
                player.sendMessage("§8§m--------------------"); player.sendMessage("§bVoidFlame §fELO Leaderboard");
                int i=1; for(var row:rows) player.sendMessage("§7"+i+++". §f"+row.name()+" §b"+Math.round(row.stats().elo()));
                player.sendMessage("§8§m--------------------");
            }));
            return true;
        }
        StatsService.PlayerStats value=stats.getCached(player.getUniqueId());
        player.sendMessage("§8§m--------------------"); player.sendMessage("§bVoidFlame §fStats");
        player.sendMessage("§7Wins: §a"+value.wins()); player.sendMessage("§7Losses: §c"+value.losses());
        player.sendMessage("§7Kills: §a"+value.kills()); player.sendMessage("§7Deaths: §c"+value.deaths());
        player.sendMessage("§7Streak: §e"+value.streak()); player.sendMessage("§7Best Streak: §6"+value.bestStreak()); player.sendMessage("§7ELO: §b"+Math.round(value.elo()));
        player.sendMessage("§8§m--------------------"); return true;
    }

    private void showLeaderboard(Player player) {
        if (!player.hasPermission("voidflame.leaderboard")) { player.sendMessage("§cNo permission."); return; }
        stats.top(10).thenAccept(rows -> getServer().getScheduler().runTask(this, () -> {
            player.sendMessage("§8§m--------------------");
            player.sendMessage("§bVoidFlame §fELO Leaderboard");
            int i = 1;
            for (var row : rows) player.sendMessage("§7" + i++ + ". §f" + row.name() + " §b" + Math.round(row.stats().elo()));
            player.sendMessage("§8§m--------------------");
        }));
    }

    private void showHistory(Player player) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> storage.query(
                "SELECT data_key,data_value FROM module_data WHERE module=? AND data_key LIKE ? ORDER BY data_key DESC",
                "stats", "history:" + player.getUniqueId() + ":%"
        ).thenAccept(rows -> getServer().getScheduler().runTask(this, () -> {
            player.sendMessage("§8§m--------------------");
            player.sendMessage("§bVoidFlame §fMatch History");
            int shown = 0;
            for (var row : rows) {
                if (shown++ >= 10) break;
                String value = String.valueOf(row.get("data_value"));
                String[] parts = value.split("\\|", -1);
                String outcome = parts.length > 0 ? parts[0] : "UNKNOWN";
                String kit = parts.length > 1 ? parts[1] : "unknown";
                String mode = parts.length > 2 ? parts[2] : "unknown";
                String arena = parts.length > 3 ? parts[3] : "unknown";
                String prefix = outcome.equalsIgnoreCase("WIN") ? "§a" : outcome.equalsIgnoreCase("LOSS") ? "§c" : "§e";
                player.sendMessage(prefix + outcome + " §7• §f" + kit + " §8• §7" + mode + " §8• §7" + arena);
            }
            if (shown == 0) player.sendMessage("§7No completed matches yet.");
            player.sendMessage("§8§m--------------------");
        })));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event){stats.load(event.getPlayer().getUniqueId());}
    public StatsService stats(){return stats;}
    @Override public void onDisable(){getServer().getServicesManager().unregister(StatsService.class,this);
    getServer().getServicesManager().unregister(net.voidflame.core.api.MatchResultService.class,this);
    getServer().getServicesManager().unregister(net.voidflame.core.api.PartyMatchResultService.class,this);}
}
