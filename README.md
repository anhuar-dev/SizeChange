# 🎮 SizeChange

Change any player's size on the fly — permanently or for a set duration — with full WorldGuard region control and database persistence. Built on Paper's native `Attribute.SCALE`, so there's no resource pack or client mod required.

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
- Runs on vanilla `Attribute.SCALE` — nothing to install client-side

### 🗺️ WorldGuard Region Control
- **DENY-REGION** — forces everyone back to normal size (1.0x) while inside
- **SIZE-REGION** — forces a specific size per region
- A player's active temporary size is preserved across a region visit and resumes with the correct time remaining when they leave
- `sizechange.bypass` lets staff ignore region enforcement entirely

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
- `/sizechange config` opens an **in-game editor** for `DENY-REGION`/`SIZE-REGION` — pick which one to manage, click an entry to remove it (with a confirmation step), click "add" and type the region name (and size, for SIZE-REGION) straight into chat — no more hand-editing YAML and `/sizechange reload`
- Both GUIs are **100% configurable** via `menu.yml` — title, size, fill item, navigation slots/materials, and every item's name/lore template, all editable, no code required
- Fully customizable messages — MiniMessage tags, hex colors (`&#RRGGBB`), and legacy `&`/`§` codes all supported
- A ready-to-copy **DeluxeMenus template** (`deluxemenu_template.yml`, generated on first run) — a self-service size picker players can open themselves, no permissions needed (requires the separate, optional DeluxeMenus plugin)

## 📋 Commands

Root command: **`/sizechange`** (no aliases).

| Command | Description | Permission |
|---|---|---|
| `/sizechange` | Show help | `sizechange.help` |
| `/sizechange permanent set <player> <size>` | Set a permanent size (`0.0625`–`16.0`) | `sizechange.permanent.set` |
| `/sizechange permanent reset <player>` | Reset the permanent size back to normal (`1.0`) | `sizechange.permanent.reset` |
| `/sizechange temporary set <player> <size> <duration>` | Apply a temporary size | `sizechange.temporary.set` |
| `/sizechange temporary clear <player>` | Cancel an active temporary size | `sizechange.temporary.clear` |
| `/sizechange view <player>` | View current permanent + temporary size in one reply | `sizechange.view` |
| `/sizechange list` | Open the paginated GUI of online players | `sizechange.list` |
| `/sizechange info` | Plugin diagnostics (version, database, active temporaries) | `sizechange.info` |
| `/sizechange config` | Open the GUI to add/remove DENY-REGION and SIZE-REGION entries | `sizechange.config` |
| `/sizechange reload` | Reload config, messages, menu and regions | `sizechange.reload` |
| — | Ignore DENY-REGION / SIZE-REGION enforcement | `sizechange.bypass` |

Duration formats: `s` (seconds), `m` (minutes), `h` (hours), `d` (days) — e.g. `30s`, `5m`, `2h`, `1d`.

> All permissions default to `op`, except `sizechange.bypass` which defaults to `false`. Assign them explicitly with your permissions plugin (LuckPerms, etc.) if you don't want every OP to have full access.

## ⚙️ Requirements

- **Minecraft:** 1.21.4
- **Server:** Paper (or Paper-based forks, e.g. Purpur) — not Spigot
- **Java:** 21+
- **WorldGuard:** 7.0.7+ and WorldEdit 7.3+ (required, must load before this plugin)
- **Database:** none required by default (SQLite); MongoDB 4.0+ only if you opt into it

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
already set is never touched or overwritten. The one exception is `DENY-REGION`/`SIZE-REGION`: those hold your own
region names, not a fixed schema, so they're only ever filled in if the whole section is missing — an update can't
tell "a new key this version added" apart from "an entry you deliberately deleted," so it never edits them once
they exist.

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
# SOUND/PARTICLE must be valid org.bukkit.Sound / org.bukkit.Particle names.
EFFECTS:
  ENABLED: true
  SOUND: "ENTITY_SLIME_SQUISH"
  PARTICLE: "POOF"

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
```

### `message.yml` - Customizable Messages

Available placeholders (vary by key): `{player}`, `{size}`, `{temp_size}`, `{duration}`, `{remaining}`, `{version}`, `{database}`, `{active_temp}`, `{regions}`, `{region}`.

```yaml
help:
  - "<gold><bold>SizeChange Plugin</bold>"
  - "<yellow>/sizechange permanent set <player> <size> <gray>- Change permanent size"
  - "<yellow>/sizechange permanent reset <player> <gray>- Reset size to normal"
  - "<yellow>/sizechange temporary set <player> <size> <duration> <gray>- Apply a temporary size"
  - "<yellow>/sizechange temporary clear <player> <gray>- Cancel temporary size"
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
config-add-prompt-size: "<yellow>Type the size (0.0625 - 16.0)."
config-add-duplicate: "<red>✗ Region <white>{region}</white> is already configured."
config-add-region-not-found-warning: "<gray>⚠ No region named <white>{region}</white> was found in any loaded world. It was saved anyway — double-check the name."
config-deny-added: "<green>✓ '{region}' added to DENY-REGION."
config-size-added: "<green>✓ '{region}' added to SIZE-REGION with size {size}."

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

Unlike the one above, this flow **does write to `setting.yml`**, and has two screens:

