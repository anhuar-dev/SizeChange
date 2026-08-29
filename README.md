# 🎮 SizeChange

Change any player's size on the fly — permanently or for a set duration — with full WorldGuard region and per-world control and database persistence. Built on Paper's native `Attribute.SCALE`, so there's no resource pack or client mod required.

---

## 🎯 Use Cases

- Roleplay servers with different races and sizes
- Mini-games with size-based mechanics (giants vs. ants, shrink challenges, etc.)
- PvP arenas that normalize every player to the same size
- Timed events and rewards (temporary giant/mini effects)
- Rank/VIP perks tied to a bigger size in specific zones

## 🚀 Features

### 📏 Dynamic Player Scaling
- Change player size from **0.0625x to 16x**, no reconnect required
- **Permanent** sizes persist across sessions; **temporary** sizes auto-revert when they expire
- Commanded size changes (`set`/`reset`/`temporary set`/`temporary clear`) **smoothly animate** over ~0.8s with an ease-out curve instead of snapping instantly, with a configurable **sound + particle** on every change — toggle either one off, and pick your own sound/particle, via `ANIMATION`/`EFFECTS` in `setting.yml`; region-driven and automatic changes always stay instant and silent either way, so neither ever adds recurring cost
- Two drawn **particle animations** — `HELIX` (twin strands spiralling up the player) and `AURA` (rings rising and pulsing outward) — that scale with the player's size and follow them as they move, plus the classic one-shot `BURST`. Pick the style and its length from the config GUI
- Runs on vanilla `Attribute.SCALE` — nothing to install client-side

### 🗺️ WorldGuard Region & World Control
- **DENY-REGION** — forces everyone back to normal size (1.0x) while inside
- **SIZE-REGION** — forces a specific size per region
- **DENY-WORLD** — forces normal size (1.0x) while in the world
- **SIZE-WORLD** — forces a specific size while in the world
- Worlds behave exactly like regions: visual-only while inside, never written to the database, restored on leaving. A region the player is standing in always takes precedence over the world
- A player's active temporary size is preserved across a region/world visit and resumes with the correct time remaining when they leave
- `sizechange.bypass` lets staff ignore region/world enforcement entirely

### ⚔️ PvP Combat Tag
- Two players damage each other → **both** are "in combat" for a configurable time (default 15s); every fresh hit refreshes it
- While tagged, size is forced to a configurable value (default 1.0x) — visual-only, restored when the tag ends, and it **outranks region/world overrides**
- Per-region exclusions: if either player is inside an excluded region on the hit, nobody is tagged
- Independent **action bar**, **boss bar** (configurable colour/overlay) and **chat** feedback — toggle each on its own
- `sizechange.bypass` players are never tagged; `/sizechange combat check|clear <player>` for staff

### 💾 Flexible Database
- **SQLite** by default — zero setup, ideal for most servers
- **MongoDB** support for networks and multi-server setups
- All required libraries (Gson, SQLite JDBC, MongoDB driver) are fetched automatically by Paper's library loader — nothing to shade into the jar

### ⚡ Performance
- Fully asynchronous database reads/writes — no main-thread I/O
- Dirty-tracking auto-save every 5 minutes (only changed players are written)
- Temporary sizes and their expirations survive server restarts
- Thread-safe caches throughout

### 🛠️ Admin Experience
- Brigadier-powered command with tab completion for player names and durations
- `/sizechange list` opens a **paginated GUI** with player heads (real skins) instead of a wall of text — hover a head to see that player's permanent/temporary size and time remaining
- `/sizechange config` opens an **in-game editor** for `DENY-REGION`/`SIZE-REGION`/`DENY-WORLD`/`SIZE-WORLD` (add/remove entries by chat, with a delete confirmation) **and** for the **Combat** and **Effects** settings — toggle booleans, cycle the boss-bar colour/overlay, edit numbers and sound/particle names in place, no YAML editing or `/sizechange reload`
- Both GUIs are **100% configurable** via `menu.yml` — title, size, fill item, navigation slots/materials, and every item's name/lore template, all editable, no code required
- Fully customizable messages — MiniMessage tags, hex colors (`&#RRGGBB`), and legacy `&`/`§` codes all supported
- A ready-to-copy **DeluxeMenus template** (`deluxemenu_template.yml`, generated on first run) — a self-service size picker players can open themselves, no permissions needed (requires the separate, optional DeluxeMenus plugin)
- **PlaceholderAPI** support (optional) — `%sizechange_size%`, `%sizechange_temporary_remaining%`, … for scoreboards, tab, holograms, etc.

## 📋 Commands

Root command: **`/sizechange`** (no aliases).

