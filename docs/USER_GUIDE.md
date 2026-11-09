# Unrest — User Guide

Unrest changes how nights are skipped on a Minecraft **1.12.2 Forge** server.
In vanilla, the night only skips when everyone is in bed. Unrest replaces that
with a **sleep meter**: the more of the server is asleep, the faster the night
passes. Skipping without everyone asleep builds **Unrest**, which makes later
nights harder.

- **Server-side only.** Install it on the server; players join with an
  unmodded (or differently modded) client. It also works in singleplayer.
- **No dependencies.** Just Forge 14.23.5.x.

---

## Contents

1. [Installing](#1-installing)
2. [How a night works](#2-how-a-night-works)
3. [The sleep meter](#3-the-sleep-meter)
4. [Night types](#4-night-types)
5. [Unrest (Night Debt)](#5-unrest-night-debt)
6. [Dreams and Nightmares](#6-dreams-and-nightmares)
7. [The cost of sleeping](#7-the-cost-of-sleeping)
8. [Who counts: AFK, creative, other dimensions](#8-who-counts-afk-creative-other-dimensions)
9. [What players see](#9-what-players-see)
10. [Commands](#10-commands)
11. [Configuration reference](#11-configuration-reference)
12. [Observe mode and the night log](#12-observe-mode-and-the-night-log)
13. [Compatibility with other mods](#13-compatibility-with-other-mods)
14. [FAQ](#14-faq)

---

## 1. Installing

1. Put `unrest-<version>.jar` in the server's `mods/` folder.
2. Start the server once. This creates `config/unrest.cfg`.
3. Change the config if you want, then run `/unrest reload` (or restart).

Clients don't need the mod. If you also put it in a client's `mods/` folder,
it does nothing there except in singleplayer.

---

## 2. How a night works

1. **Dusk (time 12541).** The night's type is rolled: Calm, Normal, Restless
   or Haunted. It's announced in chat.
2. **Players get into bed.** After the usual 5-second vanilla sleep timer they
   count as asleep, and the meter starts to fill.
3. **The meter fills** at a speed set by how many players are asleep, the
   night type, the difficulty preset and each sleeper's dreams.
4. **At 100% the night is skipped.** Time jumps to the next morning, the
   weather clears, everyone in bed wakes up, sleepers lose some food, and
   Unrest goes up or down.
5. **If nobody fills the meter**, the night ends on its own at dawn and Unrest
   goes down.

If **everyone** who counts is asleep, the meter is ignored and the night
skips after **5 seconds**, like vanilla.

A thunderstorm during the day lets players sleep as in vanilla. Unrest treats
that as a Normal night.

---

## 3. The sleep meter

The time to fill the meter depends on the share of counted players asleep
(`r = asleep / counted`):

```
fill time = 30s + 60s × (1 − r) / r        capped at 520s
```

| Asleep | Time to dawn (Hard preset, Normal night) |
|---|---|
| everyone | 5 seconds |
| 3 of 4 | 50s |
| 2 of 3 | 1:00 |
| 1 of 2 | 1:30 |
| 1 of 3 | 2:30 |
| 1 of 4 | 3:30 |
| 1 of 8 | 7:30 |
| 1 of 10 or fewer | 8:40 (the cap) |

A night lasts about 9 minutes (546 seconds). So on a big server, a single
sleeper only shortens the night a little, and only if they went to bed at dusk.

- The **night type** multiplies the speed (×1.3 on Calm, ×0.6 on Haunted).
- The **difficulty preset** scales the time: Easy ×0.5, Normal ×0.75,
  Hard ×1.0. By default it follows the world difficulty.
- If nobody is asleep (or every sleeper is having a Restless dream), the
  meter **drains**: from full to empty in 60 seconds.
- The meter resets at the start of each night and after each skip.
- Chimes play when it passes 50% and 90%.

---

## 4. Night types

| Night | Chance at Unrest 0 | Chance at Unrest 5 | Meter speed | Special rule |
|---|---|---|---|---|
| **Calm** | 25% | 5% | ×1.3 | — |
| **Normal** | 45% | 30% | ×1.0 | — |
| **Restless** | 22% | 42% | ×0.7 | — |
| **Haunted** | 8% | 23% | ×0.6 | The meter only fills if **at least half** of the counted players are asleep |

A whole-server sleep still skips a Haunted night (this can be turned off).

Chat messages at dusk:

- Calm: *The night is calm.*
- Normal: *Night falls.*
- Restless: *The night feels restless…*
- Haunted: *Something is wrong with this night.*

---

## 5. Unrest (Night Debt)

Unrest is one number for the whole world, from **0.0 to 5.0**. It is saved
with the world.

| What happened | Change |
|---|---|
| A **partial skip** (not everyone asleep) | **+ (1 − r)**. 1 of 2 asleep → +0.5; 1 of 4 → +0.75 |
| A **whole-server sleep** | **− 1.5** |
| A night that **ends on its own** | **− 1.0** |
| The time is changed by a command (e.g. `/time set day`) | no change |

Unrest has **no direct penalty**. It only changes the odds:

- Restless and Haunted nights become more likely.
- Restless dreams and Nightmares become more likely.
- Deep sleep becomes less likely.

So a server that keeps skipping nights with one sleeper will soon get nights
that are hard to skip, until everyone sleeps together or lets a night pass.

---

## 6. Dreams and Nightmares

Every 15 seconds each sleeper has a dream. It decides how much that sleeper
counts towards the meter for the next 15 seconds.

| Dream | Chance | Effect |
|---|---|---|
| **Nightmare** | `3% × (1 − r) × (1 + 0.3 × Unrest)` | The sleeper wakes up, the meter loses 5%, and chat says *Steve wakes up in a cold sweat.* |
| **Restless** | `30% − 20% × r + 3% × Unrest` | Counts as 0 sleepers |
| **Deep** | `10% + 20% × r − 2% × Unrest` (at least 5%) | Counts as 2 sleepers |
| **Normal** | the rest | Counts as 1 sleeper |

With half the server asleep and no Unrest, Deep and Restless cancel out on
average. More sleepers make Deep sleep more likely; more Unrest makes bad
dreams more likely. A player woken by a Nightmare can get straight back into
bed.

Dreams don't apply to a whole-server sleep.

Dreams are announced in chat, one line per roll:

```
💭 Steve sinks into a deep sleep. Alex tosses and turns.
```

By default a sleeper is only mentioned when their dream changes (so a run of
ordinary sleep stays quiet). Hover over a name to see what the dream does, or
over 💭 to see the odds of each dream right now. Set `dreams.announce` to `all`
to hear every roll, or `off` to keep chat quiet. The `/unrest` menu also shows
each sleeper's current dream, and hovering over the *night passes* message
shows how many Deep, Restless and Nightmare dreams the night had.

---

## 7. The cost of sleeping

When the night is skipped, everyone who was asleep loses food:

```
loss = round(hours skipped / 2) + random −1, 0 or +1      (0 to 6 points)
```

Skipping a whole night (about 11 hours) costs about 5–6 food points (2½–3
drumsticks). A skip **never takes anyone below 3 food points**, and players
already at 3 or less lose nothing. Saturation isn't touched.

Turn it off with `hungerCost.enabled=false`.

---

## 8. Who counts: AFK, creative, other dimensions

**Counted players** are players who are:

- in the **Overworld**,
- in **survival or adventure** mode,
- **not AFK**.

Everyone else (creative, spectator, the Nether, the End, AFK players) is
ignored: they don't slow the night down, and they can't speed it up.

**AFK:** a player is AFK after **5 minutes** without doing anything. "Doing
something" means looking around, attacking, breaking or using blocks, using
items, chatting, running commands or opening containers. Being pushed by
water or mobs doesn't count. Players in bed are never AFK.

**Active before bed:** to count as a sleeper, a player must have done
something in the **3 minutes before** getting into bed. Clicking the bed
itself doesn't count. This stops a player from parking in a bed and walking
away to skip the night on their behalf.

If nobody counts (for example everyone online is AFK or in creative), the
meter pauses.

---

## 9. What players see

All of these use normal vanilla features, so they work on unmodded clients.

| Where | What |
|---|---|
| **Bossbar** | `🌙 Restless Night — Steve, Alex +2 asleep — 42%`. Shown while anyone is in bed. Green on Calm, blue on Normal, yellow on Restless, red on Haunted. |
| **Chat** | `Steve lies down (3/7) — dawn in ~1:20`. Hover over it to see who's asleep and who's awake. Also the night roll, dreams (`💭 Steve sinks into a deep sleep.`), Nightmares, and `☀ The night passes. (3/7 asleep, Restless Night, 1:42)`. |
| **Actionbar** | Awake players see `3 players are waiting for you to sleep` every 5 seconds. Nobody is named. |
| **Tab list** | `[Zz]` before the names of sleepers. |
| **Particles** | A small cloud above each sleeper. |
| **Sounds** | A chime at 50% and at 90%. |
| **`/unrest` menu** | A chest screen with every online player's head (see below). |

### The `/unrest` menu

Top row: a **clock** (night type and time to dawn), an **experience bottle**
(meter %), a **dye** (Unrest as `●●○○○`, green → red) and **paper** (how many
are asleep, the preset and the mode).

Below: one head per online player. Sleepers come first, then awake players,
then AFK players, then players in other dimensions. Each head says
`💤 Sleeping 1:20 · Deep sleep` (the dream appears after the first roll), `In bed`, `Awake`, `AFK (6m)` or `Elsewhere`. You can't
take anything out of the menu.

If the client's font can't show 🌙, ☀, 💤 or 💭, set `indicators.asciiOnly=true`.

---

## 10. Commands

| Command | Who | What it does |
|---|---|---|
| `/unrest` or `/sleep` | everyone | Opens the menu (in the console it prints the info instead) |
| `/unrest info` | everyone | Night type, time to dawn, meter, Unrest, asleep count, preset, mode |
| `/unrest reload` | ops | Re-reads `config/unrest.cfg` |
| `/unrest debt get` | ops | Shows Unrest |
| `/unrest debt set <0–5>` | ops | Sets Unrest |
| `/unrest mode active` | ops | Turns the full mechanic on |
| `/unrest mode observe` | ops | Vanilla sleep, but every night is logged (see §12) |

---

## 11. Configuration reference

File: `config/unrest.cfg`. Every setting can be reloaded with `/unrest reload`
except `registerSleepAlias`, which needs a restart.

### general

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Turns the whole mod on or off |
| `mode` | `active` | `active` = full mechanic; `observe` = vanilla sleep, logging only |
| `difficultyPreset` | `auto` | `auto` follows the world difficulty; or `easy`, `normal`, `hard` |

### meter

| Key | Default | Meaning |
|---|---|---|
| `baseSeconds` | `30.0` | The minimum fill time (with almost everyone asleep) |
| `scaleSeconds` | `60.0` | How much each missing sleeper adds. Bigger = slower partial skips |
| `capSeconds` | `520.0` | The longest fill time |
| `wholeServerSeconds` | `5.0` | Delay before a whole-server sleep skips |
| `drainPerSecond` | `0.016667` | How fast the meter empties when nobody is asleep (1/60 = 60s) |

### presets

| Key | Default | Meaning |
|---|---|---|
| `easyScale` | `0.5` | Fill time × this on Easy (and Peaceful) |
| `normalScale` | `0.75` | Fill time × this on Normal |
| `hardScale` | `1.0` | Fill time × this on Hard |

### nights

For each of `calm`, `normal`, `restless` and `haunted`:

| Key | Defaults (calm / normal / restless / haunted) | Meaning |
|---|---|---|
| `<type>Weight` | 25 / 45 / 22 / 8 | Roll weight at Unrest 0 |
| `<type>PerDebt` | −4 / −3 / +4 / +3 | Weight change per point of Unrest |
| `<type>Min` | 5 / 20 / 0 / 0 | The lowest the weight can go |
| `<type>Multiplier` | 1.3 / 1.0 / 0.7 / 0.6 | Meter speed on that night |

| Key | Default | Meaning |
|---|---|---|
| `hauntedMinRatio` | `0.5` | Share of counted players who must sleep for the meter to fill on a Haunted night |
| `hauntedAllowsWholeServerSleep` | `true` | Whether a whole-server sleep skips a Haunted night |

### debt

| Key | Default | Meaning |
|---|---|---|
| `max` | `5.0` | The highest Unrest can go |
| `partialSkipFactor` | `1.0` | Unrest += this × (1 − r) on a partial skip |
| `wholeServerRelief` | `1.5` | Unrest lost on a whole-server sleep |
| `naturalNightRelief` | `1.0` | Unrest lost when a night ends on its own |

### dreams

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Turns dream rolls off (every sleeper then counts as 1) |
| `rollIntervalSeconds` | `15` | Seconds between rolls |
| `deepBase`, `deepPerRatio`, `deepPerDebt`, `deepMin` | 0.10, 0.20, −0.02, 0.05 | Deep chance = base + perRatio × r + perDebt × Unrest, at least min |
| `restlessBase`, `restlessPerRatio`, `restlessPerDebt` | 0.30, −0.20, 0.03 | Restless chance, same shape |
| `nightmareBase`, `nightmarePerDebt` | 0.03, 0.3 | Nightmare chance = base × (1 − r) × (1 + perDebt × Unrest) |
| `nightmareMeterLoss` | `0.05` | Meter lost on a Nightmare |
| `announce` | `changes` | Dream rolls in chat: `off`, `changes` (only when a sleeper's dream changes) or `all` |
| `nightmareSound` | `true` | A quiet sound for the player a Nightmare wakes (needs `indicators.sounds`) |

### hungerCost

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Sleepers lose food on a skip |
| `hoursPerPoint` | `2.0` | Hours skipped per food point |
| `randomSpread` | `1` | Random ± added to the loss |
| `maxLoss` | `6` | Most food lost in one skip |
| `floor` | `3` | A skip never takes anyone below this |

### afk

| Key | Default | Meaning |
|---|---|---|
| `timeoutSeconds` | `300` | Seconds without activity before a player is AFK |
| `activeBeforeBedSeconds` | `180` | A sleeper must have been active this many seconds before getting into bed |

### indicators

| Key | Default | Meaning |
|---|---|---|
| `bossbar` | `true` | Show the bossbar |
| `alwaysAtNight` | `false` | Show the bossbar all night, not only while someone is in bed |
| `maxNames` | `3` | Sleeper names on the bossbar before `+N` |
| `chat` | `true` | Chat messages |
| `actionbar` | `true` | The "players are waiting" nudge |
| `actionbarIntervalSeconds` | `5` | How often the nudge is shown |
| `tabList` | `true` | The `[Zz]` tag |
| `tabPrefix` | `§7[Zz]§r` | The tag text (`§` colour codes allowed) |
| `particles` | `true` | Cloud puffs above sleepers |
| `sounds` | `true` | Chimes at 50% and 90% |
| `asciiOnly` | `false` | Replace 🌙 ☀ 💤 💭 ● with plain text |

### commands and logging

| Key | Default | Meaning |
|---|---|---|
| `commands.registerSleepAlias` | `true` | Also register `/sleep` (needs a restart) |
| `logging.nightLog` | `true` | Write `logs/unrest-nights.csv` |

### Tuning tips

- **Too hard for a small group?** Lower `scaleSeconds` (e.g. 40), or set
  `difficultyPreset=normal`.
- **Lone sleepers on a big server should never skip?** Raise `capSeconds`
  above the night length even on a Calm night (546s × 1.3 ≈ 710s, so e.g.
  `750`). A small share asleep then hits the cap and the night ends first.
- **Unrest builds too fast?** Lower `partialSkipFactor` or raise
  `naturalNightRelief`.
- **No surprises?** Set `dreams.enabled=false` and every `<type>Multiplier`
  to 1.0.

The repo's `tools/simulate.py` shows what a change does before you try it on
a live server.

---

## 12. Observe mode and the night log

`/unrest mode observe` puts vanilla sleep back: no meter, no rolls shown, no
hunger cost, no indicators, and Unrest doesn't change. Every night is still
logged in the same format. Run a week in `observe` and a week in `active` to
compare how your players actually sleep.

The log is `logs/unrest-nights.csv`, one row per night:

| Column | Meaning |
|---|---|
| `timestamp` | When the night ended |
| `mode` | `active` or `observe` |
| `day` | The in-game day number |
| `nightType` | `calm`, `normal`, `restless` or `haunted` |
| `debtBefore`, `debtAfter` | Unrest before and after the night |
| `outcome` | `whole_server`, `partial`, `natural`, `vanilla_skip` (observe mode) or `external` (time changed by a command or another mod) |
| `secondsToSkip` | Seconds from dusk to the skip (empty if there was none) |
| `playersOnline` | Everyone online |
| `countedN` | Counted players (§8) |
| `sleepersAtSkip`, `ratioAtSkip` | Sleepers and their share at the end |
| `afkCount` | AFK players in the Overworld |
| `peakSleepers` | Most sleepers at once that night |
| `nightmares` | Nightmare wake-ups |
| `hungerLostTotal` | Food points lost by all sleepers |

---

## 13. Compatibility with other mods

- **Other sleep mods** (Morpheus, Sleeping Overhaul, …): don't use them with
  Unrest.
- **Quark:** works as it is. Quark's *Improved Sleeping* leaves AFK players
  out of vanilla's "everyone asleep" check. Unrest recognises those skips and
  counts them itself (whole-server or partial). At Quark's default
  `Required Percentage` of 100 that's a whole-server sleep. Below 100, Quark
  skips before the meter fills and Unrest warns about it in the server log.
  Both mods post sleep messages in chat.
- **Comforts:** sleeping bags work like beds. Daytime hammock naps aren't
  counted as night skips.
- **Bloodmoon:** players can't sleep during a blood moon, so that night
  usually ends on its own and lowers Unrest.
- **`doDaylightCycle false`** (e.g. GlobalGameRules): a skip wakes sleepers
  and clears the weather but doesn't move the clock, as in vanilla.
- **`/time set` or `/time add`** during a night ends it without changing
  Unrest; the log says `external`.

---

## 14. FAQ

**Do players need to install anything?** No.

**Does it work in singleplayer?** Yes. With one player every skip is a
whole-server sleep, so it plays like vanilla except for the hunger cost.

**Does it change the day length or tick speed?** No. It only decides when the
night is skipped.

**What happens if the server restarts at night?** The night type and meter
are kept; the night isn't rolled again.

**Why didn't my sleep count?** Check `/unrest`: you need to be in survival or
adventure, in the Overworld, in bed for 5 seconds, and active in the 3 minutes
before you lay down.

**How do I turn it off without removing it?** `/unrest mode observe` (keeps
logging) or `general.enabled=false` and `/unrest reload`.
