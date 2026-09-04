package com.mira.events.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class MiraEventStoppedEvent extends Event {
    public enum Reason { MANUAL, DURATION_ELAPSED, RELOAD, SHUTDOWN }

    private static final HandlerList HANDLERS = new HandlerList();

    private final String eventId;
    private final String displayName;
    private final Reason reason;

    public MiraEventStoppedEvent(String eventId, String displayName, Reason reason) {
        this.eventId = eventId;
        this.displayName = displayName;
        this.reason = reason == null ? Reason.MANUAL : reason;
    }

    public String eventId() { return eventId; }
    public String displayName() { return displayName; }
    public Reason reason() { return reason; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