| Command | Description | Permission |
|---|---|---|
| `/sizechange` | Show help | `sizechange.help` |
| `/sizechange permanent set <player> <size>` | Set a permanent size (`0.0625`–`16.0`) | `sizechange.permanent.set` |
| `/sizechange permanent reset <player>` | Reset the permanent size back to normal (`1.0`) | `sizechange.permanent.reset` |
| `/sizechange temporary set <player> <size> <duration>` | Apply a temporary size | `sizechange.temporary.set` |
| `/sizechange temporary clear <player>` | Cancel an active temporary size | `sizechange.temporary.clear` |
| `/sizechange temporary extend <player> <duration>` | Add time to an active temporary size | `sizechange.temporary.extend` |
| `/sizechange view <player>` | View current permanent + temporary size in one reply | `sizechange.view` |
| `/sizechange list` | Open the paginated GUI of online players | `sizechange.list` |
| `/sizechange info` | Plugin diagnostics (version, database, active temporaries) | `sizechange.info` |
| `/sizechange config` | Open the GUI to add/remove region and world overrides (DENY/SIZE) | `sizechange.config` |
| `/sizechange combat check <player>` | Show whether a player is combat-tagged and for how long | `sizechange.combat.check` |
| `/sizechange combat clear <player>` | Remove a player's combat tag now | `sizechange.combat.clear` |
| `/sizechange reload` | Reload config, messages, menu, regions, worlds and combat | `sizechange.reload` |
| — | Ignore DENY/SIZE region & world enforcement, and PvP combat tags | `sizechange.bypass` |

Duration formats: `s` (seconds), `m` (minutes), `h` (hours), `d` (days) — e.g. `30s`, `5m`, `2h`, `1d`.

> All permissions default to `op`, except `sizechange.bypass` which defaults to `false`. Assign them explicitly with your permissions plugin (LuckPerms, etc.) if you don't want every OP to have full access.

## ⚙️ Requirements

- **Minecraft:** 1.21.4
- **Server:** Paper (or Paper-based forks, e.g. Purpur) — not Spigot
- **Java:** 21+
- **WorldGuard:** 7.0.7+ and WorldEdit 7.3+ (required, must load before this plugin)
- **Database:** none required by default (SQLite); MongoDB 4.0+ only if you opt into it
- **PlaceholderAPI:** optional — the expansion registers itself automatically when it's installed

## 📦 Installation

