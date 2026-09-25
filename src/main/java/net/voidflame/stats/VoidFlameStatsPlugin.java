package net.voidflame.stats;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

public final class VoidFlameStatsPlugin extends JavaPlugin implements Listener {
    private Object storage;
    private Method put;
    private Method get;
    private StatsService stats;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!connectStorage()) {
            getLogger().severe("VoidFlame-Core storage service is unavailable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        stats = new StatsService(this);
        getServer().getServicesManager().register(StatsService.class, stats, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("VoidFlame-Stats enabled with persistent W/L/K/D/streak/ELO storage.");
    }

    private boolean connectStorage() {
        try {
            Class<?> type = Class.forName("net.voidflame.core.storage.StorageService");
            RegisteredServiceProvider<?> registration = getServer().getServicesManager().getRegistration(type);
            if (registration == null) return false;
            storage = registration.getProvider();
            put = type.getMethod("put", String.class, String.class, String.class);
            get = type.getMethod("get", String.class, String.class);
            return true;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    public CompletableFuture<Void> put(String key, String value) {
        try {
            return (CompletableFuture<Void>) put.invoke(storage, "stats", key, value);
        } catch (ReflectiveOperationException ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    public CompletableFuture<String> get(String key) {
        try {
            return (CompletableFuture<String>) get.invoke(storage, "stats", key);
        } catch (ReflectiveOperationException ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { stats.load(event.getPlayer().getUniqueId()); }

    public StatsService stats() {
        return stats;
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregister(StatsService.class, this);
    }
}
