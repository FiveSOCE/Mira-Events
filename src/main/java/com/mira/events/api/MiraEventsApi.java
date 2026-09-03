package com.mira.events.api;

import java.util.OptionalLong;
import java.util.Set;

public interface MiraEventsApi {
    Set<String> eventIds();
    boolean isActive(String eventId);
    OptionalLong activeUntil(String eventId);
    OptionalLong nextStart(String eventId);
    boolean start(String eventId);
    boolean stop(String eventId);
}
