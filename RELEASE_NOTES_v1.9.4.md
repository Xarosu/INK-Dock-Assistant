# INK-Dock Assistant - Versión 1.9.4

**Publicada en GitHub**: 16 de noviembre de 2026 (tag `v1.9.4`)
**Versión anterior en el repositorio**: 1.6.2 (tag `v1.6.2`)

## Novedades principales

### 1. Conexión con ESP32 por USB
- Nuevo modo de conexión **USB ESP32**, además del modo Ethernet con PLC
- Cliente `ESP32SerialClient`: USB serie a 115200 baudios con tramas tipo Host Link (`@`, comando, área, dirección, datos, FCS, `*`)
- Modelos de controlador: **ESP32-P1**, **ESP32-P2**, **ESP32-P3** y **ESP32-P4**
- Detección automática al conectar la placa (chips CP2102/CP2104, CH340 y FTDI, en `res/xml/device_filter.xml`)
- Diálogo de selección de dispositivo USB (`UsbDeviceSelectionDialog`)
- En modo ESP32 solo se gestionan Plataforma y Semáforos; Abrigo y Puerta siguen siendo exclusivos del PLC

### 2. PLC OMRON CP2E-N por Ethernet
- Modelos CP2E-N14, N20, N30, N40 y N60 (DR-A) con su configuración de E/S
- Conexión recomendada mediante adaptador USB-Ethernet
- `ConnectionManager` centraliza la creación del cliente (FINS/TCP o ESP32) según el modo

### 3. Sistema de LOG
- Buffer circular ampliado de 150 a **300 eventos** (D600-D2099, 5 palabras por evento)
- Fecha y hora en BCD (YYMM, DDHH, MMSS) y código de evento en la quinta palabra
- Configuración del PLC en el CSV tomada de la app (modelo, IP, pedido, muelle) en lugar de leerla del PLC

### 4. Pantallas
- Configuración de **Semáforos** completa
- **Sinóptico** rehecho con LEDs por canal de E/S y lectura/escritura de direcciones
- Pantallas de Abrigo, Puerta y Plataforma rehechas (temporizadores, contadores con reset, switches, números de serie)
- Configuración del muelle: número de pedido (10 dígitos/2 dígitos) y número de muelle en BCD
- Pantalla de **Soporte** nueva

### 5. Idiomas
- Interfaz en **español, inglés y francés** (`LanguageManager`)

## Cambios en el repositorio
- Licencia **GNU GPL v3 o posterior**: archivo `LICENSE` con el texto oficial y cabecera de licencia en los archivos fuente
- Se retiran el APK y `local.properties` del repositorio; los binarios se distribuyen aparte
- Documentación actualizada a esta versión (README, MEMORY_MAP y resto de guías)

## Datos de la versión
- **Nombre en el dispositivo**: INK-Dock_assistant
- **versionName**: 1.9.4 · **versionCode**: 9
- **applicationId**: `com.example.ink_omron_viewer` (sin cambios, para que se actualice encima de las versiones instaladas)
- **Requisitos mínimos**: Android 7.0 (API 24) · **Target SDK**: 34

## Puntos pendientes conocidos
- El `LogManager` lee el estado de switches de Abrigo y Puerta para el CSV desde H0/H1, mientras que las pantallas de máquina usan H120 y H122 (ver MEMORY_MAP.md)
- Los eventos del LOG se ordenan por el texto `dd/MM/yy HH:mm:ss`, por lo que el orden no es cronológico cuando cambian el mes o el año
- Room, WorkManager y commons-net están declaradas como dependencias pero no se usan
- El APK no se publica todavía como GitHub Release
