# Unrest — Design Specification

Unrest replaces the all-or-nothing night skip with a **sleep meter**. The more of
the server is asleep, the faster the meter fills. When it reaches 100%, the night
is skipped. Each night has a randomly rolled character, and skipping without
the whole server asleep builds **Unrest**, which makes later nights harder to
skip.

This document is the build specification for version 1.

---

## 0. Scope and constraints

| Constraint | Meaning |
|---|---|
| **Target** | Minecraft 1.12.2, Forge 14.23.5.x, Java 8 |
| **Server-only** | `@Mod(acceptableRemoteVersions = "*")`. Clients join without installing anything. |
| **Standalone** | No dependencies: no coremod, no Mixin, no scripting layer. |
| **Vanilla state only** | The mod reads only vanilla and Forge state (players, beds, world time, player activity). It never references another mod's classes, blocks or items. |
| **Changes only sleep** | The only things it changes are when the night is skipped, who is woken, the weather reset on skip, and the vanilla hunger bar of sleepers. It never changes the day/night speed or the tick rate. |
| **Singleplayer** | Runs on the integrated server too. With one player, every skip is a whole-server sleep, so it behaves like vanilla apart from the hunger cost. |

Everything that changes the world happens **only in the Overworld (dimension 0)**.

---

## 1. Terms

| Term | Definition |
|---|---|
| **Counted player** | An online player in the Overworld who is in survival or adventure mode and is not AFK (§6). |
| **N** | The number of counted players. |
| **Sleeper** | A counted player who is fully asleep (`isPlayerFullyAsleep()`, meaning they have been in bed for the 100-tick vanilla sleep timer) **and** was active in the 3 minutes before getting into bed (§6). |
| **S** | The number of sleepers. |
| **r** | The headcount ratio, `S / N`. |
| **Whole-server sleep** | `S == N` and `N ≥ 1`. |
| **Partial skip** | A skip that happens when `S < N`. |
| **Sleep window** | The time when vanilla allows sleeping: night (world time 12541–23458) or a thunderstorm. |
| **Night** | From world time 12541 to 23458 of each day. Its type is rolled at 12541. |
| **Unrest (D)** | Night Debt. A world-wide value from 0.0 to 5.0. |

---

## 2. Sleep meter

The meter is a value from 0.0 to 1.0, stored per world. It updates once per
second (every 20 ticks).

### 2.1 Fill time

```
T(r) = BASE + SCALE × (1 − r) / r        BASE = 30s, SCALE = 60s
T(r) = min(T(r), CAP)                    CAP  = 520s
```

| Asleep | T (Hard preset) |
|---|---|
| everyone | **5s** (whole-server sleep, §2.3) |
| 3 of 4 | 50s |
| 2 of 3 | 60s |
| 1 of 2 | 1m 30s |
| 1 of 3 | 2m 30s |
| 1 of 4 | 3m 30s |
| 1 of 8 | 7m 30s |
| 1 of 10 or more | 8m 40s (cap) |

A night lasts about 546 seconds. So a lone sleeper on a large server only
skips the last few seconds of the night, and only if they went to bed at dusk.

### 2.2 Each second

```
weight  = Σ dream-weight of each sleeper          (§4, normally 1.0 each)
r_eff   = min(1.0, weight / N)
if r_eff > 0:
    meter += nightMultiplier / (T(r_eff) × presetScale)
else:
    meter −= DRAIN_PER_SECOND                     (default 1/60: empty in 60s)
meter    = clamp(meter, 0, 1)
if meter ≥ 1: skip()                              (§2.4)
```

- The `nightMultiplier` comes from the night type (§3).
- `presetScale` comes from the difficulty preset (§8).
- On a **Haunted** night with `r < 0.5`, the meter does not fill; it only drains.
- The meter resets to 0 at the start of each night and after each skip.

### 2.3 Whole-server sleep

If `S == N`, the mod ignores the meter and skips after **5 seconds**. This
happens on every night type, including Haunted
(`hauntedAllowsWholeServerSleep = true`). Dream rolls don't apply.

### 2.4 The skip

1. `world.setWorldTime(t + 24000 − (t % 24000))`, which jumps to the next morning.
2. `world.provider.resetRainAndThunder()`.
3. For each player in bed in the Overworld, call `player.wakeUpPlayer(false, false, true)`.
4. Apply the hunger cost to sleepers (§5).
5. Update Unrest (§3.2) and log the night (§9).
6. Send a chat message: `☀ The night passes. (3/7 asleep, Restless Night, 1:42)`.

The mod decides when to skip in every case except one. Vanilla still skips on
its own when **every** player in the Overworld is fully asleep, AFK players
included. That situation is always a whole-server sleep, so it doesn't
conflict with this design. The mod doesn't need to stop it, which also means
it doesn't need a Mixin. Instead, the mod watches for it: in the first
second-tick after the jump, `PlayerWakeUpEvent` has fired and
`worldTime % 24000` has dropped below the previous value. The mod then runs
steps 4–6 as a whole-server sleep, so the hunger cost, Unrest and the log stay
correct.

