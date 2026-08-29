# bStats — SizeChange

Internal notes on how bStats is wired into this plugin and how to turn it on.
Not linked from `README.md` on purpose — the plugin id is a developer concern,
not a server-admin setting.

---

## What it is

[bStats](https://bstats.org) is an anonymous metrics service for Minecraft
plugins. On a schedule it POSTs a small JSON payload to `https://bStats.org/api`
with things like server software, online player count, Java version, plugin
version and any custom charts the plugin defines. No IPs, no player names, no
world names, no config values other than the ones a custom chart explicitly
reports. Server owners can opt every plugin out globally in
`plugins/bStats/config.yml` (`enabled: false`).

---

## State in this plugin

- Dependency: `org.bstats:bstats-bukkit:3.1.0`, declared as `implementation` in
  `build.gradle.kts` (shaded into the jar, **not** a Paper `library`).
- Relocated: `shadowJar { relocate("org.bstats", "dev.anhuar.bstats") }`. bStats
  **requires** every plugin to relocate it — two plugins sharing an unrelocated
  `org.bstats` on the classpath is a documented breakage. After a build,
  `unzip -l build/libs/SizeChange-*.jar | grep bstats` must show
  `dev/anhuar/bstats/...` and never a bare `org/bstats/...`.
- Wiring: `SizeChange#setupMetrics()`, called near the end of `onEnable()`.
- **Off by default.** The id is the compile-time constant
  `SizeChange.BSTATS_PLUGIN_ID`, initialised to `0`. `setupMetrics()` returns
  immediately while it is `<= 0`, so nothing is ever constructed or sent.
- There is **no `setting.yml` entry** for this on purpose — a server admin has no
  use for the plugin id, and the only knob they need (turn all metrics off) is
  the global bStats config.

---

## Turning it on (developer, one time)

1. Sign in at <https://bstats.org> → **Add plugin** → platform **Bukkit**, name
   `SizeChange`. You get a numeric **plugin id** (also at
   <https://bstats.org/what-is-my-plugin-id>).
2. In `SizeChange.java`, set the constant:

   ```java
   private static final int BSTATS_PLUGIN_ID = 12345; // the real id
   ```
3. Rebuild (`./gradlew clean build`) and ship that jar. First data point is sent
   ~3 minutes after a server starts, then every 30 minutes.
4. Public charts: `https://bstats.org/plugin/bukkit/SizeChange/<id>`.

To pull it back out, set the constant to `0` and rebuild.

---

## What this plugin reports

**Default bStats charts** (same for every plugin): server software & version,
online players, offline mode, Java version, OS, core count, plugin version,
location (country, resolved server-side from the server IP, not stored).

**Custom charts** defined in `setupMetrics()`:

| Chart id | Type | Value |
|---|---|---|
| `database_type` | SimplePie | `DATABASE.TYPE` from `setting.yml` (`SQLITE` / `MONGODB`) |
| `animation_enabled` | SimplePie | `ANIMATION.ENABLED` as `true` / `false` |
| `active_temporary_sizes` | SingleLineChart | `TemporarySizeManager#activeCount()` at send time |

To add a chart, add another `metrics.addCustomChart(...)` in `setupMetrics()`.
Chart types live in `dev.anhuar.bstats.charts.*` (relocated) — `SimplePie`,
`SingleLineChart`, `AdvancedPie`, `DrilldownPie`, `MultiLineChart`, `BarChart`.
Keep the lambdas cheap and null-safe; they run off the main thread on the bStats
submit schedule.

---

## Disabling

- Not compiled with an id → already off.
- Compiled with an id, one server wants out → `plugins/bStats/config.yml` →
  `enabled: false` (this is server-wide, all plugins). The `Metrics` constructor
  checks it itself.

---

## Troubleshooting

- **No data on the site** — wait 30+ min for the first real submission; confirm
  `BSTATS_PLUGIN_ID` is non-zero in the shipped jar and matches the site.
- **`NoClassDefFoundError: org/bstats/...` or a clash with another plugin** —
  the relocation didn't run. Build with `./gradlew clean build` and re-check the
  jar contents.
- **`ClassNotFoundException` for `dev.anhuar.bstats.bukkit.Metrics`** — bStats
  ended up as `compileOnly`/`library` instead of `implementation`; it must be
  shaded.
