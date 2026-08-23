# 🎮 SizeChange

Change any player's size on the fly — permanently or for a set duration — with full WorldGuard region control and database persistence. Built on Paper's native `Attribute.SCALE`, so there's no resource pack or client mod required.

---

## 🎯 Use Cases

- Roleplay servers with different races and sizes
- Mini-games with size-based mechanics (giants vs. ants, shrink challenges, etc.)
- PvP arenas that normalize every player to the same size
- Timed events and rewards (temporary giant/mini effects)
- Rank/VIP perks tied to a bigger size in specific zones

---

## 🚀 Features

### 📏 Dynamic Player Scaling
- Change player size from **0.0625x to 16x**, instantly, no reconnect required
- **Permanent** sizes persist across sessions; **temporary** sizes auto-revert when they expire
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

---

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

---

## ⚙️ Requirements

- **Minecraft:** 1.21.4
- **Server:** Paper (or Paper-based forks, e.g. Purpur) — not Spigot
- **Java:** 21+
- **WorldGuard:** 7.0.7+ and WorldEdit 7.3+ (required, must load before this plugin)
- **Database:** none required by default (SQLite); MongoDB 4.0+ only if you opt into it

---

## 📦 Installation

1. Download `SizeChange.jar`
2. Drop it into your `plugins/` folder
3. Make sure **WorldGuard** and **WorldEdit** are installed
4. Start the server once to generate `setting.yml`, `message.yml` and `menu.yml`
5. *(Optional)* Set `DATABASE.TYPE` to `MONGODB` in `setting.yml` if you're running a network
6. Restart or `/sizechange reload`
