# How Unrest works (short note)

Unrest is a **server-only Forge mod for Minecraft 1.12.2**. It replaces "skip the
night when enough players are in bed" with a **sleep meter** that fills over
time. For the full rules see [DESIGN.md](../DESIGN.md); for players and admins
see the [User Guide](USER_GUIDE.md).

## The loop

Everything runs from one world-tick handler (`core/SleepTracker`), which does
its work once per second (every 20 ticks):

1. **Count players.** `N` is the active players in the Overworld. AFK players,
   creative/spectator players and players in other dimensions are not counted.
   `S` is how many of them are asleep. A sleeper only counts if they were
   active shortly before getting into bed (`core/ActivityTracker`).
2. **Fill the meter** (0 → 1). The fill time depends on the share asleep,
   `r = S / N`:

   ```
   T(r) = 20s + 40s × (1 − r) / r      (capped at 240s)
   ```

   The rate is then scaled by the night type, the difficulty preset and a
   **ramp** that doubles the speed every 40s someone stays in bed. A hard
   limit means a partial skip never takes more than **2 minutes**.
   If nobody is asleep, the meter drains.
3. **Skip when full** (`core/SkipHandler`): jump to morning, clear the weather,
   wake the sleepers, take some food from them, update Unrest and log the night.
   If **everyone** is asleep, the night skips after 5 seconds.

The formulas live in `core/SleepMath`, which has no Minecraft dependencies so
it can be unit tested and matched against `tools/simulate.py`.

## What changes from night to night

- **Night type**, rolled at dusk: Calm (×1.3), Normal, Restless (×0.7),
  Haunted (×0.6, slower again with less than half asleep) or Fever Dream
  (rolls every few seconds, effects tripled).
- **Unrest (night debt)**, 0–5, saved with the world. A partial skip adds
  `1 − r`, a whole-server sleep removes 1.5, and a night with no skip removes 1.
  Higher Unrest makes bad night types and bad dreams more likely.
- **Moon phase**: a full moon makes rolls wilder, a new moon makes them calmer.

## Dreams and momentum

Every 8–22 seconds the sleepers make **one shared roll** (0–100):

| Roll | Effect until the next roll |
|---|---|
| Nightmare | Meter −10%, the unluckiest sleeper wakes, and the **Reaper** may spawn |
| Restless | Sleepers count as half a player |
| Normal | Sleepers count as one player |
| Deep | Sleepers count as two (up to 4× in a chain) |

The odds depend on `r`, Unrest, the moon and the sleepers' combined vanilla
**Luck**. On top of that: bad rolls store tension that a Deep roll releases,
three Restless rolls become a Night terror, and once a night there is a
**double or nothing** flip at 50%.

## Awake players can help

- **Night watch:** killing hostile mobs, mining and exploring new chunks add to
  the meter, up to 20% per player per night.
- **The Reaper:** a wither skeleton that hunts an awake player after a
  Nightmare. Kill it within 60s for +20%; otherwise the meter loses 5%.
- **No dodging:** a player who logs out or leaves the Overworld at night keeps
  counting as awake for a while (`core/Leavers`).

## Everything else

- **Displays** use vanilla only (bossbar, chat, actionbar, tab list, particles,
  the `/unrest` head menu), so clients need nothing installed.
- **Modes:** `active` runs the mechanic; `observe` keeps vanilla sleep but still
  logs every night to `logs/unrest-nights.csv` for A/B comparison.
- **Config:** `config/unrest.cfg`, reloadable with `/unrest reload`.
