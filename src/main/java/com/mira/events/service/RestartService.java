package com.mira.events.service;

import com.mira.core.api.MiraCore;
import com.mira.events.MiraEventsPlugin;
import com.mira.events.api.event.MiraRestartPrepareEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

public final class RestartService {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final MiraEventsPlugin plugin;
    private final MiraCore core;
    private long restartAt;
    private String reason = "";
    private boolean automatic;
    private long lastCountdown = -1L;
    private long nextAutomaticAt;

    public RestartService(MiraEventsPlugin plugin, MiraCore core) {
        this.plugin = plugin;
        this.core = core;
        reload();
    }

    public void reload() {
        if (restartAt <= 0L) nextAutomaticAt = computeNextAutomatic(System.currentTimeMillis());
    }

    public void tick(long now) {
        if (restartAt <= 0L) {
            ensureAutomatic(now);
            return;
        }

        long millis = restartAt - now;
        if (millis <= 0L) {
            executeRestart();
            return;
        }

        long seconds = Math.max(1L, (millis + 999L) / 1000L);
        if (countdowns().contains(seconds) && lastCountdown != seconds) {
            lastCountdown = seconds;
            broadcast(plugin.getConfig().getString("restart.messages.countdown",
                            "&eServer restart in &f%time%&e. &7%reason%")
                    .replace("%time%", format(seconds))
                    .replace("%reason%", reason));
        }
    }

    public boolean schedule(long delaySeconds, String reason, boolean automatic) {
        if (!plugin.getConfig().getBoolean("restart.enabled", true) || delaySeconds <= 0L) return false;
        this.restartAt = System.currentTimeMillis() + delaySeconds * 1000L;
        this.reason = reason == null || reason.isBlank()
                ? plugin.getConfig().getString("restart.default-reason", "Scheduled restart")
                : reason.trim();
        this.automatic = automatic;
        this.lastCountdown = -1L;

        core.audit().record("MiraEvents", automatic ? "RESTART_SCHEDULED_AUTOMATIC" : "RESTART_SCHEDULED_MANUAL",
                null, automatic ? "scheduler" : "admin", "server", "Server restart scheduled",
                Map.of("restartAt", Long.toString(restartAt), "reason", this.reason));
        return true;
    }

    public boolean cancel(String actor) {
        if (restartAt <= 0L) return false;
        long cancelledAt = restartAt;
        boolean wasAutomatic = automatic;
        restartAt = 0L;
        lastCountdown = -1L;
        automatic = false;
        reason = "";
        if (wasAutomatic) nextAutomaticAt = computeNextAutomatic(cancelledAt + 1000L);
        core.audit().record("MiraEvents", "RESTART_CANCELLED", null, actor == null ? "admin" : actor,
                "server", "Server restart cancelled");
        broadcast(plugin.getConfig().getString("restart.messages.cancelled", "&aScheduled server restart cancelled."));
        return true;
    }

    public OptionalLong restartAt() {
        return restartAt <= 0L ? OptionalLong.empty() : OptionalLong.of(restartAt);
    }

    public String reason() { return reason; }
    public boolean automatic() { return automatic; }

    private void ensureAutomatic(long now) {
        if (!plugin.getConfig().getBoolean("restart.automatic.enabled", false)) return;
        if (nextAutomaticAt <= 0L || nextAutomaticAt <= now) nextAutomaticAt = computeNextAutomatic(now);
        if (nextAutomaticAt <= 0L) return;
        long delay = Math.max(1L, (nextAutomaticAt - now + 999L) / 1000L);
        schedule(delay, plugin.getConfig().getString("restart.automatic.reason", "Automatic scheduled restart"), true);
        restartAt = nextAutomaticAt;
    }

    private long computeNextAutomatic(long afterEpochMillis) {
        if (!plugin.getConfig().getBoolean("restart.automatic.enabled", false)) return 0L;
        ZoneId zone;
        try {
            zone = ZoneId.of(plugin.getConfig().getString("restart.automatic.timezone", "Australia/Brisbane"));
        } catch (Exception ignored) {
            zone = ZoneId.of("Australia/Brisbane");
        }

        ZonedDateTime after = Instant.ofEpochMilli(afterEpochMillis).atZone(zone);
        ZonedDateTime best = null;
        for (String raw : plugin.getConfig().getStringList("restart.automatic.times")) {
            try {
                LocalTime time = LocalTime.parse(raw.trim(), CLOCK);
                ZonedDateTime candidate = ZonedDateTime.of(after.toLocalDate(), time, zone);
                if (!candidate.isAfter(after)) candidate = candidate.plusDays(1);
                if (best == null || candidate.isBefore(best)) best = candidate;
            } catch (DateTimeParseException exception) {
                plugin.getLogger().warning("Ignoring invalid automatic restart time '" + raw + "'. Use HH:mm.");
            }
        }
        return best == null ? 0L : best.toInstant().toEpochMilli();
    }

    private Set<Long> countdowns() {
        Set<Long> values = new LinkedHashSet<>();
        List<Integer> configured = plugin.getConfig().getIntegerList("restart.countdown-seconds");
        for (Integer value : configured) if (value != null && value > 0) values.add(value.longValue());
        return values;
    }

    private void executeRestart() {
        if (restartAt <= 0L) return;
        String finalReason = reason;
        boolean finalAutomatic = automatic;
        restartAt = 0L;
        lastCountdown = -1L;

        Bukkit.getPluginManager().callEvent(new MiraRestartPrepareEvent(finalReason, finalAutomatic));
        core.audit().record("MiraEvents", "RESTART_EXECUTING", null, "scheduler",
                "server", "Server restart executing",
                Map.of("reason", finalReason, "automatic", Boolean.toString(finalAutomatic)));

        broadcast(plugin.getConfig().getString("restart.messages.now", "&cServer restarting now. &7%reason%")
                .replace("%reason%", finalReason));

        if (plugin.getConfig().getBoolean("restart.save-before-shutdown", true)) {
            Bukkit.savePlayers();
            for (World world : Bukkit.getWorlds()) {
                try { world.save(); }
                catch (RuntimeException exception) {
                    plugin.getLogger().warning("World save failed for " + world.getName() + ": " + exception.getMessage());
                }
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, Bukkit::shutdown, 20L);
    }

    private void broadcast(String raw) {
        Bukkit.broadcast(core.messages().prefix().append(core.messages().parse(raw == null ? "" : raw)));
    }

    private String format(long seconds) {
        if (seconds >= 3600 && seconds % 3600 == 0) return (seconds / 3600) + "h";
        if (seconds >= 60 && seconds % 60 == 0) return (seconds / 60) + "m";
        return seconds + "s";
    }
}
