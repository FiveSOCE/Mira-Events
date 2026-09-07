package com.mira.events.service;

import com.mira.events.MiraEventsPlugin;
import com.mira.events.api.event.MiraEventStartedEvent;
import com.mira.events.api.event.MiraEventStoppedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class EventModifierService implements Listener {
    private final MiraEventsPlugin plugin;
    private final Set<String> active = new HashSet<>();
    private final Map<String, Map<String, Object>> previous = new HashMap<>();

    public EventModifierService(MiraEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public void syncActive(Collection<String> eventIds) {
        if (eventIds == null) return;
        for (String id : eventIds) apply(id);
    }

    @EventHandler
    public void onStart(MiraEventStartedEvent event) {
        apply(event.eventId());
    }

    @EventHandler
    public void onStop(MiraEventStoppedEvent event) {
        restore(event.eventId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNaturalSpawn(CreatureSpawnEvent event) {
        if (!active.contains("mob-frenzy")) return;
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL
                && reason != CreatureSpawnEvent.SpawnReason.SPAWNER) return;
        if (!(event.getEntity() instanceof Monster)) return;

        double chance = clamp(plugin.eventsConfig().getDouble("events.mob-frenzy.modifier.extra-spawn-chance", 0.50D));
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!active.contains("mob-frenzy")) return;
            if (!event.getLocation().getChunk().isLoaded()) return;
            event.getLocation().getWorld().spawnEntity(event.getLocation(), event.getEntityType(),
                    CreatureSpawnEvent.SpawnReason.CUSTOM);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (!active.contains("harvest-rush")) return;
        double chance = clamp(plugin.eventsConfig().getDouble("events.harvest-rush.modifier.extra-growth-chance", 0.65D));
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        Block block = event.getBlock();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!active.contains("harvest-rush")) return;
            if (!(block.getBlockData() instanceof Ageable ageable)) return;
            if (ageable.getAge() >= ageable.getMaximumAge()) return;
            ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 1));
            block.setBlockData(ageable, false);
        });
    }

    private void apply(String rawId) {
        String id = normalize(rawId);
        if (!active.add(id)) return;

        switch (id) {
            case "enhanced-airdrops" -> applyEnhancedAirdrops(id);
            case "mega-pinata" -> applyMegaPinata(id);
            case "mob-frenzy", "harvest-rush" -> { }
            default -> { active.remove(id); }
        }
    }

    private void restore(String rawId) {
        String id = normalize(rawId);
        if (!active.remove(id)) return;
        Map<String, Object> snapshot = previous.remove(id);
        if (snapshot == null || snapshot.isEmpty()) return;

        Plugin target = switch (id) {
            case "enhanced-airdrops" -> Bukkit.getPluginManager().getPlugin("MiraAirdrops");
            case "mega-pinata" -> Bukkit.getPluginManager().getPlugin("MiraPinata");
            default -> null;
        };
        if (target == null) return;
        snapshot.forEach((path, value) -> target.getConfig().set(path, value));
        target.saveConfig();
    }

    private void applyEnhancedAirdrops(String id) {
        Plugin target = Bukkit.getPluginManager().getPlugin("MiraAirdrops");
        if (target == null || !target.isEnabled()) {
            active.remove(id);
            plugin.getLogger().warning("Enhanced Airdrops could not start: MiraAirdrops is unavailable.");
            return;
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        setTemporary(target, snapshot, "event.min-crates",
                plugin.eventsConfig().getInt("events.enhanced-airdrops.modifier.min-crates", 35));
        setTemporary(target, snapshot, "event.max-crates",
                plugin.eventsConfig().getInt("events.enhanced-airdrops.modifier.max-crates", 50));
        setTemporary(target, snapshot, "event.min-loot-items",
                plugin.eventsConfig().getInt("events.enhanced-airdrops.modifier.min-loot-items", 3));
        setTemporary(target, snapshot, "event.max-loot-items",
                plugin.eventsConfig().getInt("events.enhanced-airdrops.modifier.max-loot-items", 5));
        target.saveConfig();
        previous.put(id, snapshot);

        try {
            Object service = target.getClass().getMethod("service").invoke(target);
            boolean activeNow = (Boolean) service.getClass().getMethod("active").invoke(service);
            boolean inbound = (Boolean) service.getClass().getMethod("inbound").invoke(service);
            if (!activeNow && !inbound) {
                service.getClass().getMethod("start", org.bukkit.command.CommandSender.class)
                        .invoke(service, Bukkit.getConsoleSender());
            }
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Could not start enhanced airdrop: " + ex.getMessage());
        }
    }

    private void applyMegaPinata(String id) {
        Plugin target = Bukkit.getPluginManager().getPlugin("MiraPinata");
        if (target == null || !target.isEnabled()) {
            active.remove(id);
            plugin.getLogger().warning("Mega Pinata could not start: MiraPinata is unavailable.");
            return;
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        setTemporary(target, snapshot, "boss.auto-scale-health", false);
        setTemporary(target, snapshot, "boss.hits",
                plugin.eventsConfig().getInt("events.mega-pinata.modifier.hits", 750));
        setTemporary(target, snapshot, "rewards.top-hitter-extra-item", true);
        target.saveConfig();
        previous.put(id, snapshot);

        try {
            Object manager = target.getClass().getMethod("manager").invoke(target);
            boolean activeNow = (Boolean) manager.getClass().getMethod("active").invoke(manager);
            boolean countdown = (Boolean) manager.getClass().getMethod("countingDown").invoke(manager);
            if (!activeNow && !countdown) {
                manager.getClass().getMethod("startCountdown").invoke(manager);
            }
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Could not start Mega Pinata: " + ex.getMessage());
        }
    }

    private void setTemporary(Plugin target, Map<String, Object> snapshot, String path, Object value) {
        snapshot.put(path, target.getConfig().get(path));
        target.getConfig().set(path, value);
    }

    public void shutdown() {
        for (String id : new ArrayList<>(active)) restore(id);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private double clamp(double value) {
        return Math.max(0D, Math.min(1D, value));
    }
}
