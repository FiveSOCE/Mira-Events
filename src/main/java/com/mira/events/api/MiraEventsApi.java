package com.mira.events.api;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import com.mira.core.api.NotificationService;

public interface MiraEventsApi {
    Set<String> eventIds();
    Set<String> activeEventIds();
    Optional<String> nextEventId();
    Optional<String> displayName(String eventId);
    boolean isActive(String eventId);
    OptionalLong activeUntil(String eventId);
    OptionalLong nextStart(String eventId);
    boolean start(String eventId);
    boolean stop(String eventId);

    void announce(String message, NotificationService.Channel channel);
    OptionalLong restartAt();
    boolean scheduleRestart(long delaySeconds, String reason);
    boolean cancelRestart();
}
