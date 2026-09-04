package com.mira.events;

import com.mira.core.api.MiraCore;
import com.mira.core.api.MiraCoreProvider;
import com.mira.core.api.ModuleHealth;
import com.mira.events.api.MiraEventsApi;
import com.mira.events.api.event.MiraEventStartedEvent;
import com.mira.events.api.event.MiraEventStoppedEvent;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class MiraEventsPlugin extends JavaPlugin implements TabExecutor, MiraEventsApi {
    private final Map<String, EventDef> definitions = new LinkedHashMap<>();
    private MiraCore core;
    private final Map<String, RuntimeState> states = new HashMap<>();
    private File eventsFile;
    private File stateFile;
    private YamlConfiguration eventsConfig;
    private YamlConfiguration stateConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        core = MiraCoreProvider.require();
        if (!new File(getDataFolder(), "events.yml").exists()) saveResource("events.yml", false);
        eventsFile = new File(getDataFolder(), "events.yml");
        stateFile = new File(getDataFolder(), "state.yml");
        reloadDefinitions();

        var command = Objects.requireNonNull(getCommand("miraevent"), "miraevent command missing");
        command.setExecutor(this);
        command.setTabCompleter(this);
        getServer().getServicesManager().register(MiraEventsApi.class, this, this, ServicePriority.Normal);
        core.modules().register(this, "MiraEvents");
        core.services().register(MiraEventsApi.class, this);
        core.modules().setHealth(this, ModuleHealth.HEALTHY,
                "Scheduled event state, command chains and typed lifecycle events ready");
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) new EventsExpansion().register();

        long ticks = Math.max(1L, getConfig().getLong("tick-seconds", 1L)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, this::tick, ticks, ticks);
        getLogger().info("MiraEvents v" + getPluginMeta().getVersion() + " enabled with " + definitions.size() + " event definition(s).");
    }

    @Override
    public void onDisable() {
        saveState();
        getServer().getServicesManager().unregisterAll(this);
        if (core != null) {
            core.services().unregister(MiraEventsApi.class, this);
            core.modules().unregister(this);
        }
    }

    private void reloadDefinitions() {
        eventsConfig = YamlConfiguration.loadConfiguration(eventsFile);
        stateConfig = YamlConfiguration.loadConfiguration(stateFile);
        definitions.clear();
        states.clear();
        ConfigurationSection root = eventsConfig.getConfigurationSection("events");
        if (root == null) return;
        long now = System.currentTimeMillis();
        for (String rawId : root.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            String path = "events." + rawId;
            String display = eventsConfig.getString(path + ".display-name", rawId);
            boolean enabled = eventsConfig.getBoolean(path + ".enabled", true);
            long durationMs = Math.max(1L, eventsConfig.getLong(path + ".duration-seconds", 60L)) * 1000L;
            long firstStart = Math.max(0L, eventsConfig.getLong(path + ".first-start-at", 0L));
            long repeatMs = Math.max(0L, eventsConfig.getLong(path + ".repeat-minutes", 0L)) * 60_000L;
            Set<Long> countdown = new LinkedHashSet<>();
            for (Integer seconds : eventsConfig.getIntegerList(path + ".countdown-seconds")) if (seconds != null && seconds > 0) countdown.add(seconds.longValue());
            EventDef def = new EventDef(id, display, enabled, durationMs, firstStart, repeatMs, countdown,
                    eventsConfig.getStringList(path + ".start-commands"), eventsConfig.getStringList(path + ".end-commands"),
                    eventsConfig.getString(path + ".start-broadcast", ""), eventsConfig.getString(path + ".end-broadcast", ""));
            definitions.put(id, def);

            long next = stateConfig.getLong("events." + id + ".next-start", firstStart);
            long activeUntil = stateConfig.getLong("events." + id + ".active-until", 0L);
            boolean active = activeUntil > now;
            if (!active && activeUntil > 0) activeUntil = 0;
            if (next <= 0 && firstStart > 0) next = firstStart;
            if (repeatMs > 0 && next > 0 && next <= now && !active) {
                while (next <= now) next += repeatMs;
            } else if (repeatMs == 0 && next > 0 && next < now && !active) {
                next = 0;
            }
            states.put(id, new RuntimeState(active, activeUntil, next));
        }
        saveState();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (EventDef def : definitions.values()) {
            RuntimeState state = states.get(def.id());
            if (state == null) continue;
            if (state.active && now >= state.activeUntil) {
                stopInternal(def, state, true);
                continue;
            }
            if (state.active || !def.enabled() || state.nextStart <= 0) continue;
            long millis = state.nextStart - now;
            long seconds = Math.max(0L, (millis + 999L) / 1000L);
            if (millis <= 0) {
                startInternal(def, state, false);
            } else if (def.countdownSeconds().contains(seconds) && state.lastCountdown != seconds) {
                state.lastCountdown = seconds;
                String raw = getConfig().getString("messages.countdown", "&e%event% &7starts in &f%seconds%s&7.")
                        .replace("%event%", def.displayName()).replace("%seconds%", Long.toString(seconds));
                broadcast(raw);
            }
        }
    }

    @Override public Set<String> eventIds() { return Collections.unmodifiableSet(new LinkedHashSet<>(definitions.keySet())); }
    @Override public boolean isActive(String eventId) { RuntimeState state = states.get(key(eventId)); return state != null && state.active; }
    @Override public OptionalLong activeUntil(String eventId) { RuntimeState state = states.get(key(eventId)); return state == null || !state.active ? OptionalLong.empty() : OptionalLong.of(state.activeUntil); }
    @Override public OptionalLong nextStart(String eventId) { RuntimeState state = states.get(key(eventId)); return state == null || state.nextStart <= 0 ? OptionalLong.empty() : OptionalLong.of(state.nextStart); }

    @Override
    public boolean start(String eventId) {
        EventDef def = definitions.get(key(eventId));
        RuntimeState state = states.get(key(eventId));
        return def != null && state != null && startInternal(def, state, true);
    }

    @Override
    public boolean stop(String eventId) {
        EventDef def = definitions.get(key(eventId));
        RuntimeState state = states.get(key(eventId));
        return def != null && state != null && stopInternal(def, state, true);
    }

    private boolean startInternal(EventDef def, RuntimeState state, boolean manual) {
        if (state.active) return false;
        long now = System.currentTimeMillis();
        state.active = true;
        state.activeUntil = now + def.durationMs();
        state.lastCountdown = -1;
        advanceNext(def, state, now);
        dispatch(def.startCommands());
        if (!def.startBroadcast().isBlank()) broadcast(def.startBroadcast());
        else broadcast(getConfig().getString("messages.started", "&a%event% &7has started!").replace("%event%", def.displayName()));
        saveState();
        return true;
    }

    private boolean stopInternal(EventDef def, RuntimeState state, boolean announce) {
        if (!state.active) return false;
        state.active = false;
        state.activeUntil = 0L;
        dispatch(def.endCommands());
        if (announce) {
            if (!def.endBroadcast().isBlank()) broadcast(def.endBroadcast());
            else broadcast(getConfig().getString("messages.ended", "&c%event% &7has ended.").replace("%event%", def.displayName()));
        }
        saveState();
        return true;
    }

    private void advanceNext(EventDef def, RuntimeState state, long now) {
        if (def.repeatMs() <= 0) {
            state.nextStart = 0L;
            return;
        }
        long base = state.nextStart > 0 ? state.nextStart : now;
        while (base <= now) base += def.repeatMs();
        state.nextStart = base;
    }

    private void dispatch(List<String> commands) {
        for (String command : commands) {
            String clean = command == null ? "" : command.trim();
            if (clean.startsWith("/")) clean = clean.substring(1);
            if (!clean.isBlank()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), clean);
        }
    }

    private void saveState() {
        if (stateConfig == null || stateFile == null) return;
        stateConfig.set("events", null);
        for (var entry : states.entrySet()) {
            String path = "events." + entry.getKey();
            stateConfig.set(path + ".active-until", entry.getValue().active ? entry.getValue().activeUntil : 0L);
            stateConfig.set(path + ".next-start", entry.getValue().nextStart);
        }
        try { stateConfig.save(stateFile); } catch (IOException ex) { getLogger().severe("Could not save state.yml: " + ex.getMessage()); }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            msg(sender, "&dConfigured Events:");
            for (EventDef def : definitions.values()) {
                RuntimeState state = states.get(def.id());
                String status = state.active ? "&aACTIVE" : (state.nextStart > 0 ? "&eSCHEDULED" : "&7IDLE");
                msg(sender, "&7- &f" + def.id() + " &8(" + def.displayName() + "&8) " + status);
            }
            if (definitions.isEmpty()) msg(sender, "&7None configured.");
            return true;
        }
        if (args[0].equalsIgnoreCase("info")) {
            if (args.length < 2) { msg(sender, "&eUsage: /mevent info <id>"); return true; }
            EventDef def = definitions.get(key(args[1]));
            RuntimeState state = states.get(key(args[1]));
            if (def == null || state == null) { msg(sender, "&cUnknown event."); return true; }
            msg(sender, "&d" + def.displayName() + " &7[&f" + def.id() + "&7]");
            msg(sender, "&7Enabled: &f" + def.enabled() + " &7Active: &f" + state.active);
            msg(sender, "&7Active until: &f" + (state.active ? new Date(state.activeUntil) : "N/A"));
            msg(sender, "&7Next start: &f" + (state.nextStart > 0 ? new Date(state.nextStart) : "Manual only"));
            return true;
        }
        if (!sender.hasPermission("miraevents.admin")) { msg(sender, "&cYou do not have permission."); return true; }
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            reloadDefinitions();
            msg(sender, "&aMiraEvents reloaded.");
            return true;
        }
        if ((args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("stop")) && args.length >= 2) {
            boolean result = args[0].equalsIgnoreCase("start") ? start(args[1]) : stop(args[1]);
            msg(sender, result ? "&aEvent state changed." : "&cEvent could not be changed (unknown event or already in that state).");
            return true;
        }
        msg(sender, "&eUsage: /mevent <list|info|start|stop|reload> [id]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("list", "info"));
            if (sender.hasPermission("miraevents.admin")) values.addAll(List.of("start", "stop", "reload"));
            return match(args[0], values);
        }
        if (args.length == 2 && List.of("info", "start", "stop").contains(args[0].toLowerCase(Locale.ROOT))) return match(args[1], definitions.keySet());
        return List.of();
    }

    private String key(String raw) { return raw == null ? "" : raw.toLowerCase(Locale.ROOT); }
    private void msg(CommandSender sender, String raw) { sender.sendMessage(color(getConfig().getString("messages.prefix", "&5&lMira &8>> &r") + raw)); }
    private void broadcast(String raw) { Bukkit.broadcastMessage(color("&5&lMira &8>> &r" + raw)); }
    private String color(String raw) { return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw); }
    private static List<String> match(String prefix, Collection<String> values) { String lower = prefix.toLowerCase(Locale.ROOT); return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList(); }

    private EventDef nextEvent() {
        return definitions.values().stream().filter(def -> {
            RuntimeState state = states.get(def.id());
            return state != null && !state.active && state.nextStart > 0;
        }).min(Comparator.comparingLong(def -> states.get(def.id()).nextStart)).orElse(null);
    }

    private record EventDef(String id, String displayName, boolean enabled, long durationMs, long firstStartAt, long repeatMs,
                            Set<Long> countdownSeconds, List<String> startCommands, List<String> endCommands,
                            String startBroadcast, String endBroadcast) { }

    private static final class RuntimeState {
        private boolean active;
        private long activeUntil;
        private long nextStart;
        private long lastCountdown = -1;
        private RuntimeState(boolean active, long activeUntil, long nextStart) { this.active = active; this.activeUntil = activeUntil; this.nextStart = nextStart; }
    }

    private final class EventsExpansion extends PlaceholderExpansion {
        @Override public String getIdentifier() { return "miraevents"; }
        @Override public String getAuthor() { return "FiveS"; }
        @Override public String getVersion() { return getPluginMeta().getVersion(); }
        @Override public boolean persist() { return true; }
        @Override public String onRequest(OfflinePlayer player, String params) {
            String lower = params.toLowerCase(Locale.ROOT);
            if (lower.equals("active_count")) return Long.toString(states.values().stream().filter(s -> s.active).count());
            if (lower.equals("active")) return definitions.values().stream().filter(def -> states.get(def.id()).active).map(EventDef::displayName).findFirst().orElse("");
            if (lower.equals("next_name")) { EventDef next = nextEvent(); return next == null ? "" : next.displayName(); }
            if (lower.equals("next_seconds")) { EventDef next = nextEvent(); return next == null ? "0" : Long.toString(Math.max(0, (states.get(next.id()).nextStart - System.currentTimeMillis()) / 1000L)); }
            if (lower.startsWith("event_") && lower.endsWith("_active")) {
                String id = lower.substring(6, lower.length() - 7);
                return Boolean.toString(isActive(id));
            }
            return null;
        }
    }
}
