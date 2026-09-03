# MiraEvents

Central scheduled event controller for the Mira Paper 1.21.11 / Java 21 plugin suite.

## Download

Current release: **v0.1.0**

[**Download MiraEvents v0.1.0**](https://github.com/FiveSOCE/Mira-Events/releases/download/v0.1.0/MiraEvents-0.1.0.jar)

[View all releases](https://github.com/FiveSOCE/Mira-Events/releases)

## Features

- scheduled and recurring events
- persistent active/next-start state
- configurable countdown broadcasts
- console command chains on event start/end
- manual `/mevent start <id>` and `/mevent stop <id>`
- `/mevent list`, `/mevent info <id>`, `/mevent reload`
- PlaceholderAPI for active/next event data
- public `MiraEventsApi` through Bukkit ServicesManager
- deliberately decoupled from other Mira plugins, so event command chains can trigger MiraShop sales, event kits/items/tags, Pinata events, seasonal crates, NPC states and future systems

Definitions live in `plugins/MiraEvents/events.yml` and runtime schedule state in `plugins/MiraEvents/state.yml`.

## Requirements

- Paper 1.21.11
- Java 21
- PlaceholderAPI optional

## Building

```bash
gradle clean build
```

Output: `build/libs/MiraEvents-0.1.0.jar`
