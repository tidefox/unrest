# Unrest

A server-side sleep overhaul for hardcore **Minecraft 1.12.2 Forge** servers.

The night no longer skips the moment enough players are in bed. Instead, a **sleep
meter** fills, and it fills faster the more of the server is asleep. A lone sleeper
on a busy server can still bring the dawn, but it takes almost the whole night.
Each night has a randomly rolled character. Skipping without the whole server
asleep builds **Unrest**, which makes later nights harder to skip.

> **Status:** design complete, implementation in progress. See [DESIGN.md](DESIGN.md).

## Features (v1)

- **Sleep meter.** The time to dawn depends on the share of players asleep. With everyone asleep it takes 5 seconds; with half the server asleep it takes about 1½ minutes; with only a few asleep it takes most of the night.
- **Night types,** rolled at dusk: Calm, Normal, Restless or Haunted. A Haunted night needs at least half the server in bed.
- **Unrest (Night Debt).** Partial skips build it up. A whole-server sleep, or a night that ends on its own with no skip, brings it down. Higher Unrest makes Restless and Haunted nights, and restless dreams, more likely.
- **Dream rolls.** Every 15 seconds each sleeper rolls for Deep sleep, Restless sleep or a Nightmare.
- **Sleeping costs food.** Sleepers wake hungrier the more hours the skip covered. A skip never takes anyone below 3 food points.
- **AFK-aware.** AFK players don't count against the skip, and sleepers must have been active just before getting into bed.
- **Shows who's asleep,** using only vanilla displays: a bossbar, chat messages, an actionbar nudge, a `[Zz]` tag in the tab list, particles, and an `/unrest` menu showing every player's head and status.
- **A/B ready.** `observe` mode leaves vanilla sleep unchanged but logs every night to CSV, so you can compare it with `active` mode on a live server.

## Requirements

- Minecraft **1.12.2**
- Forge **14.23.5.x**
- Install it on the **server only**. Clients don't need it.
- No other dependencies.

## Installation

1. Put `unrest-<version>.jar` in the server's `mods/` folder.
2. Start the server once to generate `config/unrest.cfg`.
3. Adjust the config if you want to, then run `/unrest reload`.

Don't run Unrest alongside another server mod that changes how nights are skipped.

## Commands

| Command | Who | What it does |
|---|---|---|
| `/unrest` (alias `/sleep`) | everyone | Opens the menu showing every player's head and status |
| `/unrest info` | everyone | Shows the meter, night type and Unrest in chat |
| `/unrest reload` | op | Reloads the config |
| `/unrest debt get\|set <v>` | op | Reads or sets Unrest |
| `/unrest mode active\|observe` | op | Switches between the full mechanic and logging only |

## Balancing

`tools/simulate.py` runs a Monte Carlo simulation of the meter using the default
values:

```bash
python3 tools/simulate.py --runs 4000
```

## Building

```bash
./gradlew build
```

The jar is written to `build/libs/`. The Gradle project setup is still to come.

## Licence

[MIT](LICENSE)
