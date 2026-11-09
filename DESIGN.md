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
T(r) = BASE + SCALE × (1 − r) / r        BASE = 20s, SCALE = 40s
T(r) = min(T(r), CAP)                    CAP  = 240s
```

| Asleep | T (Hard preset) |
|---|---|
| everyone | **5s** (whole-server sleep, §2.3) |
| 3 of 4 | 33s |
| 2 of 3 | 40s |
| 1 of 2 | 1m 00s |
| 1 of 3 | 1m 40s |
| 1 of 4 | 2m 20s |
| 1 of 6 or more | 4m 00s (cap) |

That is the starting fill time. The ramp (§2.2) speeds it up the longer
someone sleeps, and the time limit (§2.2) ends any partial skip **2 minutes**
after the first sleeper, so the longest wait is 2:00 (§12). A night lasts
about 546 seconds.

### 2.2 Each second

```
weight  = S × weight of the shared dream          (§4, normally 1.0 each)
r_eff   = min(1.0, weight / N)
if r_eff > 0:
    meter += nightMultiplier / (T(r_eff) × presetScale) × ramp(rampSeconds)
else:
    meter −= DRAIN_PER_SECOND                     (default 1/60: empty in 60s)
rampSeconds = S > 0 ? rampSeconds + 1 : max(0, rampSeconds − 1)
meter   += watch                                  (§4.2, added this second)
meter    = max(meter, rampSeconds / MAX_SLEEP)    (the time limit)
meter    = clamp(meter, 0, 1)
if meter ≥ 1: skip()                              (§2.4)
```

- The `nightMultiplier` comes from the night type (§3).
- `presetScale` comes from the difficulty preset (§8).
- `ramp(t) = min(RAMP_MAX, 2^(t / RAMP_DOUBLE))`, with `RAMP_DOUBLE = 40s`
  and `RAMP_MAX = 8`. The speed doubles every 40 seconds that anyone is
  asleep, so a long wait always pays off. It keeps building through Restless
  dreams, winds back down while nobody sleeps, and resets with the meter.
  `RAMP_DOUBLE = 0` turns it off.
- **The time limit**, `MAX_SLEEP = 120s` (`meter.maxSleepSeconds`): the
  meter never sits below the share of 2 minutes that someone has been asleep,
  so it fills by then at the latest, on any night type and through any dream.
  Dreams, Unrest, Luck, the night type and the watch only decide how much
  sooner. It counts the same `rampSeconds`, so it winds back while nobody
  sleeps. 0 turns it off.
- On a **Haunted** night with `r < 0.5`, the night multiplier is multiplied
  again by `hauntedBelowRatioMultiplier` (0.5, so ×0.3 in all). 0 restores the
  hard block, where the meter only drains.
- The meter and the ramp reset to 0 at the start of each night and after each skip.

### 2.3 Whole-server sleep

If `S == N`, the mod ignores the meter and skips after **5 seconds**. This
happens on every night type, including Haunted
(`hauntedAllowsWholeServerSleep = true`). Dream rolls don't apply.

### 2.4 The skip

1. `world.setWorldTime(t + 24000 − (t % 24000))`, which jumps to the next morning. Like vanilla, this step is skipped when the `doDaylightCycle` gamerule is off; the sleepers are still woken and the night still counts as skipped.
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

The mod recognises this skip by what it does (the sleepers are woken during the
world tick and the time lands on the next morning), not by vanilla's own rule,
because other mods can change that rule. Quark's *Improved Sleeping*, for
example, leaves AFK players out. The outcome is then worked out from the mod's
own S and N: whole-server if `S ≥ N`, otherwise partial.

**Exception: leavers (§6.1).** While a player who left still counts as awake,
vanilla's "everyone is asleep" would be a dodge, so the mod stops it: just
before each Overworld tick it clears `WorldServer.allPlayersSleeping` (SRG
`field_73068_P`, by reflection). If that field can't be found, a warning is
logged and the vanilla skip goes through, but it counts as a partial skip.

---

## 3. Night types and Unrest

### 3.1 Night roll

At world time 12541 the mod rolls one night type, weighted by the current Unrest `D`:

| Night | Weight | Minimum | Meter multiplier | Extra rule |
|---|---|---|---|---|
| **Calm** | 25 − 4D | 5 | ×1.3 | — |
| **Normal** | 45 − 3D | 20 | ×1.0 | — |
| **Restless** | 22 + 4D | 0 | ×0.7 | — |
| **Haunted** | 8 + 3D | 0 | ×0.6 | ×0.5 again if `r < 0.5` (§2.2) |

The weights add up to 100 for any D from 0 to 5. At D = 0 the odds are
25 / 45 / 22 / 8 %. At D = 5 they are 5 / 30 / 42 / 23 %.

If a thunderstorm lets players sleep before 12541, that day uses **Normal**.

The roll is shown on the bossbar (§7). With `indicators.announceNight = true` (off by default) it is also posted in chat:
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

Every 15 seconds the sleepers make **one shared roll**. The result applies to
all of them until the next roll, so a good roll is everyone's luck and a bad
roll is everyone's problem. Here `r` is the headcount ratio, `D` is Unrest
and `L` is the sleepers' **combined Luck**.

**Luck.** `L` is the sum of every sleeper's vanilla Luck attribute
(`EntityPlayer.getLuck()`: +1 per level of the Luck potion, −1 per level of
Bad Luck, plus anything a mod or item adds), clamped to ±`luckMax` (5).
`bad(L) = max(0, 1 − 0.15L)` scales the bad rolls: +2 Luck makes them 30 %
less likely, −2 makes them 30 % more likely.

| Roll | Chance | Effect for the next 15s |
|---|---|---|
| **Nightmare** | `0.06 × (1 − r) × (1 + 0.3D) × bad(L)` | The meter loses 10 % and the **unluckiest** sleeper (lowest Luck, a random one on a tie) is woken (`wakeUpPlayer(true, true, false)`). The others sleep on with weight 1 |
| **Restless** | `(0.30 − 0.20r + 0.03D) × bad(L)` | Every sleeper's weight is 0.5 |
| **Deep** | `max(0.05, 0.10 + 0.20r − 0.02D + 0.04L)` | Every sleeper's weight is 2 |
| **Normal** | the remaining chance | Every sleeper's weight is 1 |

Check the rolls in this order: Nightmare, then Restless, then Deep, then Normal.

- With half the server asleep, D = 0 and no Luck, Deep and Restless are both 20 %.
- More sleepers make Deep more likely and the bad rolls less likely.
- More Unrest makes Restless and Nightmare more likely.
- More Luck does the opposite, so a Luck potion before bed helps everyone.
- With `tools/simulate.py --luck 3`, skips take about 15 % less time; with
  `--luck -3`, about 15 % more.

The sleeper woken by a Nightmare can get back into bed straight away. They
count again once the vanilla sleep timer finishes, since they were active
just before. The Nightmare also plays a quiet zombie-villager-cure sound to
them only (`dreams.nightmareSound`, and `indicators.sounds` must be on).

### 4.1 Dream announcements

A roll can post one coloured chat line to everyone in the Overworld:

```
💭 Steve and Alex sink into a deep sleep together. [every sleeper counts 2x] (luck +2)
💭 A nightmare grips Steve and Alex! Alex wakes up in a cold sweat. [dawn −10%, the unluckiest sleeper wakes]
```

`dreams.announce` decides which rolls are mentioned:

| Value | Mentioned |
|---|---|
| `off` | Only Nightmares |
| `changes` (default) | A roll that differs from the previous one, and every Nightmare. A first roll counts as a change from Normal, so a first Normal roll is silent. |
| `all` | Every roll |

- **Names:** one sleeper by name, two as `Steve and Alex`, three as
  `Steve, Alex and Bob`, more as `Steve, Alex and 3 others`.
- **Phrase**, after the previous roll: *sink into a deep sleep (together)* /
  *stay deeply asleep*, *toss and turn* / *keep tossing and turning*,
  *settle down* (Restless → Normal), *stir a little* (Deep → Normal), *sleep
  soundly*, and *A nightmare grips … ! X wakes up in a cold sweat*.
- **Colours:** the phrase is aqua for Deep, gray for Normal, yellow for
  Restless and red for a Nightmare. The `[effect]` is dark gray. The Luck
  tag is green when positive and dark red when negative, and left out at 0.
- **Hovers:** 💭 shows the odds of the next roll with the current Luck; the
  `[effect]` lists every sleeper's own Luck.
- With `indicators.chat = false` nothing is posted.

### 4.2 Night watch

Awake players don't only hold the night back; they can help. While **at
least one sleeper** is counted, every awake, counted player in the Overworld
(not in bed, not creative) adds to the meter by keeping watch:

| Deed | Meter added | Detected by |
|---|---|---|
| Killing a hostile mob (`IMob`) | 0.03 | `LivingDeathEvent`, the true source is the player (arrows count) |
| Entering a chunk for the first time tonight | 0.005 | Checked each second. The chunk the player was in when first seen doesn't count, and going back doesn't count again |
| Breaking a block with hardness > 0 | 0.002 | `BlockEvent.BreakEvent`, not cancelled. Grass, flowers and torches break instantly and don't count |

- Each player can add at most `nightWatch.maxPerPlayer` (0.20) per night, so
  farms and strip-mining can't skip the night on their own.
- Deeds are collected as they happen and added to the meter in the next
  second-tick (§2.2), on any night type. During a whole-server sleep they are
  discarded.
- A skip helped by the watch is still a partial skip for Unrest.
- Feedback: right after a deed, the player's actionbar shows how much time
  it took off the wait: `Night watch −6s (+3%) · dawn in 0:42 · your watch
  12/20%`. The seconds are the meter added divided by the sleepers' current
  fill rate (ramp included); while the meter isn't filling only `+3%` is
  shown. The 5-second nudge skips
  them for 3 seconds. The nudge itself ends with `— or keep watch: fight,
  mine, explore`. The skip summary's hover lists each helper's share.
- The watch resets with the meter. Its total is logged as `watchTotal` (§9.2).

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
  - breaking or using a block, or using an item (right-clicking a bed doesn't count, because that is how a player gets into bed)
  - chatting or running a command
  - opening a container
- **Moving on its own doesn't count**, so water currents and being pushed don't make a player active.
- **AFK** means no activity for `afk.timeoutSeconds` (default 300). AFK players are not counted in N or S.
- **The active-before-bed rule:** a player in bed only counts as a sleeper if their last activity was no more than `afk.activeBeforeBedSeconds` (default 180) **before they got into bed**. Lying in bed doesn't count as activity, but being asleep doesn't make the player AFK either.
- Players **outside the Overworld**, in **creative** or in **spectator** mode are not counted.
- If `N == 0`, the meter is paused.

### 6.1 Leavers (anti-dodge)

Without this rule, awake players could speed up the night by **logging out**
or **stepping into the Nether**: N drops, the ratio rises, and if everyone left
is in bed it even becomes a whole-server sleep that *lowers* Unrest.

So an awake, counted player who logs out (`antiDodge.countLeavers`) or leaves
the Overworld (`antiDodge.countDimensionLeavers`) keeps counting as **awake**
in N until `antiDodge.graceSeconds` (300) after their **last activity**. That
is the moment they would have gone AFK had they stayed, so leaving is never
better than standing still. A player who was already idle for 4 minutes counts
for 1 more minute; someone who leaves right after doing something counts for
the full 5.

- Coming back to the Overworld ends it at once; the census counts them
  normally again (awake, asleep or AFK).
- Going to bed, going AFK or switching to creative isn't leaving.
- Leavers are tracked all the time, so leaving just before dusk doesn't escape it.
- While any leaver counts, vanilla's own skip is blocked (§2.4).
- Leavers count only in `active` mode. They're kept in memory, so a server
  restart forgets them.
- They are listed in the `lies down` hover, in `/unrest info` and on the paper
  in the `/unrest` menu, with the time left.

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
| **Chat: events** | Everyone in the Overworld | The night roll (only with `indicators.announceNight`, off by default), the shared dream roll (§4.1, Nightmares included), and the skip summary. Hovering over the skip summary shows the night's Deep, Restless and Nightmare counts and each player's Night watch share. |
| **Actionbar** | Counted players who are awake, every 5s while S ≥ 1 | `3 players are waiting for you to sleep`. It doesn't name anyone. |
| **Tab list** | Everyone | `[Zz] ` in gray before sleepers' names, sent as a vanilla player-list display-name update (Forge 14.23.5 has no tab-list name event). No scoreboard teams are used. |
| **Particles** | Nearby players | A small cloud puff above each sleeper every 2s (`WorldServer.spawnParticle`). |
| **Sound** | Overworld players | A soft chime when the meter passes 50 % and 90 %. |

If the client font can't show 🌙, ☀ and 💭, `indicators.asciiOnly = true` replaces
them with `(night)`, `(dawn)` and `(dream)`.

### 7.1 `/unrest` head menu

Running `/unrest` with no arguments opens a read-only 6-row chest screen:

- **Row 1 (info):**
  - Clock: the night type and the time until dawn
  - Experience bottle: the meter %
  - Coloured dye: Unrest shown as `●●○○○` plus the exact value
  - Paper: `S / N` and the preset
- **Rows 2–6:** one `minecraft:skull` (meta 3, `SkullOwner` set to the player's profile) for each online player. Sleepers come first, then awake players, then AFK players, then players in other dimensions. Each head's name is the player's name. Its lore is `💤 Sleeping 1:20 · Deep sleep` (the dream is added once the sleeper has rolled), `Awake`, `AFK (6m)` or `Elsewhere`.
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
    D:baseSeconds=20.0
    D:scaleSeconds=40.0
    D:capSeconds=240.0
    D:wholeServerSeconds=5.0
    D:drainPerSecond=0.016667
    D:rampDoubleSeconds=40.0           # 0 = no ramp
    D:rampMax=8.0
    D:maxSleepSeconds=120.0            # the time limit; 0 = none
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
    D:hauntedBelowRatioMultiplier=0.5  # 0 = the meter doesn't fill below hauntedMinRatio
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
    D:nightmareBase=0.06 D:nightmarePerDebt=0.3
    D:nightmareMeterLoss=0.10
    D:deepWeight=2.0     D:restlessWeight=0.5
    D:luckDeep=0.04      D:luckBad=0.15           D:luckMax=5.0
    S:announce=changes   B:nightmareSound=true
}
nightWatch {
    B:enabled=true
    D:killHostile=0.03   D:breakBlock=0.002   D:newChunk=0.005
    D:maxPerPlayer=0.20  B:feedback=true
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
antiDodge {
    B:countLeavers=true   B:countDimensionLeavers=true   I:graceSeconds=300
}
indicators {
    B:bossbar=true        B:alwaysAtNight=false   I:maxNames=3
    B:chat=true           B:announceNight=false
    B:actionbar=true        I:actionbarIntervalSeconds=5
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
nightmares,hungerLostTotal,watchTotal
```