1. **`region-config-select`** — the selector opened by `/sizechange config`: two buttons, `deny-button`
   and `size-button`. Picking one opens `region-config` already scoped to that collection — the rest of
   the flow (add, list, remove) already knows whether you're managing `DENY-REGION` or `SIZE-REGION`, so
   it never asks for the type again.
2. **`region-config`** — the paginated list of that collection (templates `deny-entry`/`size-entry` with
   placeholders `{region}`/`{size}`; the title supports `{type}` → "DENY-REGION" or "SIZE-REGION"). Clicking
   an existing entry opens a **confirmation screen** (`confirm-delete`: `cancel`/`confirm` buttons, also
   configurable) before removing it — avoiding a misclick wiping out a configured region. The `back` button
   returns to the selector. The `add` button (configurable slot) triggers a **chat** flow: it asks for the
   region name (validates it doesn't already exist in either list — DENY or SIZE, regardless of which one
   you're editing —, and rejects empty names or names with a dot `.`, since Bukkit's `ConfigurationSection`
   treats the dot as a path separator), and if you're in the SIZE menu, it also asks for the numeric value
   (`0.0625`–`16.0`). At any step you can type `cancel` to abort.

After saving or removing, `regionManager.reloadDenyRegions()`/`DenyRegionHandler.reloadSizeRegions()` is
called automatically — the change applies live, no need for `/sizechange reload`. If the typed name
doesn't match any real WorldGuard region in the loaded worlds, it still saves but warns with
`config-add-region-not-found-warning` (useful for catching typos without blocking a legitimate save, e.g.
if the region belongs to a world that isn't loaded yet).

```yaml
region-config-select:
  title: "<dark_gray>Configure Regions"
  rows: 3
  deny-button:
    slot: 11
    material: BARRIER
    name: "<red>DENY-REGION"
  size-button:
    slot: 15
    material: PAPER
    name: "<yellow>SIZE-REGION"

region-config:
  title: "<dark_gray>{type} <gray>({page}/{total_pages})"
  rows: 6

  deny-entry:
    material: BARRIER
    name: "<red>{region}"
    lore:
      - "<gray>Forces <white>1.0</white> size on entry"
      - "<yellow>Click to remove (asks for confirmation)"

  size-entry:
    material: PAPER
    name: "<yellow>{region}"
    lore:
      - "<gray>Forces <white>{size}</white> size on entry"
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
- **Scoped effects**: same four command-triggered changes also play a sound + particle once per change
  (`EFFECTS.SOUND`/`EFFECTS.PARTICLE` in `setting.yml`, its own independent `EFFECTS.ENABLED` toggle — you can keep
  the smooth motion without the noise, or vice versa). An invalid sound/particle name logs a console warning and is
  skipped rather than crashing anything.

### WorldGuard Regions

- **Boundary-crossing detection**: triggered via a WorldGuard session `Handler` (`onCrossBoundary`), not checked
  every tick.
- **Applied on join**: if the player is already inside a DENY/SIZE region when they join, it's applied 3 seconds
  after join.
- **Static region cache**: the `DENY-REGION`/`SIZE-REGION` configuration is cached in memory and reloaded with
  `/sizechange reload` (which also reloads `setting.yml`, `message.yml` and `menu.yml`).
- **Automatic restore on exit**: retrieves the saved size (permanent or temporary with remaining time) and
  reapplies it.
- **No persistence**: size changes caused by regions are never written to the database.
- **Expiration inside a region**: if a temporary size expires while the player is still inside a DENY/SIZE region,
  the region's forced size stays in effect instead of immediately restoring the original.

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
  → Does NOT modify the database
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
│   ├── DenyRegionHandler.java   # DENY-REGION / SIZE-REGION enforcement
│   ├── ListenerHandler.java
│   └── ManagerHandler.java
├── listener/
│   └── PlayerListener.java      # Join/Quit
├── manager/
│   ├── PlayerDataManager.java   # DB management, dirty tracking and auto-save
│   ├── SizeManager.java         # Reads/writes Attribute.SCALE (instant)
│   ├── SizeAnimator.java        # Smooth ~0.5s interpolation, command-triggered changes only
│   ├── RegionManager.java       # DENY-REGION cache
│   └── TemporarySizeManager.java # Temporary sizes and their expiration
├── menu/
│   ├── MenuHolder.java          # InventoryHolder + functional click handler; includes
│   │                            # the nested MenuHolder.MenuListener (cancels clicks/drag)
│   ├── MenuItemFactory.java     # Shared helpers: builds ItemStacks from menu.yml
│   ├── ChatInputManager.java    # Captures a player's next chat message (with "cancel")
│   ├── PlayerListMenu.java      # Paginated GUI for /sizechange list (read-only)
│   ├── RegionTypeMenu.java      # DENY/SIZE selector opened by /sizechange config
│   └── RegionConfigMenu.java    # Lists+adds+removes (with confirmation) one region collection
├── model/
│   ├── PlayerData.java
│   └── TemporarySizeData.java
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

Stack: Paper Dev Bundle `1.21.4-R0.1-SNAPSHOT`, Java 21 (toolchain), WorldGuard/WorldEdit `compileOnly`, Lombok. Runtime
dependencies (Gson, MongoDB driver, SQLite JDBC) are declared as `libraries` in `paper-plugin.yml` and downloaded by
Paper on startup.

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
