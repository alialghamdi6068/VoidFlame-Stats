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

public final class VoidFlameStatsPlugin extends JavaPlugin implements Listener {
    private StorageService storage;
    private StatsService stats;

    @Override public void onEnable() {
        saveDefaultConfig();
        var registration=getServer().getServicesManager().getRegistration(StorageService.class);
        if(registration==null || (storage=registration.getProvider())==null){getLogger().severe("VoidFlame-Core storage service is unavailable.");getServer().getPluginManager().disablePlugin(this);return;}
        stats=new StatsService(this);
        getServer().getServicesManager().register(StatsService.class,stats,this,ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(this,this);
        getLogger().info("VoidFlame-Stats enabled with persistent W/L/K/D/streak/ELO storage.");
    }

    public CompletableFuture<Void> put(String key,String value){return storage.put("stats",key,value);}

    public CompletableFuture<List<StatsService.RankedPlayer>> queryTop(int limit) {
        return storage.query("SELECT data_key,data_value FROM module_data WHERE module=? AND data_key LIKE 'player:%' ORDER BY updated_at DESC LIMIT ?", "stats", limit * 3)
            .thenApply(rows -> rows.stream().map(row -> {
                String key=String.valueOf(row.get("data_key"));
                String raw=String.valueOf(row.get("data_value"));
                try {
                    UUID id=UUID.fromString(key.substring("player:".length()));
                    String[] p=raw.split(",");
                    if(p.length!=6) return null;
                    StatsService.PlayerStats s=new StatsService.PlayerStats(Long.parseLong(p[0]),Long.parseLong(p[1]),Long.parseLong(p[2]),Long.parseLong(p[3]),Long.parseLong(p[4]),Double.parseDouble(p[5]));
                    Player online=getServer().getPlayer(id);
                    return new StatsService.RankedPlayer(id,online==null?id.toString():online.getName(),s);
                } catch(Exception ignored){return null;}
            }).filter(java.util.Objects::nonNull).sorted(java.util.Comparator.comparingDouble((StatsService.RankedPlayer p)->p.stats().elo()).reversed()).limit(limit).toList());
    }


    public CompletableFuture<String> get(String key){return storage.get("stats",key);}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)||!command.getName().equalsIgnoreCase("stats")) return true;
        if(args.length>0 && args[0].equalsIgnoreCase("top")){
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
        player.sendMessage("§7Streak: §e"+value.streak()); player.sendMessage("§7ELO: §b"+Math.round(value.elo()));
        player.sendMessage("§8§m--------------------"); return true;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event){stats.load(event.getPlayer().getUniqueId());}
    public StatsService stats(){return stats;}
    @Override public void onDisable(){getServer().getServicesManager().unregister(StatsService.class,this);}
}