`outcome` is one of `whole_server`, `partial`, `natural`, `vanilla_skip` or
`external`. `vanilla_skip` is only used in `observe` mode. `external` means
something else moved the time out of the night (for example `/time set day`):
the night ends with no skip, no hunger cost and no change to Unrest.

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
│   ├── DreamRoller.java        the shared dream roll every 15s, with Luck
│   ├── NightWatch.java         awake players' deeds, capped per player (§4.2)
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
assumes sleepers get into bed at dusk and stay there, on the Hard preset,
with the ramp and the 2-minute limit, no Luck, and nobody keeping watch
(§4.2), which only makes it faster.

| Asleep | Unrest 0 | Unrest 2 | Unrest 5 |
|---|---|---|---|
| 3 of 4 | 0:27 (0:19–0:40) | 0:29 (0:19–0:43) | 0:37 (0:25–0:51) |
| 2 of 3 | 0:31 (0:20–0:45) | 0:35 (0:21–0:51) | 0:41 (0:27–0:58) |
| 1 of 2 | 0:40 (0:22–0:59) | 0:45 (0:26–1:04) | 0:56 (0:35–1:15) |
| 2 of 4 | 0:40 (0:22–0:58) | 0:44 (0:25–1:03) | 0:54 (0:35–1:12) |
| 1 of 3 | 0:59 (0:39–1:23) | 1:06 (0:45–1:40) | 1:21 (0:59–1:49) |
| 1 of 4 | 1:12 (0:54–1:36) | 1:19 (1:00–1:50) | 1:32 (1:13–1:55) |
| 1 of 8 | 1:31 (1:19–1:50) | 1:35 (1:22–2:00) | 1:42 (1:31–2:00) |
| 1 of 12 | 1:31 (1:22–1:50) | 1:41 (1:22–2:00) | 1:42 (1:31–2:00) |

**Target:** no partial skip takes more than **2:00**; with a third to half of
the server asleep and low Unrest, most take **0:30–1:00**. "No skip" means the night ended on its own
before the meter filled.

---

## 13. Roadmap

| Version | Content |
|---|---|
| **1.0** | Everything in this document, server-only. |
| 1.x | Tune the defaults using night-log data from live A/B testing. |
| 2.0 (optional) | An optional client add-on that shows a row of sleeper heads under the bossbar. The server keeps working fully for clients without it. |
