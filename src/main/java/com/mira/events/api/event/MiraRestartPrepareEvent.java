package com.mira.events.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class MiraRestartPrepareEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String reason;
    private final boolean automatic;

    public MiraRestartPrepareEvent(String reason, boolean automatic) {
        this.reason = reason == null ? "" : reason;
        this.automatic = automatic;
    }

    public String reason() { return reason; }
    public boolean automatic() { return automatic; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
