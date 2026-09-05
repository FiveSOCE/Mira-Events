package com.mira.events.service;

import com.mira.core.api.MiraCore;
import com.mira.core.api.NotificationService;
import com.mira.events.MiraEventsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class AnnouncementService {
    private final MiraEventsPlugin plugin;
    private final MiraCore core;
    private int cursor;
    private long nextAt;

    public AnnouncementService(MiraEventsPlugin plugin, MiraCore core) {
        this.plugin = plugin;
        this.core = core;
        resetTimer();
    }

    public void reload() {
        cursor = 0;
        resetTimer();
    }

    public void tick(long now) {
        if (!plugin.getConfig().getBoolean("announcements.enabled", false)) return;
        if (now < nextAt) return;
        List<Announcement> announcements = configured();
        if (announcements.isEmpty()) {
            resetTimer();
            return;
        }

        Announcement selected;
        if (plugin.getConfig().getBoolean("announcements.random-order", false)) {
            selected = announcements.get(ThreadLocalRandom.current().nextInt(announcements.size()));
        } else {
            selected = announcements.get(cursor % announcements.size());
            cursor = (cursor + 1) % announcements.size();
        }
        send(selected);
        resetTimer();
    }

    public void announce(String message, NotificationService.Channel channel) {
        Announcement announcement = new Announcement("manual", channel == null ? NotificationService.Channel.CHAT : channel,
                message, "", "", "");
        send(announcement);
    }

    public int configuredCount() {
        return configured().size();
    }

    private void resetTimer() {
        long interval = Math.max(10L, plugin.getConfig().getLong("announcements.interval-seconds", 600L));
        nextAt = System.currentTimeMillis() + interval * 1000L;
    }

    private List<Announcement> configured() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("announcements.items");
        if (root == null) return List.of();
        List<Announcement> out = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            String base = "announcements.items." + id + ".";
            if (!plugin.getConfig().getBoolean(base + "enabled", true)) continue;
            String message = plugin.getConfig().getString(base + "message", "");
            if (message == null || message.isBlank()) continue;
            NotificationService.Channel channel = channel(plugin.getConfig().getString(base + "channel", "CHAT"));
            out.add(new Announcement(id, channel, message,
                    plugin.getConfig().getString(base + "subtitle", ""),
                    plugin.getConfig().getString(base + "click-command", ""),
                    plugin.getConfig().getString(base + "hover", "")));
        }
        return List.copyOf(out);
    }

    private void send(Announcement announcement) {
        Component primary = core.messages().parse(announcement.message());
        if (!announcement.clickCommand().isBlank()) {
            String command = announcement.clickCommand().startsWith("/")
                    ? announcement.clickCommand() : "/" + announcement.clickCommand();
            primary = primary.clickEvent(ClickEvent.runCommand(command));
        }
        if (!announcement.hover().isBlank()) {
            primary = primary.hoverEvent(HoverEvent.showText(core.messages().parse(announcement.hover())));
        }
        Component secondary = announcement.subtitle().isBlank()
                ? Component.empty() : core.messages().parse(announcement.subtitle());

        for (Player player : Bukkit.getOnlinePlayers()) {
            core.notifications().send(player, announcement.channel(), primary, secondary);
        }
    }

    private NotificationService.Channel channel(String raw) {
        try {
            return NotificationService.Channel.valueOf(raw == null ? "CHAT" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NotificationService.Channel.CHAT;
        }
    }

    private record Announcement(String id, NotificationService.Channel channel, String message,
                                String subtitle, String clickCommand, String hover) { }
}