---

## 3. Night types and Unrest

### 3.1 Night roll

At world time 12541 the mod rolls one night type, weighted by the current Unrest `D`:

| Night | Weight | Minimum | Meter multiplier | Extra rule |
|---|---|---|---|---|
| **Calm** | 25 − 4D | 5 | ×1.3 | — |
| **Normal** | 45 − 3D | 20 | ×1.0 | — |
| **Restless** | 22 + 4D | 0 | ×0.7 | — |
| **Haunted** | 8 + 3D | 0 | ×0.6 | The meter only fills if `r ≥ 0.5` |

The weights add up to 100 for any D from 0 to 5. At D = 0 the odds are
25 / 45 / 22 / 8 %. At D = 5 they are 5 / 30 / 42 / 23 %.

If a thunderstorm lets players sleep before 12541, that day uses **Normal**.

The roll is announced in chat and shown on the bossbar (§7):
- Calm: `The night is calm.`
- Normal: `Night falls.`
- Restless: `The night feels restless…`
- Haunted: `Something is wrong with this night.`

### 3.2 Unrest (Night Debt)

| Event | Change to D |
|---|---|
| Partial skip | `+ (1 − r)` using the headcount ratio at the moment of the skip. Example: 1 of 2 asleep → +0.5 |
| Whole-server sleep | `− 1.5` |
| A night ends on its own with no skip | `− 1.0` |

D is clamped to the range 0.0–5.0 and saved with the world. Unrest has **no
direct penalty**. It only changes the odds of the night roll (§3.1) and the
dream rolls (§4).

---

## 4. Dream rolls

Every 15 seconds, each sleeper rolls once. Here `r` is the headcount ratio and
`D` is Unrest.

| Roll | Chance | Effect for the next 15s |
|---|---|---|
| **Nightmare** | `0.03 × (1 − r) × (1 + 0.3D)` | The sleeper is woken (`wakeUpPlayer(true, true, false)`), the meter loses 5 %, and a chat message is sent: `Steve wakes up in a cold sweat.` |
| **Restless** | `0.30 − 0.20r + 0.03D` | That sleeper's weight is 0 |
| **Deep** | `max(0.05, 0.10 + 0.20r − 0.02D)` | That sleeper's weight is 2 |
| **Normal** | the remaining chance | That sleeper's weight is 1 |

Check the rolls in this order: Nightmare, then Restless, then Deep, then Normal.

- With half the server asleep and D = 0, Deep and Restless cancel out on average.
- More sleepers make Deep more likely.
- More Unrest makes Restless and Nightmare more likely.

A sleeper woken by a Nightmare can get back into bed straight away. They count
again once the vanilla sleep timer finishes, since they were active just before.

---

## 5. Cost of a skip (vanilla hunger)

When a skip happens, each sleeper loses food points:

```
hoursSkipped = ticksSkipped / 1000
loss         = round(hoursSkipped / 2) + random(−1, 0, +1)
loss         = clamp(loss, 0, 6)
food         = max(food − loss, 3)          only takes effect if food was above 3
```

This changes the vanilla `FoodStats` only. Saturation is left alone. It can be
turned off with `hungerCost.enabled`.

---

## 6. Activity, AFK and dimensions

- **Activity** is any of the following:
  - changing the direction the player is looking (yaw or pitch)
  - swinging an arm or attacking
  - breaking or using a block, or using an item
  - chatting or running a command
  - opening a container
- **Moving on its own doesn't count**, so water currents and being pushed don't make a player active.
- **AFK** means no activity for `afk.timeoutSeconds` (default 300). AFK players are not counted in N or S.
- **The active-before-bed rule:** a player in bed only counts as a sleeper if their last activity was no more than `afk.activeBeforeBedSeconds` (default 180) **before they got into bed**. Lying in bed doesn't count as activity, but being asleep doesn't make the player AFK either.
- Players **outside the Overworld**, in **creative** or in **spectator** mode are not counted.
- If `N == 0`, the meter is paused.

Activity is tracked with Forge events (`PlayerInteractEvent.*`,
`AttackEntityEvent`, `ServerChatEvent`, `CommandEvent`,
`PlayerContainerEvent.Open`, `BlockEvent.BreakEvent`). Looking around is
checked once per second by comparing `rotationYaw` and `rotationPitch`. The
last-activity time is kept in memory only.

---

## 7. Indicators (all vanilla)

