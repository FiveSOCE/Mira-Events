# MiraEvents

MiraEvents is the central scheduled-event controller for the Mira Paper server suite. It manages recurring and manually triggered server events, countdowns, persistent event state, and command chains that can coordinate other Mira plugins.

## Download

[**Download MiraEvents v0.1.1**](https://github.com/FiveSOCE/Mira-Events/releases/download/v0.1.1/MiraEvents-0.1.1.jar)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21
- PlaceholderAPI optional
- MiraCore 0.2.0 or newer
- MiraShop optional integration
- MiraKits optional integration
- MiraItems optional integration
- MiraTags optional integration
- MiraCrates optional integration
- MiraPinata optional integration
- MiraNPC optional integration

## How MiraEvents Works

Event definitions are stored in `plugins/MiraEvents/events.yml`, while current/next runtime schedule state is stored in `plugins/MiraEvents/state.yml`. Events can be scheduled or recurring, broadcast countdowns before starting, execute console command chains on start/end, and expose active/next-event state through PlaceholderAPI and the public `MiraEventsApi`.

Because the event controller executes configured command chains instead of directly owning every event mechanic, it can trigger systems such as shops, kits, items, tags, crates, Pinata events, NPC states and future Mira modules without hard-coupling those plugins together.

v0.1.1 registers MiraEvents and its API through MiraCore, writes event start/stop actions to the Core audit trail and emits typed Bukkit `MiraEventStartedEvent` / `MiraEventStoppedEvent` lifecycle events for direct integrations that should not parse commands. The public API now also exposes active event IDs, the next scheduled event ID and display-name lookup while preserving restart-safe active/next state.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/mevent list` | `miraevents.use` | Lists configured/available events. |
| `/mevent info <id>` | `miraevents.use` | Shows information and schedule state for an event. |
| `/mevent start <id>` | `miraevents.admin` | Manually starts an event. |
| `/mevent stop <id>` | `miraevents.admin` | Manually stops an active event. |
| `/mevent reload` | `miraevents.admin` | Reloads MiraEvents configuration and definitions. |

Aliases: `/miraevent`, `/mevents`

## Permissions

| Permission | Default | What it does |
| --- | --- | --- |
| `miraevents.use` | Everyone | Allows normal event status/list/info commands. |
| `miraevents.admin` | OP | Allows starting, stopping and reloading events. |


## API / Lifecycle Events

`MiraEventsApi` is available through both Bukkit services and MiraCore. In addition to starting/stopping events, integrations can query all event IDs, active IDs, the next scheduled event, display names, active-until timestamps and next-start timestamps.

Bukkit integrations may listen for:

- `MiraEventStartedEvent` with event ID, display name, manual/scheduled source and active-until timestamp.
- `MiraEventStoppedEvent` with event ID, display name and stop reason.
