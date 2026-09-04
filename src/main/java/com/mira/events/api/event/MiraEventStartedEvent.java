package com.mira.events.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class MiraEventStartedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String eventId;
    private final String displayName;
    private final boolean manual;
    private final long activeUntil;

    public MiraEventStartedEvent(String eventId, String displayName, boolean manual, long activeUntil) {
        this.eventId = eventId;
        this.displayName = displayName;
        this.manual = manual;
        this.activeUntil = activeUntil;
    }

    public String eventId() { return eventId; }
    public String displayName() { return displayName; }
    public boolean manual() { return manual; }
    public long activeUntil() { return activeUntil; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
