# MiraEvents

MiraEvents is the central scheduled-event and server-operations controller for the Mira Paper server suite. It manages recurring/manually triggered gameplay events, rotating announcements, countdowns, persistent event state, command chains, and safe server restart scheduling.

## Download

[**Download MiraEvents v0.2.0**](https://github.com/FiveSOCE/Mira-Events/releases/download/v0.2.0/MiraEvents-0.2.0.jar)

[View All Releases](https://github.com/FiveSOCE/Mira-Events/releases)

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
| `/mevent announce <message>` | `miraevents.admin` | Sends a manual Mira announcement. |
| `/mevent restart status` | `miraevents.admin` | Shows the current restart schedule. |
| `/mevent restart <duration> [reason]` | `miraevents.admin` | Schedules a safe restart using values such as `30s`, `10m`, `2h` or `1d`. |
| `/mevent restart cancel` | `miraevents.admin` | Cancels the pending restart occurrence. |
| `/mevent reload` | `miraevents.admin` | Reloads event, announcement and restart configuration. |

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

## Announcements (0.2.0)

MiraAnnouncements is folded into MiraEvents instead of becoming another JAR. Rotating announcements are configured under `announcements` and can render through MiraCore's shared CHAT, ACTION_BAR or TITLE notification channels. Chat announcements may include a click command and hover text. Automatic rotation is disabled by default until configured.

## Safe Restarts (0.2.0)

MiraRestart is folded into MiraEvents. Manual restart schedules support configurable countdown thresholds and reasons. Immediately before shutdown MiraEvents fires `MiraRestartPrepareEvent` so other modules can persist last-second state, then saves players and loaded worlds before requesting normal Bukkit shutdown.

Optional automatic restart windows use a configurable IANA timezone and `HH:mm` times. They are disabled by default. The supplied default timezone is `Australia/Brisbane`.