1. Download `SizeChange.jar` (or build it — see [Build](#build) below)
2. Drop it into your `plugins/` folder
3. Make sure **WorldGuard** and **WorldEdit** are installed
4. Start the server once to generate `setting.yml`, `message.yml`, `menu.yml`, and an optional `deluxemenu_template.yml`
5. *(Optional)* Set `DATABASE.TYPE` to `MONGODB` in `setting.yml` if you're running a network
6. Restart or `/sizechange reload`

---

# 🔧 Technical Documentation

The following is aimed at whoever administers the plugin at a low level or wants to edit the source code.

## ⚙️ Configuration

All three config files (`setting.yml`, `message.yml`, `menu.yml`) **auto-update on their own** when you drop in a
new plugin version: on startup, any key a newer version introduced that your existing file doesn't have yet gets
added automatically (with its default value) — including a new key added *inside* a section you already have (e.g.
if a future update adds a new option under `ANIMATION`), not just entirely new top-level sections. Anything you've
already set is never touched or overwritten. The one exception is `DENY-REGION`/`SIZE-REGION`/`DENY-WORLD`/`SIZE-WORLD`:
those hold your own region and world names, not a fixed schema, so they're only ever filled in if the whole section
is missing — an update can't tell "a new key this version added" apart from "an entry you deliberately deleted," so
it never edits them once they exist. (They ship empty, so an update can never silently start forcing a size in a
world you didn't configure.)

### `setting.yml` - Main Configuration

```yaml
# TYPE must be exactly "SQLITE" or "MONGODB" — anything else falls back to
# SQLite with a console warning.
DATABASE:
  TYPE: "SQLITE"

  SQLITE:                    # used only when TYPE is "SQLITE"
    FILE: "database/players.db"  # subfolder + the .db-wal/.db-shm files are created automatically

  MONGODB:                   # used only when TYPE is "MONGODB"
    URI: "mongodb://localhost:27017"
    DATABASE: "size_plugin"
    COLLECTION: "players"

# Smooth size transition for permanent set/reset and temporary set/clear
# (~0.8s, ease-out). Set to false for an instant change instead.
ANIMATION:
  ENABLED: true

# Sound + particle played once when a command actually changes a player's size.
# Sound and particle are independent — each has its own ENABLED toggle. NAME must
# be a valid org.bukkit.Sound / org.bukkit.Particle name.
EFFECTS:
  SOUND:
    ENABLED: true
    NAME: "ENTITY_SLIME_SQUISH"
  PARTICLE:
    ENABLED: true
    NAME: "FLAME"     # bright single-point particles read best; POOF looks faint
    STYLE: "HELIX"    # HELIX, AURA or BURST
    DURATION: 50      # ticks; HELIX/AURA only

# PvP combat tag. Both players are tagged for DURATION seconds on a mutual hit
# (each hit refreshes it); size is forced to SIZE while tagged, outranking any
# region/world override, and restored when it ends. No tag if either player is
# in an EXCLUDED-REGIONS region on the hit. sizechange.bypass players are exempt.
COMBAT:
  ENABLED: true
  DURATION: 15
  SIZE: 1.0
  EXCLUDED-REGIONS: []
  DISPLAY:               # each independent
    ACTIONBAR: true
    BOSSBAR: true
    CHAT: true
  BOSSBAR:
    COLOR: "RED"         # PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE
    OVERLAY: "NOTCHED_10" # PROGRESS, NOTCHED_6/10/12/20

# WorldGuard region names. Size is forced to 1.0 (normal) while a player is
# standing inside any of these — their permanent/temporary size isn't lost,
# just hidden until they leave. Manageable in-game via /sizechange config.
DENY-REGION:
  - "spawn"
  - "pvp_arena"

# WorldGuard region name -> forced size, same behavior as DENY-REGION above
# but with a specific value per region instead of always 1.0.
SIZE-REGION:
  mini_game: 0.3   # Players shrink
  giant_zone: 3.0  # Players grow

# World names. Size forced to 1.0 (normal) while a player is in the world —
# same visual-only behavior as DENY-REGION, but world-wide. A region the
# player stands in still takes precedence. Ships empty; manageable in-game
# via /sizechange config.
DENY-WORLD:
  - "world_nether"

# World name -> forced size, same behavior as DENY-WORLD but with a specific
# value per world instead of always 1.0. Ships empty.
SIZE-WORLD:
  minigames_lobby: 0.5
```

### `message.yml` - Customizable Messages

Available placeholders (vary by key): `{player}`, `{size}`, `{temp_size}`, `{duration}`, `{remaining}`, `{seconds}`, `{version}`, `{database}`, `{active_temp}`, `{regions}`, `{worlds}`, `{combat}`, `{region}`, `{world}`, `{name}`.

```yaml
help:
  - "<gold><bold>SizeChange Plugin</bold>"
  - "<yellow>/sizechange permanent set <player> <size> <gray>- Change permanent size"
  - "<yellow>/sizechange permanent reset <player> <gray>- Reset size to normal"
  - "<yellow>/sizechange temporary set <player> <size> <duration> <gray>- Apply a temporary size"
  - "<yellow>/sizechange temporary clear <player> <gray>- Cancel temporary size"
  - "<yellow>/sizechange temporary extend <player> <duration> <gray>- Add time to a temporary size"
  - "<yellow>/sizechange combat check|clear <player> <gray>- Inspect or remove a combat tag"
  - "<yellow>/sizechange view <player> <gray>- View current size"
  - "<yellow>/sizechange list <gray>- Open the online players menu"
  - "<yellow>/sizechange info <gray>- View plugin diagnostics"
  - "<yellow>/sizechange config <gray>- Open the region menu (DENY/SIZE)"
  - "<yellow>/sizechange reload <gray>- Reload configuration"

size-set: "<green>✓ <white>{player}</white>'s size set to <white>{size}</white>"
size-reset: "<green>✓ <white>{player}</white>'s size reset to normal (1.0)"
size-changed: "<yellow>Your size has been changed to <white>{size}</white>"

temp-size-set: "<green>✓ <white>{player}</white>'s temporary size set to <white>{size}</white> for <white>{duration}</white>"
temp-size-applied: "<yellow>You've been given a temporary size of <white>{size}</white> for <white>{duration}</white>"
temp-size-cleared: "<green>✓ <white>{player}</white>'s temporary size cancelled and restored"

player-not-found: "<red>✗ Player <white>{player}</white> is not online"
invalid-duration: "<red>✗ Invalid duration. Use format: 30s, 5m, 2h, 1d"
gui-console-only: "<red>✗ This command can only be run in-game."

info:
  - "<gold><bold>SizeChange</bold> <gray>v{version}</gray>"
  - "<yellow>Database: <white>{database}</white>"
  - "<yellow>Active temporary sizes: <white>{active_temp}</white>"
  - "<yellow>Configured regions: <white>{regions}</white>"

config-add-prompt-name: "<yellow>Type the region name."
config-add-prompt-world: "<yellow>Type the world name."
config-add-prompt-size: "<yellow>Type the size (0.0625 - 16.0)."
config-add-duplicate: "<red>✗ <white>{name}</white> is already configured."
config-add-region-not-found-warning: "<gray>⚠ No region named <white>{region}</white> was found in any loaded world. It was saved anyway — double-check the name."
config-add-world-not-found-warning: "<gray>⚠ No world named <white>{world}</white> is loaded. It was saved anyway — double-check the name."
config-deny-added: "<green>✓ '{region}' added to DENY-REGION."
config-size-added: "<green>✓ '{region}' added to SIZE-REGION with size {size}."
config-world-deny-added: "<green>✓ '{world}' added to DENY-WORLD."
config-world-size-added: "<green>✓ '{world}' added to SIZE-WORLD with size {size}."

reload: "<green>✓ Configuration reloaded successfully"
```

All the real keys live in `src/main/resources/message.yml`; edit them directly to customize text,
colors (MiniMessage) and prefix.

### `menu.yml` - `/sizechange list` GUI Menu

Fully controls the paginated menu opened by `/sizechange list`: title, rows, fill item, navigation
slots (previous/next/info), and the name+lore template for each player head. Head placeholders:
`{player}`, `{size}`, `{has_temp}`, `{temp_size}`, `{remaining}`; navigation placeholders: `{page}`,
`{total_pages}`, `{total_players}`.

```yaml
list:
  title: "<dark_gray>Online Players <gray>({page}/{total_pages})"
  rows: 6

  content:
    start-slot: 0
    end-slot: 44          # 45 visible slots per page

  player-head:
    name: "<yellow>{player}"
    lore:
      - "<gray>Permanent size: <white>{size}</white>"
      - "<gray>Temporary active: <white>{has_temp}</white>"
      - "<gray>Temporary size: <white>{temp_size}</white>"
      - "<gray>Time remaining: <white>{remaining}</white>"

  navigation:
    previous:
      slot: 48
      material: ARROW
      disabled-material: GRAY_STAINED_GLASS_PANE
    next:
      slot: 50
      material: ARROW
      disabled-material: GRAY_STAINED_GLASS_PANE
```

The menu is **informational only**: clicking a head does nothing (all the info already shows in the
lore on hover); only the `navigation.previous`/`navigation.next` slots react, changing the page. It
only lists online players — no new database queries.

### `menu.yml` - `/sizechange config` GUI Menu

Unlike the one above, this flow **does write to `setting.yml`**, and has these screens:

1. **`region-config-select`** — the hub opened by `/sizechange config`: `deny-button`, `size-button`,
   `world-deny-button`, `world-size-button` (each opens `region-config` scoped to that collection),
   `combat-button` and `effects-button` (each opens a `SettingsMenu`). A missing button section is just
   skipped — on an existing `menu.yml` the new buttons are added automatically by the config auto-update.
2. **`region-config`** — the paginated list of that collection (templates `deny-entry`/`size-entry`, shared
   by the region and world collections, with placeholders `{name}`/`{size}` — `{region}`/`{world}` are kept
   as aliases; the title supports `{type}` → the setting.yml key). Clicking an existing entry opens a
   **confirmation screen** (`confirm-delete`: `cancel`/`confirm` buttons, also configurable) before removing
   it — avoiding a misclick wiping out a configured entry. The `back` button returns to the selector. The
   `add` button (configurable slot) triggers a **chat** flow: it asks for the region/world name (validates
   it doesn't already exist in the sibling list — DENY or SIZE within the same region/world scope — and
   rejects empty names or names with a dot `.`, since Bukkit's `ConfigurationSection` treats the dot as a
   path separator), and for the SIZE collections it also asks for the numeric value (`0.0625`–`16.0`). At
   any step you can type `cancel` to abort.

After saving or removing, the matching cache is reloaded automatically
(`regionManager.reloadDenyRegions()` / `DenyRegionHandler.reloadSizeRegions()` / `worldManager.reload()` /
`combatTagManager.reload()`) — the change applies live, no need for `/sizechange reload`. If the typed name
doesn't match a real WorldGuard region (regions) or a loaded world (worlds), it still saves but warns with
`config-add-region-not-found-warning` / `config-add-world-not-found-warning`.

3. **`settings-combat` / `settings-effects`** — the Combat and Effects screens (from `combat-button` /
   `effects-button`). Only `title` / `rows` / `back-slot` / `fill` are read from `menu.yml`; each setting's
   item is built by the plugin and shows its live value. Clicking one flips a boolean, cycles the boss-bar
   colour/overlay or the particle animation style, or opens a chat prompt for a number (validated on submit).
   For the **sound / particle name** it first asks *type it* vs *browse the list* — the latter opens
   `value-picker`, a paginated menu of every `Sound` / `Particle`, click to select. The Effects screen also
   carries the particle animation style and its length. The Combat screen's "Excluded regions" slot reuses
   `region-config` (its `back` returns to the Combat screen). Every write saves `setting.yml` and reloads
   `CombatTagManager`; the Effects keys are read live by `SizeAnimator`, so they need no reload.

```yaml
region-config-select:
  title: "<dark_gray>Configure Overrides"
  rows: 3
  deny-button:          # red items = DENY (left)
    slot: 10
    material: BARRIER
    name: "<red>DENY-REGION"
  world-deny-button:
    slot: 11
    material: RED_BED
    name: "<red>DENY-WORLD"
  size-button:          # blue items = SIZE (right)
    slot: 15
    material: BLUE_DYE
    name: "<blue>SIZE-REGION"
  world-size-button:
    slot: 16
    material: LAPIS_LAZULI
    name: "<blue>SIZE-WORLD"

region-config:
  title: "<dark_gray>{type} <gray>({page}/{total_pages})"
  rows: 6

  deny-entry:
    material: BARRIER
    name: "<red>{name}"
    lore:
      - "<gray>Forces <white>1.0</white> size while inside"
      - "<yellow>Click to remove (asks for confirmation)"

  size-entry:
    material: PAPER
    name: "<yellow>{name}"
    lore:
      - "<gray>Forces <white>{size}</white> size while inside"
      - "<yellow>Click to remove (asks for confirmation)"

  add:
    slot: 49
    material: LIME_DYE
    name: "<green>+ Add {type}"

  back:
    slot: 45
    material: ARROW
    name: "<gray>← Back"

  confirm-delete:
    title: "<red>Delete '{region}'?"
    rows: 3
    cancel:
      slot: 11
      material: LIME_STAINED_GLASS_PANE
      name: "<green>✔ Cancel"
    confirm:
      slot: 15
      material: RED_STAINED_GLASS_PANE
      name: "<red>✘ Delete permanently"
```

### `deluxemenu_template.yml` - Self-Service Size Picker (optional)

Generated in `plugins/SizeChange/` on first run, alongside the other config files — but unlike them, SizeChange
never reads it. It's a ready-made menu in DeluxeMenus' own format, meant to be copied into
`plugins/DeluxeMenus/menus/` if you have that plugin installed (it's a separate, optional dependency — not
required by SizeChange). It gives regular players a GUI with preset permanent/temporary sizes they can apply to
themselves.

Every button runs `/sizechange ...` prefixed with `[console]`, which executes it as console and **skips
SizeChange's own permission checks** — that's what lets a player without `sizechange.permanent.set`/
`sizechange.temporary.set` use the menu on themselves. Do **not** grant players those real permissions thinking
they need them for the menu — they don't, and if they have them they can also run `/sizechange` manually against
any other player, not just themselves. Leave those permissions at their default (`op`); the menu buttons work
regardless, because console execution bypasses the check entirely.

One item, `permanent_vip_giant`, shows a different kind of gate: a **DeluxeMenus click requirement** (not a
SizeChange permission) on a made-up node like `sizechange.menu.vip`, handed out via your rank plugin. The item is
always visible; clicking it checks the requirement — with the permission, `left_click_commands` run and the resize
happens; without it, `deny_commands` run instead (just a message) and the resize command never executes. Use this
pattern for any preset you want to reserve for a specific rank. Note: DeluxeMenus' `has permission` requirement
type needs **Vault** installed — without it, this specific button's gate won't work even though the rest of the
menu will.

### PlaceholderAPI (optional)

If PlaceholderAPI is installed, an expansion registers itself on startup (no `/papi ecloud` download — it ships
inside the plugin). All placeholders read live in-memory state, so the size ones only resolve for **online**
players (an offline player gives an empty string; the booleans still answer):

| Placeholder | Value |
|---|---|
| `%sizechange_size%` | current visible size, 2 decimals (`%sizechange_size_raw%` for full precision) |
| `%sizechange_permanent_size%` | the player's real permanent size (ignores an active temp or a region/world override) |
| `%sizechange_temporary_active%` | `true` / `false` |
| `%sizechange_temporary_size%` | the temp size, or empty |
| `%sizechange_temporary_remaining%` | e.g. `4m 12s`, or empty |
| `%sizechange_temporary_remaining_seconds%` | integer seconds, or `0` |
| `%sizechange_override_active%` | `true` while a DENY/SIZE region or world is forcing the player's size |
| `%sizechange_combat_tagged%` | `true` / `false` |
| `%sizechange_combat_remaining%` | seconds left on the combat tag, or `0` |

---

## 🔧 Technical Features

### Data Persistence

- **Periodic auto-save**: every 5 minutes, only for players whose state changed since the last save (dirty
  tracking), avoiding unnecessary writes.
- **On disconnect**: saves the player's real size immediately (the permanent one, not the visual one if they're
  inside a region or have an active temporary size).
- **On connect**: restores the saved permanent size; if they had an active temporary size that hadn't expired, it's
  restored with the remaining time calculated from its saved expiration.
- **Temporary sizes**: persist across server restarts as long as they haven't expired.

### Optimized Performance

- **Asynchronous operations**: DB reads/writes without blocking the main thread.
- **Dirty tracking**: the periodic save only writes players with actual changes.
- **Thread-safe**: `ConcurrentHashMap` collections for region caches, saved sizes, and temporary sizes.
- **Periodic temporary-size check**: every 30s (600 ticks) on an async thread; the actual restore happens on the
  main thread.
- **Scoped animation**: `SizeAnimator` runs a short (~16 tick / 0.8s, ease-out) repeating task only for the four
  command-triggered size changes (`permanent set/reset`, `temporary set/clear`) — toggleable via `ANIMATION.ENABLED`
  in `setting.yml`. WorldGuard region crossings, automatic temporary-size expiration, and the join-time restore all
  keep calling `SizeManager.setSize()` directly (instant, no task) regardless of that setting — those can fire far
  more often than a player types a command, so they never animate even when the toggle is on.
- **Scoped effects**: same four command-triggered changes also play a sound and/or particle once per change.
  `EFFECTS.SOUND` and `EFFECTS.PARTICLE` in `setting.yml` are fully independent — each has its own `ENABLED` toggle
  and `NAME`, and the whole `EFFECTS` block is independent of `ANIMATION.ENABLED`, so any combination works (smooth
  motion + only a particle, instant snap + only a sound, nothing at all, …). An invalid `NAME` logs a console
  warning and is skipped rather than crashing anything. An older single-toggle `EFFECTS` block
  (`ENABLED` + string `SOUND`/`PARTICLE`) is migrated to the new shape automatically on first load, preserving both
  the on/off state and any custom names.

### Particle Animations

`ParticleAnimator` draws the particle half of an effect. `EFFECTS.PARTICLE.STYLE` picks the shape and
`EFFECTS.PARTICLE.DURATION` its length in ticks (default 50 = 2.5s):

| Style | Shape |
|---|---|
| `HELIX` *(default)* | The full double spiral (2 strands, ~2.5 turns) redrawn every tick — spinning, radius pinched at the feet and head so it reads as a cocoon |
| `AURA` | Four full rings at staggered heights, each looping feet→head and widest at chest height, the whole set spinning |
| `BURST` | The original one-shot puff, kept so an existing `setting.yml` keeps behaving the same |

`HELIX`/`AURA` run as a per-player repeating task that **redraws the entire shape every tick** (~68 / ~88
points) rather than tracing it with a few particles, and a `sin` envelope over the run makes it bloom out of
the player, hold, then draw back in. It re-reads the player's location each tick so it follows them, and
scales with the target size (clamped to `0.35`–`4.0`, so a 16x giant doesn't paint a 30-block helix). The
task self-terminates when the player goes offline; `SizeAnimator.cancel()` deliberately does **not** touch it
(that method fires mid-`animateTo`, right after the effect starts).

Every point is spawned with `count=1`, zero offset and zero `extra` — Bukkit's usual `count`+offset form
scatters particles randomly in a box and `extra` launches them, which is what made the old effect spray. With
count 1 and no speed a particle stays exactly where placed. Particles that need a data object (`DUST`,
`BLOCK`, `ITEM`, …) get a sensible default so every entry in the picker menu is usable. Bright single-point
particles (`FLAME`, `END_ROD`, `SOUL_FIRE_FLAME`, `ELECTRIC_SPARK`, coloured `DUST`) read best; `POOF`/`CLOUD`
look faint.

### WorldGuard Regions & Worlds

- **Boundary-crossing detection**: region crossings are triggered via a WorldGuard session `Handler`
  (`onCrossBoundary`), not checked every tick.
- **World changes**: a world change fires no WorldGuard boundary event, so `PlayerListener` handles
  `PlayerChangedWorldEvent` and recomputes the override 2 ticks later (`DenyRegionHandler.recomputeOverride`),
  applying or restoring as needed.
- **Precedence** (`DenyRegionHandler.resolveOverrideSize`): **combat tag → region → world → temporary → permanent**.
  Each level only applies when nothing above it does; the visible size is always the topmost active one.
- **Applied on join**: if the player is already inside a DENY/SIZE region *or* world when they join, it's
  applied 3 seconds after join (`DenyRegionHandler.applyOverrideIfApplicable`).
- **Caches**: `DENY-REGION` (RegionManager), `SIZE-REGION` (DenyRegionHandler) and `DENY-WORLD`/`SIZE-WORLD`
  (WorldManager) are cached in memory and reloaded with `/sizechange reload` (which also reloads
  `setting.yml`, `message.yml` and `menu.yml`).
- **Automatic restore on exit**: retrieves the saved size (permanent or temporary with remaining time) and
  reapplies it — unless a lower-precedence override (a world, when leaving a region) still applies.
- **No persistence**: size changes caused by regions or worlds are never written to the database.
- **Expiration inside a region/world**: if a temporary size expires while the player is still inside a
  DENY/SIZE region or world, the forced size stays in effect instead of immediately restoring the original.

### PvP Combat Tag

- **Trigger**: `EntityDamageByEntityEvent` at `MONITOR` / `ignoreCancelled` — only a hit that actually landed
  (not cancelled by a PvP flag or protection plugin) counts. The damager is resolved through a projectile to
  its player shooter. `CombatListener` → `CombatTagManager.onPvpHit(attacker, victim)`.
- **Both players** get tagged for `COMBAT.DURATION` seconds; each new hit re-stamps the expiry. `COMBAT.SIZE`
  is applied through the same `overriddenPlayers` set the regions use (`DenyRegionHandler.addVisualOverride`),
  at the top of the precedence chain.
- **Exclusions** are checked only when the hit lands: if either player is in a `COMBAT.EXCLUDED-REGIONS`
  region, neither is tagged. A tag already running is *not* cancelled by walking into an excluded region.
- **One 20-tick task** drives the countdown: updates the action bar / boss bar for each tagged player and,
  when a timer hits zero, calls `DenyRegionHandler.recomputeOverride` to restore the real size (region/world
  still in effect, active temp with remaining time, or permanent).
- **Displays** (`COMBAT.DISPLAY.ACTIONBAR` / `BOSSBAR` / `CHAT`) are independent; the boss bar colour/overlay
  come from `COMBAT.BOSSBAR`. All text is `message.yml` (`combat-*` keys, `{seconds}` placeholder).
- `sizechange.bypass` players are never tagged. On quit the tag is dropped and the real permanent size is
  saved (not the combat one); tags do not survive a relog.

---

## 📊 Database Comparison

| Database    | Requires external server | Recommended for            | Advantages                                  |
|-------------|---------------------------|----------------------------|----------------------------------------------|
| **SQLite**  | No                        | Most servers                | Simple, fast, no extra configuration          |
| **MongoDB** | Yes                       | Networks / large servers    | Scalable, shareable across multiple servers   |

**Recommendation:** use **SQLite** unless you already run a network with a shared MongoDB.

---

## 📖 Full Examples

### Example 1: Survival Server with VIPs

```yaml
# setting.yml
DATABASE:
  TYPE: "SQLITE"

SIZE-REGION:
  vip_lounge: 1.5
```

```bash
/sizechange permanent set VIPPlayer1 1.2
/sizechange permanent set VIPPlayer2 1.2
```

### Example 2: Mini-Game "Giant vs. Ants"

```bash
/sizechange temporary set GiantPlayer 5.0 10m
/sizechange temporary set Player1 0.2 10m
/sizechange temporary set Player2 0.2 10m
```

### Example 3: Balanced PvP Zone

```yaml
# setting.yml
DENY-REGION:
  - "pvp_arena"  # Everyone forced to 1.0 size inside the PvP zone
```

---

## 🔄 How It Works

### Permanent Size Change

```
1. Run: /sizechange permanent set Player1 2.0
2. Changes the Attribute.SCALE attribute
3. Saves to the DB asynchronously
4. Persists across sessions
```

### Temporary Size

```
1. Run: /sizechange temporary set Player1 3.0 10m
2. Saves the current size as "original"
3. Applies the temporary size (3.0)
4. Schedules the restore for 10 minutes later
5. If the player disconnects: the state (original size + expiration) is saved to the DB
6. If they reconnect before it expires: the temporary size is restored with the remaining time
7. On expiration: automatically restores the original size
   (or keeps the forced size if the player is still inside a DENY/SIZE region)
```

### WorldGuard Regions

```
Player enters "mini_game" (SIZE-REGION: 0.3)
  → Caches the current size (temporary or original from the DB)
  → Changes to 0.3x

Player leaves "mini_game"
  → Retrieves the cached size
  → If they had an active temporary size, restores it with the recalculated remaining time
  → Otherwise, restores the permanent size from the DB
  → (If they're now in a DENY/SIZE world, that world's forced size applies instead)
  → Does NOT modify the database
```

### WorldGuard Worlds

```
Player teleports into a DENY-WORLD / SIZE-WORLD
  → PlayerChangedWorldEvent → recompute 2 ticks later
  → No relevant region here → world's forced size applied (visual only)

Player leaves the world
  → Recompute → no override left → restore temporary (with remaining time) or permanent size
  → Never touches the database
```

---

## 🚨 Troubleshooting

### Plugin doesn't start

```
✅ Check that WorldGuard (and WorldEdit) are installed
✅ Check the Java version (21 minimum)
✅ Check the console logs
```

### Database won't connect

```
✅ Check the configuration in setting.yml
✅ If it's MongoDB: verify the server is running and the URI is correct
✅ If it's SQLite: verify write permissions in the plugin's folder
```

### Sizes don't persist

```
✅ Check that TYPE in setting.yml is correct (SQLITE or MONGODB)
✅ Check that the file/collection is being created
✅ Check for connection errors in the console
```

---

## 🔧 Development

### Project Structure

```
src/main/java/dev/anhuar/
├── SizeChange.java             # Plugin's main class
├── command/
│   └── SizeCommand.java        # /sizechange command (Brigadier)
├── db/
│   ├── Database.java           # Persistence interface
│   ├── SQLiteDB.java            # SQLite implementation
│   └── MongoDB.java             # MongoDB implementation
├── handler/
│   ├── CommandHandler.java
│   ├── DenyRegionHandler.java   # override precedence (combat/region/world), enforcement, restore
│   ├── ListenerHandler.java
│   └── ManagerHandler.java
├── listener/
│   ├── PlayerListener.java      # Join/Quit/ChangedWorld
│   └── CombatListener.java      # EntityDamageByEntity -> combat tag
├── hook/
│   └── PlaceholderHook.java     # PlaceholderAPI expansion (%sizechange_...%)
├── manager/
│   ├── PlayerDataManager.java   # DB management, dirty tracking and auto-save
│   ├── SizeManager.java         # Reads/writes Attribute.SCALE (instant)
│   ├── SizeAnimator.java        # Smooth ~0.8s interpolation, command-triggered changes only
│   ├── ParticleAnimator.java    # Drawn particle shapes (HELIX / AURA / BURST)
│   ├── RegionManager.java       # DENY-REGION cache
│   ├── WorldManager.java        # DENY-WORLD / SIZE-WORLD cache
│   ├── CombatTagManager.java    # PvP combat tags, countdown task, bossbar/actionbar
│   └── TemporarySizeManager.java # Temporary sizes and their expiration
├── menu/
│   ├── MenuHolder.java          # InventoryHolder + functional click handler; includes
│   │                            # the nested MenuHolder.MenuListener (cancels clicks/drag)
│   ├── MenuItemFactory.java     # Shared helpers: builds ItemStacks from menu.yml
│   ├── ChatInputManager.java    # Captures a player's next chat message (with "cancel")
│   ├── PlayerListMenu.java      # Paginated GUI for /sizechange list (read-only)
│   ├── RegionTypeMenu.java      # /sizechange config hub (region/world/combat/effects)
│   ├── RegionConfigMenu.java    # Lists+adds+removes (with confirmation) one name/size collection
│   ├── SettingsMenu.java        # Generic setting.yml editor GUI (Combat / Effects screens)
│   └── ValuePickerMenu.java     # Paginated string picker (browse every Sound / Particle)
├── model/
│   ├── PlayerData.java
│   └── TemporarySizeData.java
├── hook/
│   └── PlaceholderHook.java     # PlaceholderAPI expansion (registered only if PAPI is present)
└── util/
    ├── ColorUtil.java
    ├── ConfigUtil.java
    └── MessageUtil.java
```

### Build

```bash
./gradlew clean shadowJar
# JAR at: build/libs/SizeChange-1.0-SNAPSHOT.jar
```

### Tests

```bash
./gradlew test
```

The tests (JUnit 5) cover the pure logic that doesn't depend on the server: duration
parsing and formatting (`DurationUtil`) and temporary size state (`PlayerData`,
`TemporarySizeData`). Logic that touches the Bukkit or WorldGuard API isn't covered
and requires testing on a real server.

Stack: Paper Dev Bundle `1.21.4-R0.1-SNAPSHOT`, Java 21 (toolchain), WorldGuard/WorldEdit and PlaceholderAPI
`compileOnly`, Lombok. Runtime dependencies (Gson, MongoDB driver, SQLite JDBC) are declared as `libraries` in
`paper-plugin.yml` and downloaded by Paper on startup.

---

## ✨ Advanced Features

### Auto-Cleanup

- `DenyRegionHandler`'s cache and `PlayerDataManager`'s dirty tracking are cleared when a player disconnects.

### Error Handling

- If WorldGuard isn't installed: the plugin disables itself in `onEnable`.
- If the database connection fails (10s timeout): the plugin disables itself with a clear console error.

### Thread-Safety

- `ConcurrentHashMap`-based caches (regions, saved sizes, temporary sizes, dirty tracking).
- Every interaction with the Bukkit API is synchronized to the main thread from async callbacks.

---

## License

This project is licensed under the [GPL-3.0 License](LICENSE).

---

**Author:** Anhuar Dev
