package net.voidflame.stats;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class StatsMenu implements Listener {
    private static final String TITLE="§8VoidFlame §7• §dStatistics";
    private final VoidFlameStatsPlugin plugin;
    public StatsMenu(VoidFlameStatsPlugin plugin){this.plugin=plugin;}
    public void open(Player p){
        var s=plugin.stats().getCached(p.getUniqueId());
        Inventory inv=Bukkit.createInventory(null,27,TITLE); fill(inv);
        inv.setItem(4,item(Material.PLAYER_HEAD,"§d§l"+p.getName(),"§7VoidFlameMC Practice","§8Your statistics"));
        inv.setItem(10,item(Material.EMERALD,"§a§lMy Stats","§7Wins: §f"+s.wins(),"§7Losses: §f"+s.losses(),"§7Kills: §f"+s.kills(),"§7Deaths: §f"+s.deaths(),"§7Streak: §f"+s.streak(),"§7Best Streak: §f"+s.bestStreak(),"§7ELO: §b"+Math.round(s.elo())));
        inv.setItem(12,item(Material.PAPER,"§f§lMatch History","§7View your recent matches.","§dClick §8» §fOpen"));
        inv.setItem(14,item(Material.GOLD_INGOT,"§6§lLeaderboard","§7View the ELO leaderboard.","§dClick §8» §fOpen"));
        inv.setItem(16,item(Material.BOOK,"§b§lProfile","§7Your public practice profile."));
        inv.setItem(18,item(Material.ARROW,"§7§lBack","§7Return to spawn."));
        inv.setItem(26,item(Material.BARRIER,"§c§lClose"));
        p.openInventory(inv);
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||!e.getView().getTitle().equals(TITLE))return;
        e.setCancelled(true);
        switch(e.getRawSlot()){
            case 12 -> {p.closeInventory();Bukkit.dispatchCommand(p,"history");}
            case 14 -> {p.closeInventory();Bukkit.dispatchCommand(p,"leaderboard");}
            case 26,18 -> p.closeInventory();
            default -> {}
        }
    }
    private void fill(Inventory inv){ItemStack x=item(Material.GRAY_STAINED_GLASS_PANE," ");for(int i=0;i<27;i++)inv.setItem(i,x.clone());}
    private ItemStack item(Material m,String n,String... l){ItemStack x=new ItemStack(m);ItemMeta meta=x.getItemMeta();if(meta!=null){meta.setDisplayName(n);meta.setLore(List.of(l));x.setItemMeta(meta);}return x;}
}