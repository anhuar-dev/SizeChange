# 🎮 SizeChange - Minecraft

Plugin de Minecraft (Paper) que permite modificar el tamaño (escala) de los jugadores de forma dinámica, con
persistencia en base de datos y control por regiones de WorldGuard.

---

## 📋 Características Principales

### ✨ Cambio de Tamaño

- **Tamaño permanente**: cambia el tamaño de un jugador y persiste entre sesiones.
- **Tamaño temporal**: aplica un tamaño por tiempo limitado y restaura el original al expirar.
- **Rango**: 0.0625x (miniatura) hasta 16.0x (gigante), usando `Attribute.SCALE`.
- **Persistencia**: se guarda automáticamente en base de datos (asíncrono, sin bloquear el servidor).

### 🗺️ Control por Regiones (WorldGuard)

- **DENY-REGION**: fuerza tamaño 1.0 (normal) al entrar, restaura al salir.
- **SIZE-REGION**: fuerza un tamaño específico por región al entrar, restaura al salir.
- **Interacción con temporales**: si el jugador tenía un tamaño temporal activo al entrar a la región, al salir se le
  restaura ese temporal con el tiempo restante recalculado (no se pierde el conteo).
- **Sin modificar la DB**: los cambios de tamaño causados por regiones son solo visuales/temporales, no se escriben en
  base de datos.
- **Permiso de bypass**: `sizechange.bypass` permite ignorar por completo la lógica de regiones.

### 💾 Base de Datos Flexible

- **SQLite**: por defecto, sin servidor externo, ideal para la mayoría de servidores.
- **MongoDB**: para redes de servidores o instalaciones grandes.

---

## 🚀 Instalación

1. **Requisitos:**
    - Paper 1.21.4 (api-version `1.21`)
    - Java 21+
    - WorldGuard 7.0.7+ y WorldEdit 7.3+ (dependencia obligatoria, se carga antes que el plugin)

2. **Instalar:**
   ```bash
   # Copiar el JAR a la carpeta plugins
   plugins/SizeChange-1.0-SNAPSHOT.jar

   # Iniciar el servidor (genera los archivos de configuración)
   ```
   Las librerías (Gson, driver de MongoDB, SQLite JDBC) se descargan automáticamente mediante el sistema de librerías de
   Paper Plugin Loader; no es necesario incluirlas en el JAR.

3. **Configurar:**
    - Edita `plugins/SizeChange/setting.yml`
    - Edita `plugins/SizeChange/message.yml` (opcional)

---

## 📝 Comandos

Comando raíz: **`/sizechange`** (sin alias registrados).

```bash
/sizechange
# Muestra la ayuda del comando (permiso: sizechange.help)

/sizechange set <jugador> <tamaño>
# Ejemplo: /sizechange set Steve 2.0
# Cambia el tamaño permanentemente y lo guarda en la DB (permiso: sizechange.set)

/sizechange reset <jugador>
# Ejemplo: /sizechange reset Steve
# Restaura el tamaño a 1.0 y lo guarda en la DB (permiso: sizechange.reset)

/sizechange view <jugador>
# Ejemplo: /sizechange view Steve
# Muestra el tamaño actual (y el temporal si tiene uno activo) (permiso: sizechange.view)

/sizechange temp <jugador> <tamaño> <duración>
# Ejemplos:
/sizechange temp Steve 3.0 30s
/sizechange temp Steve 0.5 5m
/sizechange temp Steve 10.0 2h
/sizechange temp Steve 2.0 1d
# Aplica un tamaño temporal sin tocar el permanente guardado (permiso: sizechange.temp)

/sizechange cleartemp <jugador>
# Cancela el tamaño temporal activo y restaura el tamaño original (permiso: sizechange.cleartemp)

/sizechange reload
# Recarga setting.yml, message.yml y las regiones DENY/SIZE (permiso: sizechange.reload)
```

### Formatos de Tiempo soportados

Solo se aceptan estos sufijos (cualquier otro formato se rechaza como duración inválida):

- `s` - Segundos
- `m` - Minutos
- `h` - Horas
- `d` - Días

---

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