| Element | Who sees it | Content |
|---|---|---|
| **Bossbar** (`BossInfoServer`, `NOTCHED_10`) | Counted players in the Overworld while at least one player is in bed (`bossbar.alwaysAtNight` makes it show for the whole night) | `🌙 Restless Night — Steve, Alex +2 asleep — 42%`. The bar shows the meter. Colour by night type: Calm green, Normal blue, Restless yellow, Haunted red. Up to `bossbar.maxNames` (3) names, then `+N`. |
| **Chat: into or out of bed** | Everyone in the Overworld | `Steve lies down (3/7) — dawn in ~1:20`. Hovering over it lists who is asleep and who is awake. |
| **Chat: events** | Everyone in the Overworld | The night roll, Nightmare wake-ups, and the skip summary. |
| **Actionbar** | Counted players who are awake, every 5s while S ≥ 1 | `3 players are waiting for you to sleep`. It doesn't name anyone. |
| **Tab list** | Everyone | `[Zz] ` in gray before sleepers' names, sent as a vanilla player-list display-name update (Forge 14.23.5 has no tab-list name event). No scoreboard teams are used. |
| **Particles** | Nearby players | A small cloud puff above each sleeper every 2s (`WorldServer.spawnParticle`). |
| **Sound** | Overworld players | A soft chime when the meter passes 50 % and 90 %. |

If the client font can't show 🌙 and ☀, `indicators.asciiOnly = true` replaces
them with `(night)` and `(dawn)`.

### 7.1 `/unrest` head menu

Running `/unrest` with no arguments opens a read-only 6-row chest screen:

- **Row 1 (info):**
  - Clock: the night type and the time until dawn
  - Experience bottle: the meter %
  - Coloured dye: Unrest shown as `●●○○○` plus the exact value
  - Paper: `S / N` and the preset
