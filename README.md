# SizeChange Plugin

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.3-green)
![Paper](https://img.shields.io/badge/Paper-1.21.3-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![License](https://img.shields.io/badge/License-MIT-yellow)

> Un plugin cosmético para Minecraft que permite cambiar el tamaño de los jugadores de forma dinámica.

## 📋 Descripción

SizeChange es un plugin de Minecraft desarrollado para servidores Paper 1.21.3+ que permite a los administradores modificar el tamaño visual de los jugadores. El plugin incluye soporte para MongoDB, integración con WorldGuard y gestión inteligente de mundos y regiones restringidas.

## ✨ Características

- 🎯 **Cambio de tamaño dinámico**: Modifica el tamaño de los jugadores de 0.1x a 10x
- 🗄️ **Persistencia de datos**: Integración completa con MongoDB
- 🌍 **Gestión de mundos**: Configuración de mundos donde el tamaño se restablece automáticamente
- 🛡️ **Integración WorldGuard**: Soporte para regiones donde el tamaño se normaliza
- ⚡ **Alto rendimiento**: Sistema de caché optimizado para regiones
- 🎮 **Modo creativo/espectador**: Ignora automáticamente estos modos de juego
- 🔄 **Recarga en caliente**: Recarga configuraciones sin reiniciar el servidor

## 📦 Instalación

### Requisitos

- **Minecraft**: 1.21.3+
- **Servidor**: Paper/Purpur/Folia
- **Java**: 21+
- **MongoDB**: 4.0+
- **WorldGuard**: 7.0.7+ (opcional)

### Pasos de instalación

1. Descarga el archivo `SizeChange-v1.0.jar`
2. Coloca el archivo en la carpeta `plugins/` de tu servidor
3. Reinicia el servidor
4. Configura MongoDB en `plugins/SizeChange/setting.yml`
5. Personaliza los mensajes en `plugins/SizeChange/message.yml`

## ⚙️ Configuración

### setting.yml

```yaml
MONGO:
  URI: "mongodb://localhost:27017"
  DATABASE: "SizeChange"
  COLLECTION: "players"

# Mundos donde el tamaño se restablece a 1.0
DENY-WORLD:
  - "Conquest"

# Regiones de WorldGuard donde el tamaño se normaliza
DENY-REGION:
  - "potzone"
  - "kothpot"
  - "crystalpvp"
  - "crystalkoth"
  - "mazopvp"
```

### message.yml

```yaml
ERROR:
  OFFLINE: "<red>Jugador no encontrado. El jugador debe estar conectado.</red>"
  INVALID-SIZE: "<red>El tamaño debe estar entre 0.1 y 10.</red>"
  SET-SIZE-FAILED: "<red>No se pudo establecer el tamaño para %player%.</red>"
  RESET-SIZE-FAILED: "<red>No se pudo restablecer el tamaño para %player%.</red>"

SUCCESS:
  SET-SIZE: "<green>Tamaño de %player% establecido a %size%</green>"
  RESET-SIZE: "<green>Tamaño de %player% restablecido al valor predeterminado.</green>"
```

## 🎮 Comandos

### Comando principal: `/sizechange`

| Subcomando | Descripción | Permiso | Uso |
|------------|-------------|---------|-----|
| `size set <jugador> <tamaño>` | Establece el tamaño de un jugador | `sizechange.admin` | `/sizechange size set Steve 2.0` |
| `size reset <jugador>` | Restablece el tamaño a 1.0 | `sizechange.admin` | `/sizechange size reset Steve` |
| `reload <tipo>` | Recarga configuraciones | `sizechange.admin` | `/sizechange reload setting` |

### Ejemplos de uso

```bash
# Hacer a un jugador más grande
/sizechange size set Notch 3.0

# Hacer a un jugador más pequeño
/sizechange size set Steve 0.5

# Restablecer el tamaño normal
/sizechange size reset Steve

# Recargar configuraciones
/sizechange reload setting
/sizechange reload message
/sizechange reload regioncache
```

## 🔑 Permisos

| Permiso | Descripción | Por defecto |
|---------|-------------|-------------|
| `sizechange.admin` | Acceso completo a todos los comandos | OP |

## 🏗️ Arquitectura del Plugin

### Estructura de paquetes

```
dev.anhuar.sizeChange/
├── SizeChange.java              # Clase principal
├── command/
│   └── SizeCommand.java         # Comandos del plugin
├── data/
│   └── DPlayer.java             # Modelo de datos del jugador
├── handler/
│   ├── CommandHandler.java      # Gestor de comandos
│   ├── ListenerHandler.java     # Gestor de eventos
│   ├── ManagerHandler.java      # Gestor de managers
│   └── MongoHandler.java        # Conexión MongoDB
├── listener/
│   ├── PlayerListener.java      # Eventos de jugador
│   └── WorldListener.java       # Eventos de mundo
├── manager/
│   ├── PlayerDataManager.java   # Gestión de datos
│   └── SizeManager.java         # Gestión de tamaños
├── task/
│   └── RegionTask.java          # Tarea de regiones
└── util/
    ├── ConfigUtil.java          # Utilidad de configuración
    └── GsonUtil.java            # Utilidad JSON
```

### Características técnicas

- **Asíncrono**: Operaciones de base de datos no bloquean el hilo principal
- **Caché inteligente**: Sistema de caché para regiones con actualización automática
- **Thread-safe**: Uso de `ConcurrentHashMap` para datos compartidos
- **Optimizado**: Verificaciones de mundo relevante antes de procesar regiones

## 🔧 Dependencias

### Dependencias principales

```kotlin
dependencies {
    paperweight.paperDevBundle("1.21.3-R0.1-SNAPSHOT")
    compileOnly("io.papermc.paper:paper-api:1.21.3-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.7")
    implementation("org.mongodb:mongodb-driver-sync:5.2.1")
    implementation("com.github.Revxrsal.Lamp:common:3.1.5")
    implementation("com.github.Revxrsal.Lamp:bukkit:3.1.5")
}
```

## 📊 Funcionamiento

### Flujo de datos

1. **Jugador se conecta** → Carga datos desde MongoDB
2. **Aplicación de tamaño** → Verifica mundo/región y aplica tamaño correspondiente
3. **Cambio de mundo** → Evalúa si debe normalizar o mantener tamaño
4. **Detección de región** → Task asíncrona verifica regiones cada 0.5 segundos
5. **Desconexión** → Guarda datos en MongoDB

### Casos especiales

- **Modo Creativo/Espectador**: El plugin ignora estos modos automáticamente
- **Mundos denegados**: El tamaño se fuerza a 1.0 independientemente del tamaño guardado
- **Regiones denegadas**: Similar a mundos, pero por ubicación específica
- **Reconexión**: El tamaño se restaura automáticamente desde la base de datos

## 🐛 Resolución de problemas

### Problemas comunes

**El tamaño no se aplica:**
- Verifica que el jugador no esté en modo creativo/espectador
- Confirma que no esté en un mundo o región denegada
- Revisa la conexión a MongoDB

**Error de conexión MongoDB:**
- Verifica la URI en `setting.yml`
- Confirma que MongoDB esté ejecutándose
- Revisa los logs del servidor para errores específicos

**Regiones no funcionan:**
- Instala WorldGuard 7.0.7+
- Verifica que las regiones existan con `/rg list`
- Recarga el caché con `/sizechange reload regioncache`

### Logs útiles

```bash
# Al iniciar el plugin
[INFO] ✓ Conexión con MongoDB establecida correctamente! (Base de datos: SizeChange)

# Al cerrar el plugin
[INFO] ✗ Conexión con MongoDB cerrada correctamente.
```

## 🤝 Contribución

### Cómo contribuir

1. Fork el repositorio
2. Crea una rama para tu feature (`git checkout -b feature/nueva-caracteristica`)
3. Commit tus cambios (`git commit -am 'Añade nueva característica'`)
4. Push a la rama (`git push origin feature/nueva-caracteristica`)
5. Crea un Pull Request

### Estándares de código

- Usa Java 21+
- Sigue las convenciones de naming de Java
- Documenta métodos públicos
- Incluye header de licencia en archivos nuevos

## 📝 Licencia

Este proyecto está bajo la Licencia MIT. Ver el archivo [LICENSE](LICENSE) para más detalles.

```
MIT License

Copyright (c) 2025 Anhuar Dev (https://anhuar.dev)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction...
```

## 👨‍💻 Autor

**Anhuar Ruiz** - *Anhuar Dev | myclass*
- Website: [anhuar.dev](https://anhuar.dev)
- GitHub: [@anhuar](https://github.com/anhuar)

## 📈 Versiones

### v1.0 (Actual)
- ✅ Sistema básico de cambio de tamaño
- ✅ Integración con MongoDB
- ✅ Soporte para WorldGuard
- ✅ Gestión de mundos y regiones denegadas
- ✅ Comandos de administración
- ✅ Sistema de permisos

### Roadmap futuro
- 🔄 API para desarrolladores
- 🎨 Interfaz gráfica (GUI)
- 📊 Estadísticas de uso
- 🌐 Soporte multi-idioma
- ⚡ Integración con PlaceholderAPI

---

<div align="center">

**⭐ Si te gusta este proyecto, ¡dale una estrella en GitHub! ⭐**

[Reportar Bug](https://github.com/anhuar/SizeChange/issues) • [Solicitar Feature](https://github.com/anhuar/SizeChange/issues) • [Documentación](https://anhuar.dev)

</div>