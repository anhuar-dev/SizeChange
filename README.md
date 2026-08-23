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

1. Download `SizeChange.jar` (or build it — see [Compilar](#compilar) below)
2. Drop it into your `plugins/` folder
3. Make sure **WorldGuard** and **WorldEdit** are installed
4. Start the server once to generate `setting.yml`, `message.yml` and `menu.yml`
5. *(Optional)* Set `DATABASE.TYPE` to `MONGODB` in `setting.yml` if you're running a network
6. Restart or `/sizechange reload`

---

# 🔧 Documentación Técnica

Lo siguiente está pensado para quien administre el plugin a bajo nivel o quiera editar el código fuente.

## ⚙️ Configuración

### `setting.yml` - Configuración Principal

```yaml
DATABASE:
  TYPE: "SQLITE" # SQLITE o MONGODB

  SQLITE:
    FILE: "players.db"

  MONGODB:
    URI: "mongodb://localhost:27017"
    DATABASE: "size_plugin"
    COLLECTION: "players"

# Regiones donde se fuerza tamaño 1.0 (normal) mientras el jugador esté dentro
DENY-REGION:
  - "spawn"
  - "pvp_arena"

# Regiones con tamaño forzado específico
SIZE-REGION:
  mini_game: 0.3   # Jugadores se hacen pequeños
  giant_zone: 3.0  # Jugadores se hacen gigantes
```

### `message.yml` - Mensajes Personalizables

Placeholders disponibles (varían según la clave): `{player}`, `{size}`, `{temp_size}`, `{duration}`, `{remaining}`, `{version}`, `{database}`, `{active_temp}`, `{regions}`, `{region}`.

```yaml
help:
  - "<gold><bold>SizeChange Plugin</bold>"
  - "<yellow>/sizechange permanent set <jugador> <tamaño> <gray>- Cambiar tamaño permanente"
  - "<yellow>/sizechange permanent reset <jugador> <gray>- Resetear tamaño a normal"
  - "<yellow>/sizechange temporary set <jugador> <tamaño> <duración> <gray>- Aplicar tamaño temporal"
  - "<yellow>/sizechange temporary clear <jugador> <gray>- Cancelar tamaño temporal"
  - "<yellow>/sizechange view <jugador> <gray>- Ver tamaño actual"
  - "<yellow>/sizechange list <gray>- Abrir el menú de jugadores conectados"
  - "<yellow>/sizechange info <gray>- Ver diagnóstico del plugin"
  - "<yellow>/sizechange config <gray>- Abrir el menú de regiones (DENY/SIZE)"
  - "<yellow>/sizechange reload <gray>- Recargar configuración"

size-set: "<green>✓ Tamaño de <white>{player}</white> establecido a <white>{size}</white>"
size-reset: "<green>✓ Tamaño de <white>{player}</white> reseteado a normal (1.0)"
size-changed: "<yellow>Tu tamaño ha sido cambiado a <white>{size}</white>"

temp-size-set: "<green>✓ Tamaño temporal de <white>{player}</white> establecido a <white>{size}</white> por <white>{duration}</white>"
temp-size-applied: "<yellow>Se te ha aplicado un tamaño temporal de <white>{size}</white> por <white>{duration}</white>"
temp-size-cleared: "<green>✓ Tamaño temporal de <white>{player}</white> cancelado y restaurado"

player-not-found: "<red>✗ El jugador <white>{player}</white> no está conectado"
invalid-duration: "<red>✗ Duración inválida. Usa formato: 30s, 5m, 2h, 1d"
gui-console-only: "<red>✗ Este comando solo puede ejecutarse en el juego."

info:
  - "<gold><bold>SizeChange</bold> <gray>v{version}</gray>"
  - "<yellow>Base de datos: <white>{database}</white>"
  - "<yellow>Tamaños temporales activos: <white>{active_temp}</white>"
  - "<yellow>Regiones configuradas: <white>{regions}</white>"

config-add-prompt-name: "<yellow>Escribe el nombre de la región."
config-add-prompt-size: "<yellow>Escribe el tamaño (0.0625 - 16.0)."
config-add-duplicate: "<red>✗ La región <white>{region}</white> ya está configurada."
config-add-region-not-found-warning: "<gray>⚠ No se encontró una región llamada <white>{region}</white> en ningún mundo cargado. Se guardó de todas formas — verifica el nombre."
config-deny-added: "<green>✓ '{region}' agregada a DENY-REGION."
config-size-added: "<green>✓ '{region}' agregada a SIZE-REGION con tamaño {size}."

reload: "<green>✓ Configuración recargada exitosamente"
```

Todas las claves reales están en `src/main/resources/message.yml`; edítalas directamente para personalizar textos,
colores (MiniMessage) y prefijo.

### `menu.yml` - Menú GUI de `/sizechange list`

Controla por completo el menú paginado que abre `/sizechange list`: título, filas, item de relleno, slots de
navegación (anterior/siguiente/info) y la plantilla de nombre+lore de cada cabeza de jugador. Placeholders de la
cabeza: `{player}`, `{size}`, `{has_temp}`, `{temp_size}`, `{remaining}`; placeholders de navegación: `{page}`,
`{total_pages}`, `{total_players}`.

```yaml
list:
  title: "<dark_gray>Jugadores conectados <gray>({page}/{total_pages})"
  rows: 6

  content:
    start-slot: 0
    end-slot: 44          # 45 slots visibles por página

  player-head:
    name: "<yellow>{player}"
    lore:
      - "<gray>Tamaño permanente: <white>{size}</white>"
      - "<gray>Temporal activo: <white>{has_temp}</white>"
      - "<gray>Tamaño temporal: <white>{temp_size}</white>"
      - "<gray>Tiempo restante: <white>{remaining}</white>"

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

El menú es **solo informativo**: hacer clic en una cabeza no hace nada (toda la info ya se ve en el lore al pasar el
cursor); solo los slots de `navigation.previous`/`navigation.next` reaccionan, cambiando de página. Solo lista
jugadores conectados — no hace queries nuevas a la base de datos.

### `menu.yml` - Menú GUI de `/sizechange config`

A diferencia del anterior, este flujo **sí escribe en `setting.yml`**, y tiene dos pantallas:

1. **`region-config-select`** — el selector que abre `/sizechange config`: dos botones, `deny-button` y
   `size-button`. Elegir uno abre `region-config` ya filtrado a esa colección — el resto del flujo (agregar, listar,
   eliminar) ya sabe si estás gestionando `DENY-REGION` o `SIZE-REGION`, así que no vuelve a preguntar el tipo.
2. **`region-config`** — la lista paginada de esa colección (plantillas `deny-entry`/`size-entry` con placeholders
   `{region}`/`{size}`; el título soporta `{type}` → "DENY-REGION" o "SIZE-REGION"). Clic en una entrada existente
   abre una **pantalla de confirmación** (`confirm-delete`: botones `cancel`/`confirm`, también configurables) antes
   de borrarla — evita que un misclick se lleve una región de un solo golpe. El botón `back` regresa al selector. El
   botón `add` (slot configurable) dispara un flujo por **chat**: pide el nombre de la región (valida que no exista
   ya en ninguna de las dos listas — DENY o SIZE, sin importar cuál estés editando —, y rechaza nombres vacíos o con
   un punto `.`, ya que `ConfigurationSection` de Bukkit interpreta el punto como separador de rutas), y si estás en
   el menú de SIZE, además pide el valor numérico (`0.0625`–`16.0`). En cualquier paso se puede escribir `cancel`
   para abortar.

Tras guardar o eliminar, se llama automáticamente a
`regionManager.reloadDenyRegions()`/`DenyRegionHandler.reloadSizeRegions()` — el cambio aplica en caliente, sin
necesitar `/sizechange reload`. Si el nombre escrito no coincide con ninguna región real de WorldGuard en los mundos
cargados, se guarda igual pero se avisa con `config-add-region-not-found-warning` (útil para detectar typos sin
bloquear el guardado, por ejemplo si la región es de un mundo que aún no está cargado).

```yaml
region-config-select:
  title: "<dark_gray>Configurar Regiones"
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
      - "<gray>Fuerza tamaño <white>1.0</white> al entrar"
      - "<yellow>Click para eliminar (pide confirmación)"

  size-entry:
    material: PAPER
    name: "<yellow>{region}"
    lore:
      - "<gray>Fuerza tamaño <white>{size}</white> al entrar"
      - "<yellow>Click para eliminar (pide confirmación)"

  add:
    slot: 49
    material: LIME_DYE
    name: "<green>+ Agregar {type}"

  back:
    slot: 45
    material: ARROW
    name: "<gray>← Volver"

  confirm-delete:
    title: "<red>¿Eliminar '{region}'?"
    rows: 3
    cancel:
      slot: 11
      material: LIME_STAINED_GLASS_PANE
      name: "<green>✔ Cancelar"
    confirm:
      slot: 15
      material: RED_STAINED_GLASS_PANE
      name: "<red>✘ Eliminar permanentemente"
```

---

## 🔧 Funcionalidades Técnicas

### Persistencia de Datos

- **Auto-guardado periódico**: cada 5 minutos, solo para jugadores cuyo estado cambió desde el último guardado (dirty
  tracking), evitando escrituras innecesarias.
- **Al desconectar**: guarda el tamaño real del jugador de forma inmediata (el permanente, no el visual si está en una
  región o con temporal activo).
- **Al conectar**: restaura el tamaño permanente guardado; si tenía un temporal activo y no expiró, lo restaura con el
  tiempo restante calculado desde su expiración guardada.
- **Tamaños temporales**: persisten entre reinicios del servidor mientras no hayan expirado.

### Rendimiento Optimizado

- **Operaciones asíncronas**: carga/guardado en DB sin bloquear el hilo principal.
- **Dirty tracking**: el guardado periódico solo escribe jugadores con cambios reales.
- **Thread-safe**: colecciones `ConcurrentHashMap` para caches de regiones, tamaños guardados y temporales.
- **Verificación periódica de temporales**: cada 30s (600 ticks) en un hilo asíncrono, la restauración real ocurre en el
  hilo principal.

### Regiones WorldGuard

- **Detección al cruzar límites**: se activa mediante un `Handler` de sesión de WorldGuard (`onCrossBoundary`), no
  revisa todos los ticks.
- **Aplicación al conectar**: si el jugador ya está dentro de una región DENY/SIZE al unirse, se le aplica 3 segundos
  después del join.
- **Cache estático de regiones**: la configuración de `DENY-REGION`/`SIZE-REGION` se cachea en memoria y se recarga con
  `/sizechange reload` (que también recarga `setting.yml`, `message.yml` y `menu.yml`).
- **Restauración automática al salir**: recupera el tamaño guardado (permanente o temporal con tiempo restante) y lo
  reaplica.
- **Sin persistencia**: los cambios de tamaño por región no se escriben en la base de datos.
- **Expiración dentro de una región**: si un tamaño temporal expira mientras el jugador sigue dentro de una región
  DENY/SIZE, se mantiene el tamaño forzado por la región en vez de restaurar el original de inmediato.

---

## 📊 Comparación de Bases de Datos

| Base de Datos | Requiere servidor externo | Recomendado para              | Ventajas                                       |
|---------------|---------------------------|-------------------------------|------------------------------------------------|
| **SQLite**    | No                        | La mayoría de servidores      | Simple, rápido, sin configuración adicional    |
| **MongoDB**   | Sí                        | Networks / servidores grandes | Escalable, compartible entre varios servidores |

**Recomendación:** usa **SQLite** salvo que ya operes una red con MongoDB compartido.

---

## 📖 Ejemplos Completos

### Ejemplo 1: Servidor Survival con VIPs

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

### Ejemplo 2: Mini-Juego "Gigante vs Hormigas"

```bash
/sizechange temporary set GiantPlayer 5.0 10m
/sizechange temporary set Player1 0.2 10m
/sizechange temporary set Player2 0.2 10m
```

### Ejemplo 3: Zona PvP Balanceada

```yaml
# setting.yml
DENY-REGION:
  - "pvp_arena"  # Todos a tamaño 1.0 dentro del PvP
```

---

## 🔄 Flujo de Funcionamiento

### Cambio de Tamaño Permanente

```
1. Ejecutar: /sizechange permanent set Player1 2.0
2. Cambia el atributo Attribute.SCALE
3. Guarda en DB de forma asíncrona
4. Persiste entre sesiones
```

### Tamaño Temporal

```
1. Ejecutar: /sizechange temporary set Player1 3.0 10m
2. Guarda el tamaño actual como "original"
3. Aplica el tamaño temporal (3.0)
4. Programa la restauración para dentro de 10 minutos
5. Si el jugador se desconecta: se guarda el estado (tamaño original + expiración) en DB
6. Si se reconecta antes de que expire: restaura el temporal con el tiempo restante
7. Al expirar: restaura el tamaño original automáticamente
   (o mantiene el tamaño forzado si el jugador sigue dentro de una región DENY/SIZE)
```

### Regiones WorldGuard

```
Jugador entra a "mini_game" (SIZE-REGION: 0.3)
  → Guarda el tamaño actual (temporal u original de DB) en cache
  → Cambia a 0.3x

Jugador sale de "mini_game"
  → Recupera el tamaño guardado del cache
  → Si tenía un temporal activo, lo restaura con el tiempo restante recalculado
  → Si no, restaura el tamaño permanente desde la DB
  → NO modifica la base de datos
```

---

## 🚨 Solución de Problemas

### Plugin no inicia

```
✅ Verificar que WorldGuard (y WorldEdit) estén instalados
✅ Revisar la versión de Java (mínimo 21)
✅ Comprobar logs en consola
```

### Base de datos no conecta

```
✅ Verificar configuración en setting.yml
✅ Si es MongoDB: verificar que el servidor esté corriendo y la URI sea correcta
✅ Si es SQLite: verificar permisos de escritura en la carpeta del plugin
```

### Tamaños no persisten

```
✅ Verificar que TYPE en setting.yml sea correcto (SQLITE o MONGODB)
✅ Revisar que el archivo/colección se esté creando
✅ Comprobar que no haya errores en consola al conectar
```

---

## 🔧 Desarrollo

### Estructura del Proyecto

```
src/main/java/dev/anhuar/
├── SizeChange.java             # Clase principal del plugin
├── command/
│   └── SizeCommand.java        # Comando /sizechange (Brigadier)
├── db/
│   ├── Database.java           # Interfaz de persistencia
│   ├── SQLiteDB.java            # Implementación SQLite
│   └── MongoDB.java             # Implementación MongoDB
├── handler/
│   ├── CommandHandler.java
│   ├── DenyRegionHandler.java   # Enforcement de DENY-REGION / SIZE-REGION
│   ├── ListenerHandler.java
│   └── ManagerHandler.java
├── listener/
│   └── PlayerListener.java      # Join/Quit
├── manager/
│   ├── PlayerDataManager.java   # Gestión de DB, dirty tracking y auto-guardado
│   ├── SizeManager.java         # Lectura/escritura de Attribute.SCALE
│   ├── RegionManager.java       # Cache de DENY-REGION
│   └── TemporarySizeManager.java # Tamaños temporales y su expiración
├── menu/
│   ├── MenuHolder.java          # InventoryHolder + click handler funcional; incluye
│   │                            # MenuHolder.MenuListener anidado (cancela clicks/drag)
│   ├── MenuItemFactory.java     # Helpers compartidos: construye ItemStacks desde menu.yml
│   ├── ChatInputManager.java    # Captura el próximo mensaje de chat de un jugador (con "cancel")
│   ├── PlayerListMenu.java      # GUI paginado de /sizechange list (solo lectura)
│   ├── RegionTypeMenu.java      # Selector DENY/SIZE que abre /sizechange config
│   └── RegionConfigMenu.java    # Lista+agrega+quita (con confirmación) una colección de regiones
├── model/
│   ├── PlayerData.java
│   └── TemporarySizeData.java
└── util/
    ├── ColorUtil.java
    ├── ConfigUtil.java
    └── MessageUtil.java
```

### Compilar

```bash
./gradlew clean shadowJar
# JAR en: build/libs/SizeChange-1.0-SNAPSHOT.jar
```

### Tests

```bash
./gradlew test
```

Los tests (JUnit 5) cubren la lógica pura que no depende del servidor: parseo y
formato de duraciones (`DurationUtil`) y el estado de los tamaños temporales
(`PlayerData`, `TemporarySizeData`). La lógica que toca la API de Bukkit o
WorldGuard no está cubierta y requiere pruebas en un servidor real.

Stack: Paper Dev Bundle `1.21.4-R0.1-SNAPSHOT`, Java 21 (toolchain), WorldGuard/WorldEdit `compileOnly`, Lombok. Las
dependencias de runtime (Gson, MongoDB driver, SQLite JDBC) se declaran como `libraries` en `paper-plugin.yml` y las
descarga Paper al arrancar.

---

## ✨ Características Avanzadas

### Auto-Limpieza

- El cache de `DenyRegionHandler` y el dirty tracking de `PlayerDataManager` se limpian al desconectar un jugador.

### Manejo de Errores

- Si WorldGuard no está instalado: el plugin se deshabilita en `onEnable`.
- Si la conexión a la base de datos falla (timeout de 10s): el plugin se deshabilita con error claro en consola.

### Thread-Safety

- Caches basados en `ConcurrentHashMap` (regiones, tamaños guardados, tamaños temporales, dirty tracking).
- Toda interacción con la API de Bukkit se sincroniza al hilo principal desde callbacks asíncronos.

---

## Licencia

Este proyecto está bajo la [Licencia GPL-3.0](LICENSE).

---

**Autor:** Anhuar Dev