- **Rows 2–6:** one `minecraft:skull` (meta 3, `SkullOwner` set to the player's profile) for each online player. Sleepers come first, then awake players, then AFK players, then players in other dimensions. Each head's name is the player's name. Its lore is `💤 Sleeping 1:20`, `Awake`, `AFK (6m)` or `Elsewhere`.
- Every click is cancelled. A custom `Container` returns `ItemStack.EMPTY` from `slotClick` and refuses `transferStackInSlot`.

The skins are drawn by the client from the profile. On an offline-mode server
the heads show default skins.

### 7.2 Commands

| Command | Permission | What it does |
|---|---|---|
| `/unrest` | everyone | Opens the head menu |
| `/unrest info` | everyone | Shows the same information in chat |
| `/unrest reload` | op (level 2) | Reloads the config |
| `/unrest debt <get\|set <value>>` | op (level 2) | Reads or sets Unrest |
| `/unrest mode <active\|observe>` | op (level 2) | Switches mode for A/B testing (§9) |

The alias `/sleep` is registered unless `commands.registerSleepAlias = false`.

---

## 8. Configuration (`config/unrest.cfg`)

Forge `@Config`. Every value can be reloaded with `/unrest reload`.

```
general {
    B:enabled=true
    S:mode=active                      # active | observe (§9)
    S:difficultyPreset=auto            # auto | easy | normal | hard ; auto follows world difficulty
}
meter {
    D:baseSeconds=30.0
    D:scaleSeconds=60.0
    D:capSeconds=520.0
    D:wholeServerSeconds=5.0
    D:drainPerSecond=0.016667
}
presets {
    D:easyScale=0.5
    D:normalScale=0.75
    D:hardScale=1.0
}
nights {
    I:calmWeight=25        D:calmPerDebt=-4    I:calmMin=5      D:calmMultiplier=1.3
    I:normalWeight=45      D:normalPerDebt=-3  I:normalMin=20   D:normalMultiplier=1.0
    I:restlessWeight=22    D:restlessPerDebt=4 I:restlessMin=0  D:restlessMultiplier=0.7
    I:hauntedWeight=8      D:hauntedPerDebt=3  I:hauntedMin=0   D:hauntedMultiplier=0.6
    D:hauntedMinRatio=0.5
    B:hauntedAllowsWholeServerSleep=true
}
debt {
    D:max=5.0
    D:partialSkipFactor=1.0            # D += factor × (1 − r)
    D:wholeServerRelief=1.5
    D:naturalNightRelief=1.0
}
dreams {
    B:enabled=true
    I:rollIntervalSeconds=15
    D:deepBase=0.10      D:deepPerRatio=0.20      D:deepPerDebt=-0.02    D:deepMin=0.05
    D:restlessBase=0.30  D:restlessPerRatio=-0.20 D:restlessPerDebt=0.03
    D:nightmareBase=0.03 D:nightmarePerDebt=0.3
    D:nightmareMeterLoss=0.05
}
hungerCost {
    B:enabled=true
    D:hoursPerPoint=2.0
    I:randomSpread=1
    I:maxLoss=6
    I:floor=3
}
afk {
    I:timeoutSeconds=300
    I:activeBeforeBedSeconds=180
}
indicators {
    B:bossbar=true        B:alwaysAtNight=false   I:maxNames=3
    B:chat=true           B:actionbar=true        I:actionbarIntervalSeconds=5
    B:tabList=true        S:tabPrefix=§7[Zz]§r
    B:particles=true      B:sounds=true           B:asciiOnly=false
}
commands {
    B:registerSleepAlias=true
}
logging {
    B:nightLog=true
}
```

---

## 9. A/B testing and logging

### 9.1 Modes

| Mode | What it does |
|---|---|
| **`active`** | The full Unrest mechanic, as described above. |
| **`observe`** | Unrest doesn't change anything. Vanilla sleep works as normal, and no meter, rolls, hunger cost or indicators are shown. The night log still records every night with the same fields, so you get a baseline (the A side) to compare against `active` (the B side). |

You can switch modes live with `/unrest mode` or through the config.

### 9.2 Night log

The log is written to `logs/unrest-nights.csv`, one row per night, appending
to the file:

```
timestamp,mode,day,nightType,debtBefore,debtAfter,outcome,secondsToSkip,
playersOnline,countedN,sleepersAtSkip,ratioAtSkip,afkCount,peakSleepers,
nightmares,hungerLostTotal
```

`outcome` is one of `whole_server`, `partial`, `natural` or `vanilla_skip`.
`vanilla_skip` is only used in `observe` mode.

---

## 10. Persistence

`WorldSavedData` named `unrest`, saved on the Overworld:

| Key | Type |
|---|---|
| `debt` | double |
| `nightType` | string. The night type for the current night. A server restart in the middle of a night doesn't roll again. |
| `nightDay` | long. The day number the type was rolled for. |
| `meter` | double |

Bossbars and the activity times are rebuilt when the server starts.

---

## 11. Package layout

```
io.github.tidefox.unrest
├── Unrest.java                 @Mod entry, server-only, registers handlers & commands
├── config/UnrestConfig.java    @Config definitions + preset resolution
├── core/
│   ├── SleepTracker.java       counts N/S each second, dream weights, meter, skip
│   ├── NightRoller.java        night type roll at 12541, weights from debt
│   ├── DreamRoller.java        per-sleeper rolls every 15s
│   ├── SkipHandler.java        time jump, wake, weather, hunger cost, debt update
│   └── ActivityTracker.java    last-activity per UUID, AFK + active-before-bed
├── data/UnrestWorldData.java   WorldSavedData
├── display/
│   ├── BossbarDisplay.java
│   ├── ChatDisplay.java        enter/leave, night roll, nightmares, skip summary
│   ├── ActionbarDisplay.java
│   ├── TabListDisplay.java
│   ├── ParticleDisplay.java
│   └── HeadMenu.java           read-only chest container with skulls
├── command/UnrestCommand.java  /unrest (+ /sleep alias)
└── log/NightLog.java           CSV writer
```

---

## 12. Balance

Simulated with `tools/simulate.py`, 4,000 nights per cell. Each cell shows the
median time to skip, with the 10th–90th percentile range in brackets. It
assumes sleepers get into bed at dusk and stay there, on the Hard preset.

| Asleep | Unrest 0 | Unrest 2 | Unrest 5 |
|---|---|---|---|
| 3 of 4 | 0:47 (0:30–1:13) | 0:59 (0:35–1:29) | 1:22 (0:50–2:01) |
| 2 of 3 | 0:55 (0:32–1:29) | 1:10 (0:39–1:51) | 1:39 (0:57–2:30) |
| 1 of 2 | 1:24 (0:44–2:16) | 1:45 (0:56–2:54) | 2:31 (1:24–4:01) |
| 2 of 4 | 1:30 (0:57–2:23) | 1:56 (1:09–3:02) | 2:55 (1:45–4:10) |
| 1 of 3 | 2:36 (1:40–3:57), 7% no skip | 3:15 (2:00–4:57), 15% no skip | 4:45 (2:54–6:56), 24% no skip |
| 1 of 4 | 3:52 (2:33–5:51), 8% no skip | 4:56 (3:12–7:20), 15% no skip | 6:42 (4:26–8:32), 44% no skip |
| 1 of 8 | 7:40 (6:04–8:49), 56% no skip | 8:06 (6:42–8:56), 85% no skip | 8:28 (7:03–9:00), 99% no skip |

**Target:** with a third to half of the server asleep and low Unrest, most
skips should take **1:00–2:30**. "No skip" means the night ended on its own
before the meter filled.

---

## 13. Roadmap

| Version | Content |
|---|---|
| **1.0** | Everything in this document, server-only. |
| 1.x | Tune the defaults using night-log data from live A/B testing. |
| 2.0 (optional) | An optional client add-on that shows a row of sleeper heads under the bossbar. The server keeps working fully for clients without it. |
