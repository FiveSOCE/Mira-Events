# MiraEvents

MiraEvents is the central scheduled-event controller for the Mira Paper server suite. It manages recurring and manually triggered server events, countdowns, persistent event state, and command chains that can coordinate other Mira plugins.

## Download

[**Download MiraEvents v0.1.0**](https://github.com/FiveSOCE/Mira-Events/releases/download/v0.1.0/MiraEvents-0.1.0.jar)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21
- PlaceholderAPI optional
- MiraCore optional integration
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
