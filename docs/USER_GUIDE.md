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
   or Haunted. It shows on the bossbar (and in chat if `indicators.announceNight` is on).
2. **Players get into bed.** After the usual 5-second vanilla sleep timer they
   count as asleep, and the meter starts to fill.
3. **The meter fills** at a speed set by how many players are asleep, the
   night type, the difficulty preset and the sleepers' shared dream. It speeds up
   the longer someone sleeps, and awake players add to it by keeping watch
   (fighting, mining, exploring).
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
fill time = 20s + 40s × (1 − r) / r        capped at 240s
```

That is the **starting** speed. The longer someone stays asleep, the faster
the meter fills: the speed **doubles every 40 seconds**, up to ×8. And there
is a hard limit: **dawn comes at most 2 minutes after the first player falls
asleep**, whatever the night, the dreams or Unrest. Those only decide how much
sooner.

| Asleep | Typical time to dawn (Hard preset, Unrest 0) |
|---|---|
| everyone | 5 seconds |
| 3 of 4 | about 0:27 |
| 1 of 2 | about 0:39 |
| 1 of 3 | about 0:54 |
| 1 of 4 | about 1:04 |
| 1 of 8 or fewer | about 1:20, never more than 2:00 |

These include dream luck and the mix of night types; higher Unrest makes
them longer, up to the 2-minute limit (see DESIGN.md §12 for the full table). A night lasts about
9 minutes (546 seconds). Awake players can bring the dawn closer still by
keeping watch (below).

- The **night type** multiplies the speed (×1.3 on Calm, ×0.6 on Haunted).
- The **ramp** keeps building as long as anyone is asleep, even through a
  Restless dream. While nobody is in bed it winds back down at the same rate.
- The **difficulty preset** scales the time: Easy ×0.5, Normal ×0.75,
  Hard ×1.0. By default it follows the world difficulty.
- The **2-minute limit** works like a floor under the meter: after 1 minute
  asleep it is at least 50%, after 2 minutes it is full. Like the ramp, it
  winds back while nobody is in bed.
- If nobody is asleep, the meter **drains**: from full to empty in 60 seconds.
- The meter resets at the start of each night and after each skip.
- Chimes play when it passes 50% and 90%.

### Night watch: awake players help

Staying up doesn't have to mean holding everyone back. While at least one
player is asleep, every awake, counted player in the Overworld pushes the
meter toward dawn by keeping watch:

| Deed | Meter added |
|---|---|
| Killing a hostile mob (zombie, skeleton, creeper, …) | +3% |
| Entering a chunk you haven't been in yet tonight | +0.5% |
| Breaking a block (not ones that break instantly, like grass or torches) | +0.2% |

Each awake player can add **up to 20%** a night. Their actionbar shows what
took off the wait (`Night watch −6s (+3%) · dawn in 0:42 · your watch 12/20%`), and hovering
over the *night passes* message lists everyone's share. A watch-assisted skip
is still a partial skip for Unrest. Nothing counts when nobody is asleep, and
creative players don't count.

---

## 4. Night types

| Night | Chance at Unrest 0 | Chance at Unrest 5 | Meter speed | Special rule |
|---|---|---|---|---|
| **Calm** | 25% | 5% | ×1.3 | — |
| **Normal** | 40% | 25% | ×1.0 | — |
| **Restless** | 22% | 42% | ×0.7 | — |
| **Haunted** | 8% | 23% | ×0.6 | With **under half** of the counted players asleep, the meter fills at half that speed again (×0.3) |
| **Fever Dream** | 5% (10% on a full moon) | 5% | ×1.0 | Dreams roll every 3–7 seconds and hit **three times as hard**: Deep counts 4×, a chain up to 6×, Restless counts 0×. Tension builds twice as fast |

A whole-server sleep still skips a Haunted night (this can be turned off).

A **Fever Dream** is always announced, with thunder (harmless lightning), and
its bossbar flashes purple and pink:
*✧ The air shimmers. A fever dream takes hold… Dreams come fast and wild tonight.*

### The moon

The moon phase colours every dream of the night. A **full moon** makes
dreams wild: Deep sleep and bad dreams both get likelier, and a Fever Dream
twice as likely. A **new moon** makes them calm and steady. The phases in
between are in between. The bossbar shows the current phase (🌕 🌖 🌗 🌘 🌑 🌒 🌓 🌔)
and `/unrest info` names it.

Chat messages at dusk, only if `indicators.announceNight` is on (it is off by default):

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

The sleepers dream **together**. At random moments (every 8 to 22 seconds,
the first one 2 seconds after someone falls asleep) they make one shared
roll, which decides how much every sleeper counts towards the meter until
the next one. A good roll speeds everyone up; a bad one slows everyone down.

### The roll

The roll is a number from **0 to 100**, and high is good:

| Number (half the server asleep) | Dream | Effect on everyone asleep |
|---|---|---|
| 80–100 | **Deep** | Each sleeper counts as 2 (more in a chain) |
| 23–79 | **Sound** | Each sleeper counts as 1 |
| 3–22 | **Restless** | Each sleeper counts as ½ |
| 0–2 | **Nightmare** | The meter loses 10%, the **unluckiest** sleeper wakes up, and the Reaper may come for an awake player |

The bands move: more sleepers widen Deep; Unrest widens the bad bands; Luck
and the moon move them too.

**It's a show.** A second before each roll, sleepers see a number spinning
across the screen with a rising click, then the real number lands, coloured
by the result, with a sound: a level-up for Deep (a fanfare for a chain or a
jackpot), a bass note for Restless, a wither groan for a Nightmare. Land within
3 of a band and it says *so close!* Set `dreams.spin=all` to show it to
everyone in the Overworld, or `off`.

**Luck matters.** The roll uses the sleepers' combined vanilla **Luck**: each
level of a *Potion of Luck* is +1, each level of *Bad Luck* is −1, and modded
items that add Luck count too (up to ±5 in total). Every point of Luck adds 4%
to Deep and takes 15% off the Restless and Nightmare chances; bad luck does the
opposite. When a Nightmare hits, it wakes whoever has the lowest Luck.

### Momentum

Bad luck builds toward a comeback, and good luck snowballs:

- **Tension and release.** Each Restless or Nightmare roll stores 1 tension
  (up to 5). The next Deep roll releases it all at once: **+5% dawn per
  point**. *⚡ The tension breaks! +15%*, with thunder at 3 or more.
- **Deep chains.** Deep rolls in a row count 2×, then 3×, then 4×.
  *⛓ chain ×3*.
- **Night terror.** Three Restless rolls in a row turn into a Nightmare, but
  the next roll is a guaranteed Deep that releases all the stored tension.
- **Rubber band.** While the meter only just leads the 2-minute line, the
  sleepers get +1 Luck; when it's far ahead, −1.
- **Double or nothing.** Once a night, when the meter passes 50%, everyone
  sees a 5-second countdown, then a number from 1 to 100. **Win** (50%,
  +5% per point of Luck, between 25% and 75%) and the dawn jumps to 75%;
  **lose** and it falls back to 25%, but the ramp doubles for the rest of
  the night. The 2-minute limit holds either way.

The bossbar shows what's building: `⚡2` tension, `⛓×3` chain, `☠0:42` the
Reaper, `🎲3` the countdown.

### The Reaper

A Nightmare sends the dream after the awake players. A wither skeleton
called **☠ The Reaper**, carrying a hoe for a scythe and glowing, appears with a
lightning flash near a random awake player and hunts them. **Kill it within
60 seconds** and the dawn comes 20% closer; let it get away and it fades,
taking 5% with it. It drops nothing. Only one hunts at a time, and none comes
on Peaceful or when nobody is awake. Its hits inflict Wither, so on a
hardcore world take it seriously. Turn it off with `reaper.enabled=false`.

### In chat

Each roll is announced as one coloured line:

```
💭 🎲 91 Steve and Alex sink into a deep sleep together. [each counts 3x] ⛓ chain ×2 ⚡ The tension breaks! +15% (luck +2)
💭 🎲 1 A nightmare grips Steve and Alex! Alex wakes up in a cold sweat. [dawn −10%] ⚡ tension 3/5
☠ Alex's nightmare breaks loose: the Reaper hunts Bob! Kill it within 60s: dawn +20%.
```

Deep is aqua, Sound gray, Restless yellow and a Nightmare red; the Luck tag is
green or dark red. By default a roll is only announced when the dream changes,
and Nightmares, chains, releases and Night terrors always are. Hover over the
number to see the bands and what moved them, or over the `[effect]` to see each
sleeper's Luck. `dreams.announce` can be `all` or `off` (Nightmares only).

Dreams don't apply to a whole-server sleep.

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

**Leaving doesn't help.** If an awake player logs out or goes to the Nether
or the End at night, they keep counting as **awake** until they would have
gone AFK: 5 minutes after the last thing they did. So logging out to let the
others skip faster gets you nothing you wouldn't get by standing still. While
someone who left still counts, vanilla's instant "everyone is asleep" skip is
blocked too. Coming back ends it straight away. Hover over a *lies down*
message, or run `/unrest info`, to see who left and how long they still count.

---

## 9. What players see

All of these use normal vanilla features, so they work on unmodded clients.

| Where | What |
|---|---|
| **Bossbar** | `🌙 Restless Night — Steve, Alex +2 asleep — 42%`. Shown while anyone is in bed. Green on Calm, blue on Normal, yellow on Restless, red on Haunted. |
| **Chat** | `Steve lies down (3/7) — dawn in ~1:20`. Hover over it to see who's asleep and who's awake. Also dreams (`💭 Steve sinks into a deep sleep.`), Nightmares, and `☀ The night passes. (3/7 asleep, Restless Night, 1:42)`. |
| **Actionbar** | Awake players see `3 players are waiting for you to sleep — or keep watch: fight, mine, explore` every 5 seconds. Nobody is named. Right after a watch deed they see what it added instead. |
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
| `baseSeconds` | `20.0` | The minimum fill time (with almost everyone asleep) |
| `scaleSeconds` | `40.0` | How much each missing sleeper adds. Bigger = slower partial skips |
| `capSeconds` | `240.0` | The longest fill time |
| `wholeServerSeconds` | `5.0` | Delay before a whole-server sleep skips |
| `drainPerSecond` | `0.016667` | How fast the meter empties when nobody is asleep (1/60 = 60s) |
| `rampDoubleSeconds` | `40.0` | The fill speed doubles every this many seconds someone is asleep. `0` turns the ramp off |
| `rampMax` | `8.0` | The most the ramp can speed the meter up |
| `maxSleepSeconds` | `120.0` | The time limit: a partial skip never takes longer than this after someone falls asleep. `0` turns it off |

### presets

| Key | Default | Meaning |
|---|---|---|
| `easyScale` | `0.5` | Fill time × this on Easy (and Peaceful) |
| `normalScale` | `0.75` | Fill time × this on Normal |
| `hardScale` | `1.0` | Fill time × this on Hard |

### nights

For each of `calm`, `normal`, `restless`, `haunted` and `fever`:

| Key | Defaults (calm / normal / restless / haunted / fever) | Meaning |
|---|---|---|
| `<type>Weight` | 25 / 40 / 22 / 8 / 5 | Roll weight at Unrest 0 |
| `<type>PerDebt` | −4 / −3 / +4 / +3 / 0 | Weight change per point of Unrest |
| `<type>Min` | 5 / 20 / 0 / 0 / 0 | The lowest the weight can go |
| `<type>Multiplier` | 1.3 / 1.0 / 0.7 / 0.6 / 1.0 | Meter speed on that night |

| Key | Default | Meaning |
|---|---|---|
| `hauntedMinRatio` | `0.5` | Share of counted players asleep below which a Haunted night is slowed further |
| `hauntedBelowRatioMultiplier` | `0.5` | Extra slow-down below `hauntedMinRatio`. `0` stops the meter filling (the old behaviour) |
| `hauntedAllowsWholeServerSleep` | `true` | Whether a whole-server sleep skips a Haunted night |
| `feverIntensity` | `3.0` | How many times as strong every dream effect is on a Fever Dream |
| `feverRollMinSeconds`, `feverRollMaxSeconds` | 3, 7 | Gap between rolls on a Fever Dream |
| `feverFullMoonBonus` | `2.0` | The Fever Dream weight is multiplied by this on a full moon |

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
| `enabled` | `true` | Turns the shared dream roll off (every sleeper then counts as 1) |
| `rollMinSeconds`, `rollMaxSeconds` | 8, 22 | Each gap between rolls is random in this range |
| `spin` | `sleepers` | Who sees the spinning number before each roll: `sleepers`, `all` or `off` |
| `deepBase`, `deepPerRatio`, `deepPerDebt`, `deepMin` | 0.10, 0.20, −0.02, 0.05 | Deep chance = base + perRatio × r + perDebt × Unrest, at least min |
| `restlessBase`, `restlessPerRatio`, `restlessPerDebt` | 0.30, −0.20, 0.03 | Restless chance, same shape (then scaled by Luck) |
| `nightmareBase`, `nightmarePerDebt` | 0.06, 0.3 | Nightmare chance = base × (1 − r) × (1 + perDebt × Unrest) (then scaled by Luck) |
| `nightmareMeterLoss` | `0.10` | Meter lost on a Nightmare |
| `deepWeight`, `restlessWeight` | 2.0, 0.5 | How much each sleeper counts after a Deep or a Restless roll |
| `luckDeep` | `0.04` | Deep chance added per point of combined Luck |
| `luckBad` | `0.15` | Share of the Restless and Nightmare chances taken off per point of Luck (added per point of bad luck) |
| `luckMax` | `5.0` | Combined Luck counts up to this much either way |
| `announce` | `changes` | The shared roll in chat: `off` (Nightmares only), `changes` (when the dream changes) or `all` |
| `nightmareSound` | `true` | A quiet sound for the player a Nightmare wakes (needs `indicators.sounds`) |

### momentum

| Key | Default | Meaning |
|---|---|---|
| `tension` | `true` | Bad rolls store tension; the next Deep releases it |
| `tensionPerBadRoll`, `tensionMax` | 1.0, 5.0 | Tension per bad roll, and the most stored |
| `tensionPayout` | `0.05` | Meter added per point of tension released |
| `chains` | `true` | Deep rolls in a row count 2×, 3×, 4× |
| `chainMaxWeight` | `4.0` | The most a chain can count each sleeper |
| `nightTerrorStreak` | `3` | Restless rolls in a row that become a Night terror (0 = never) |
| `rubberBand` | `1.0` | Luck added while the meter rides the 2-minute line, taken when far ahead (0 = off) |
| `doubleOrNothing` | `true` | The once-a-night flip |
| `gambleAt` | `0.5` | Meter at which it starts |
| `gambleCountdownSeconds` | `5` | Countdown before the flip |
| `gambleWinMeter`, `gambleLoseMeter` | 0.75, 0.25 | Meter after a win, after a loss |
| `gambleLuck` | `0.05` | Win chance per point of the sleepers' Luck |

### moon

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | The moon phase affects the dreams |
| `moonDeep` | `0.05` | Deep chance added on a full moon (taken on a new moon) |
| `moonBad` | `0.25` | Share added to the bad chances on a full moon (taken on a new moon) |

### reaper

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | A Nightmare sends the Reaper after an awake player |
| `seconds` | `60` | How long it hunts before it fades |
| `reward` | `0.20` | Meter added when a player kills it |
| `penalty` | `0.05` | Meter lost when it fades unkilled |
| `health` | `30.0` | Its health |

### nightWatch

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Awake players add to the meter while someone sleeps |
| `killHostile` | `0.03` | Meter added per hostile mob killed |
| `breakBlock` | `0.002` | Meter added per block broken (instant-break blocks don't count) |
| `newChunk` | `0.005` | Meter added per chunk a player enters for the first time that night |
| `maxPerPlayer` | `0.20` | Most one awake player can add in a night |
| `feedback` | `true` | Show players what their watch added, on the actionbar |

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

### antiDodge

| Key | Default | Meaning |
|---|---|---|
| `countLeavers` | `true` | An awake player who logs out keeps counting as awake |
| `countDimensionLeavers` | `true` | The same for an awake player who leaves the Overworld |
| `graceSeconds` | `300` | How long, from their last activity. Keep it equal to `afk.timeoutSeconds` so leaving is never better than idling |

### indicators

| Key | Default | Meaning |
|---|---|---|
| `bossbar` | `true` | Show the bossbar |
| `alwaysAtNight` | `false` | Show the bossbar all night, not only while someone is in bed |
| `maxNames` | `3` | Sleeper names on the bossbar before `+N` |
| `chat` | `true` | Chat messages |
| `announceNight` | `false` | Also post the night type in chat at dusk (*Night falls.*, *The night is calm.*). The bossbar and `/unrest` show it either way. |
| `actionbar` | `true` | The "players are waiting" nudge |
| `actionbarIntervalSeconds` | `5` | How often the nudge is shown |
| `tabList` | `true` | The `[Zz]` tag |
| `tabPrefix` | `§7[Zz]§r` | The tag text (`§` colour codes allowed) |
| `particles` | `true` | Cloud puffs above sleepers |
| `sounds` | `true` | Chimes at 50% and 90% |
| `asciiOnly` | `false` | Replace 🌙 ☀ 💤 💭 ● 🎲 and the moon phases with plain text |
| `lightning` | `true` | Harmless lightning (no fire, no damage) for jackpots, the Reaper and Fever Dreams |

### commands and logging

| Key | Default | Meaning |
|---|---|---|
| `commands.registerSleepAlias` | `true` | Also register `/sleep` (needs a restart) |
| `logging.nightLog` | `true` | Write `logs/unrest-nights.csv` |

### Tuning tips

- **Still too long?** Lower `maxSleepSeconds` (e.g. 90). It's the longest
  anyone waits.
- **Too easy now?** Raise `maxSleepSeconds` (e.g. 180) or `rampDoubleSeconds`.
  For the original v1 meter set `maxSleepSeconds=0`, `rampDoubleSeconds=0`,
  `baseSeconds=30`, `scaleSeconds=60` and `capSeconds=520`.
- **Lone sleepers on a big server should never skip?** Set
  `maxSleepSeconds=0`, `rampDoubleSeconds=0` and raise `capSeconds` above the
  night length even on a Calm night (546s × 1.3 ≈ 710s, so e.g. `750`).
- **Luck too strong or too weak?** Change `luckDeep` and `luckBad`; set both
  to 0 to ignore Luck.
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
| `countedN` | Counted players (§8), including leavers who still count |
| `sleepersAtSkip`, `ratioAtSkip` | Sleepers and their share at the end |
| `afkCount` | AFK players in the Overworld |
| `peakSleepers` | Most sleepers at once that night |
| `nightmares` | Nightmare wake-ups |
| `hungerLostTotal` | Food points lost by all sleepers |
| `watchTotal` | Meter added by awake players keeping watch (0.15 = 15%) |

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