Placeholders disponibles: `{player}`, `{size}`, `{temp_size}`, `{duration}`, `{remaining}`.

```yaml
help:
  - "<gold><bold>SizeChange Plugin</bold>"
  - "<yellow>/sizechange set <jugador> <tamaño> <gray>- Cambiar tamaño permanente"
  - "<yellow>/sizechange view <jugador> <gray>- Ver tamaño actual"
  - "<yellow>/sizechange reset <jugador> <gray>- Resetear tamaño a normal"
  - "<yellow>/sizechange temp <jugador> <tamaño> <duración> <gray>- Aplicar tamaño temporal"
  - "<yellow>/sizechange cleartemp <jugador> <gray>- Cancelar tamaño temporal"
  - "<yellow>/sizechange reload <gray>- Recargar configuración"

size-set: "<green>✓ Tamaño de <white>{player}</white> establecido a <white>{size}</white>"
size-reset: "<green>✓ Tamaño de <white>{player}</white> reseteado a normal (1.0)"
size-changed: "<yellow>Tu tamaño ha sido cambiado a <white>{size}</white>"

temp-size-set: "<green>✓ Tamaño temporal de <white>{player}</white> establecido a <white>{size}</white> por <white>{duration}</white>"
temp-size-applied: "<yellow>Se te ha aplicado un tamaño temporal de <white>{size}</white> por <white>{duration}</white>"
temp-size-cleared: "<green>✓ Tamaño temporal de <white>{player}</white> cancelado y restaurado"

player-not-found: "<red>✗ El jugador <white>{player}</white> no está conectado"
invalid-duration: "<red>✗ Duración inválida. Usa formato: 30s, 5m, 2h, 1d"
reload: "<green>✓ Configuración recargada exitosamente"
```

Todas las claves reales están en `src/main/resources/message.yml`; edítalas directamente para personalizar textos,
colores (MiniMessage) y prefijo.

---

## 🎯 Casos de Uso

### 1. Sistema de Rangos

```yaml
# VIPs más grandes en su zona
SIZE-REGION:
  vip_zone: 1.2
```

```bash
/sizechange set VIPPlayer 1.3
```

### 2. Mini-Juegos

```bash
# Todos los participantes se hacen pequeños por 10 minutos
/sizechange temp Player1 0.2 10m
/sizechange temp Player2 0.2 10m
```

### 3. Eventos Temporales

```bash
# Jugador gigante por 1 hora (evento especial)
/sizechange temp EventWinner 5.0 1h
```

### 4. Zonas Especiales

```yaml
SIZE-REGION:
  secret_tunnel: 0.3
```

### 5. Castigos Temporales

```bash
/sizechange temp TrollPlayer 0.2 30m
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
  `/sizechange reload`.
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

## 🛡️ Permisos

```yaml
sizechange.help       # Ver la ayuda del comando (nodo raíz de /sizechange)
sizechange.set        # Cambiar tamaño permanente
sizechange.reset      # Resetear tamaño a normal
sizechange.view       # Ver tamaño (permanente y temporal) de un jugador
sizechange.temp       # Aplicar tamaño temporal
sizechange.cleartemp  # Cancelar tamaño temporal
sizechange.reload     # Recargar configuración
sizechange.bypass     # Ignorar por completo la lógica de DENY-REGION / SIZE-REGION
```

> Estos permisos no vienen predefinidos en `paper-plugin.yml`; asígnalos con tu plugin de permisos (LuckPerms, etc.).

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
/sizechange set VIPPlayer1 1.2
/sizechange set VIPPlayer2 1.2
```

### Ejemplo 2: Mini-Juego "Gigante vs Hormigas"

```bash
/sizechange temp GiantPlayer 5.0 10m
/sizechange temp Player1 0.2 10m
/sizechange temp Player2 0.2 10m
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
1. Ejecutar: /sizechange set Player1 2.0
2. Cambia el atributo Attribute.SCALE
3. Guarda en DB de forma asíncrona
4. Persiste entre sesiones
```

### Tamaño Temporal

```
1. Ejecutar: /sizechange temp Player1 3.0 10m
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
