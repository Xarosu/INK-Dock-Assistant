# INK-Dock Assistant

<div align="center">

**Aplicación Android para monitorización y configuración de muelles de carga con PLC OMRON CP2E y controladores ESP32**

Versión actual: **1.9.4** · Licencia: **GPL v3 o posterior**

[Características](#características-principales) • [Instalación](#instalación-y-configuración) • [Documentación](#documentación) • [Soporte](#soporte-y-contacto)

</div>

---

## Índice

1. [Descripción General](#descripción-general)
2. [Características Principales](#características-principales)
3. [Requisitos del Sistema](#requisitos-del-sistema)
4. [Arquitectura del Proyecto](#arquitectura-del-proyecto)
5. [Estructura de Directorios](#estructura-de-directorios)
6. [Documentación](#documentación)
7. [Instalación y Configuración](#instalación-y-configuración)
8. [Uso de la Aplicación](#uso-de-la-aplicación)
9. [Desarrollo](#desarrollo)
10. [Notas de Versión](#notas-de-versión)
11. [Soporte y Contacto](#soporte-y-contacto)
12. [Licencia](#licencia)

---

## Descripción General

**INK-Dock Assistant** (en el dispositivo aparece como `INK-Dock_assistant`) es una aplicación Android desarrollada por X. Rodriguez para la monitorización, configuración y registro de actividad de muelles de carga.

La aplicación tiene dos modos de conexión:

| Modo | Equipo | Medio | Máquinas soportadas |
|------|--------|-------|---------------------|
| **Ethernet** | PLC OMRON CP2E-N (N14/N20/N30/N40/N60) | FINS/TCP, puerto 9600, normalmente con adaptador USB-Ethernet | Abrigo, Puerta, Plataforma, Semáforos |
| **USB ESP32** | Placa ESP32 (P1, P2, P3, P4) | USB serie, 115200 baudios | Plataforma y Semáforos |

Con ella se puede:
- Monitorizar en tiempo real las entradas y salidas del controlador (sinóptico)
- Configurar el muelle: máquinas activas, número de pedido y número de muelle
- Ajustar temporizadores, resetear contadores y cambiar switches de cada máquina
- Guardar números de serie de las máquinas
- Leer el registro circular de eventos del PLC y exportarlo a CSV, email o FTP

### Tecnologías Utilizadas

- **Lenguaje**: Kotlin
- **Arquitectura**: MVVM (Model-View-ViewModel)
- **UI**: Material Design, ViewBinding, Navigation Component
- **Comunicación PLC**: FINS/TCP (implementación propia, sin librerías externas)
- **Comunicación ESP32**: USB serie con [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) y tramas tipo Host Link
- **Concurrencia**: Kotlin Coroutines
- **Idiomas**: Español, inglés y francés
- **IA**: Claude Code (depuración del código y redacción de documentación)

---

## Características Principales

### 1. Comunicación con PLC OMRON (modo Ethernet)
- ✅ Protocolo FINS/TCP nativo (sin librerías externas)
- ✅ PLC OMRON serie CP2E-N: N14, N20, N30, N40 y N60 (modelos DR-A)
- ✅ Conexión mediante adaptador USB-Ethernet (recomendado) o Wi-Fi en la misma red
- ✅ Lectura y escritura de memoria (DM, HR, WR, CIO)
- ✅ Lecturas en bloques de 50 palabras como máximo para evitar el error FINS 4363

### 2. Comunicación con ESP32 (modo USB)
- ✅ Detección automática al conectar la placa por USB (chips CP2102/CP2104, CH340 y FTDI)
- ✅ Diálogo de selección cuando hay varios dispositivos USB
- ✅ Modelos: ESP32-P1, ESP32-P2, ESP32-P3 y ESP32-P4
- ⚠️ En modo ESP32 solo están disponibles Plataforma y Semáforos (Abrigo y Puerta requieren PLC)

### 3. Gestión de Máquinas
- ✅ **Abrigo** (Shelter): temporizador, horas desde mantenimiento, horas totales del soplador, switches
- ✅ **Puerta seccional** (Door): temporizador, maniobras de subida y bajada, horas, switches
- ✅ **Plataforma niveladora** (Platform): dos temporizadores, contadores de maniobras, horas de bomba, switches
- ✅ **Semáforos** (Traffic Light): temporizador, contadores, switches

Cada máquina incluye reset de contadores y número de serie (BCD).

### 4. Sistema de LOG
- ✅ Buffer circular de **300 eventos** en el PLC (D600-D2099, 5 palabras por evento)
- ✅ Fecha y hora en BCD y código de evento por entrada
- ✅ Exportación a CSV (UTF-8)
- ✅ Envío por email
- ✅ Subida a servidor FTP (cliente FTP propio)

### 5. Interfaz de Usuario
- ✅ Material Design con tema claro y oscuro
- ✅ Menú lateral que muestra solo las máquinas activas
- ✅ Fuente personalizada (Inter Tight)
- ✅ Sinóptico de E/S con LEDs por canal
- ✅ Selector de idioma en la pantalla de Soporte (ES/EN/FR o idioma del sistema)

---

## Requisitos del Sistema

### Aplicación Android
- **Android**: 7.0 (API 24) o superior. Target SDK 34 (Android 14).
- **Hardware**: USB Host (OTG) para el modo ESP32 y para el adaptador USB-Ethernet
- **Permisos**:
  - `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE` (comunicación con el PLC)
  - Acceso USB (lo concede el usuario al conectar el dispositivo)
  - `READ_EXTERNAL_STORAGE` (Android ≤ 12) y `WRITE_EXTERNAL_STORAGE` (Android ≤ 9) para los CSV

### PLC OMRON
- **Modelos**: CP2E-N14DR-A, CP2E-N20DR-A, CP2E-N30DR-A, CP2E-N40DR-A, CP2E-N60DR-A
- **Comunicación**: puerto Ethernet integrado con FINS/TCP habilitado (puerto 9600)
- **IP por defecto en la app**: 192.168.250.1
- **Programa ladder**: debe respetar el mapa de memoria de [MEMORY_MAP.md](MEMORY_MAP.md)

### ESP32
- Firmware de plataforma compatible que responda a los comandos serie de la app (115200 baudios)

### Desarrollo
- **Android Studio**: Hedgehog (2023.1.1) o superior
- **Android Gradle Plugin**: 8.5.0
- **Kotlin**: 1.9.23 (KSP 1.9.23-1.0.19)
- **JDK**: 17

---

## Arquitectura del Proyecto

### Patrón MVVM

```
┌─────────────────────────────────────────────────┐
│                   View (UI)                      │
│  Fragments · ViewBinding · Material Components   │
└──────────────────┬──────────────────────────────┘
                   │ observa (LiveData / StateFlow)
                   ↓
┌─────────────────────────────────────────────────┐
│                 ViewModels                       │
│  CommunicationViewModel · SharedViewModel        │
│  Shelter/Door/Platform/TrafficLightViewModel …   │
└──────────────────┬──────────────────────────────┘
                   │ usa
                   ↓
┌─────────────────────────────────────────────────┐
│          Comunicación (IFinsClient)              │
│  ConnectionManager                               │
│   ├── FinsTcpClient     → PLC OMRON (Ethernet)   │
│   └── ESP32SerialClient → ESP32 (USB serie)      │
└─────────────────────────────────────────────────┘
```

### Flujo de Comunicación

```
Fragment → ViewModel → CommunicationViewModel → ConnectionManager → IFinsClient
                                                                  ├─ FinsTcpClient (PLC)
                                                                  └─ ESP32SerialClient (ESP32)
LogFragment → LogViewModel → LogManager → CsvGenerator → Email / FTP
```

Ver [ARCHITECTURE.md](ARCHITECTURE.md) para el detalle.

---

## Estructura de Directorios

```
INK-Dock-Assistant/
├── app/
│   ├── build.gradle.kts                       # Configuración del módulo (versión, dependencias)
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/ink_omron_viewer/
│       │   │   ├── MainActivity.kt
│       │   │   ├── domain/
│       │   │   │   ├── ConnectionManager.kt          # Crea y gestiona el cliente activo
│       │   │   │   ├── ConnectionMode.kt             # ETHERNET / USB_ESP32
│       │   │   │   ├── IFinsClient.kt                # Interfaz común de lectura/escritura
│       │   │   │   ├── FinsTcpClient.kt              # Cliente FINS/TCP (PLC)
│       │   │   │   ├── FinsTcpProtocol.kt
│       │   │   │   ├── FinsUdpClient.kt              # Cliente FINS/UDP (prueba de conexión)
│       │   │   │   ├── FinsProtocol.kt
│       │   │   │   ├── FinsMemoryArea.kt
│       │   │   │   ├── FinsAddressParser.kt          # "D100", "H120.03"… → área + dirección
│       │   │   │   ├── ESP32SerialClient.kt          # Cliente USB serie (ESP32)
│       │   │   │   ├── UsbSerialManager.kt
│       │   │   │   ├── UsbCommandManager.kt
│       │   │   │   ├── PlcModel.kt                   # Modelos CP2E y ESP32 con sus E/S
│       │   │   │   ├── ConfigurationViewModel.kt
│       │   │   │   ├── DockConfigurationViewModel.kt
│       │   │   │   ├── ActiveMachinesViewModel.kt
│       │   │   │   ├── ShelterViewModel.kt
│       │   │   │   ├── DoorViewModel.kt
│       │   │   │   ├── PlatformViewModel.kt
│       │   │   │   ├── TrafficLightViewModel.kt
│       │   │   │   ├── SynopticViewModel.kt
│       │   │   │   ├── LogViewModel.kt
│       │   │   │   └── log/
│       │   │   │       ├── LogManager.kt             # Lectura del buffer de eventos
│       │   │   │       ├── LogModels.kt
│       │   │   │       ├── CsvGenerator.kt
│       │   │   │       ├── EmailExporter.kt
│       │   │   │       └── FtpExporter.kt
│       │   │   ├── ui/
│       │   │   │   ├── CommunicationViewModel.kt     # ViewModel principal de comunicación
│       │   │   │   ├── SharedViewModel.kt
│       │   │   │   ├── home/            (HomeFragment, HomeViewModel)
│       │   │   │   ├── configuration/   (ConfigurationFragment, UsbDeviceSelectionDialog)
│       │   │   │   ├── dock/            (DockConfigurationFragment, DoorConfigurationFragment)
│       │   │   │   ├── machines/        (Shelter/Door/Platform/TrafficLightConfigurationFragment)
│       │   │   │   ├── synoptic/        (SynopticFragment, SynopticViewModel)
│       │   │   │   ├── log/             (LogFragment, LogEntriesAdapter)
│       │   │   │   ├── support/         (SupportFragment, SupportViewModel)
│       │   │   │   └── usb/             (UsbDeviceAdapter)
│       │   │   └── util/
│       │   │       └── LanguageManager.kt            # Cambio de idioma ES/EN/FR
│       │   └── res/
│       │       ├── layout/  menu/  navigation/  drawable/  font/  mipmap-*/
│       │       ├── values/ values-es/ values-en/ values-fr/   # Textos por idioma
│       │       ├── values-night/                              # Tema oscuro
│       │       └── xml/ (device_filter.xml, file_provider_paths.xml, …)
│       ├── test/            # Tests unitarios
│       └── androidTest/     # Tests instrumentados
├── gradle/ (libs.versions.toml, wrapper/)
├── build.gradle.kts
├── settings.gradle.kts
├── LICENSE                  # GNU GPL v3
└── *.md                     # Documentación (ver abajo)
```

---

## Documentación

Todos los documentos están en la raíz del repositorio.

### Para Usuarios

| Documento | Contenido |
|-----------|-----------|
| **[USER_MANUAL.md](USER_MANUAL.md)** | Instalación, conexión, pantallas, solución de problemas |

### Para Técnicos de PLC

| Documento | Contenido |
|-----------|-----------|
| **[MEMORY_MAP.md](MEMORY_MAP.md)** | **Referencia vigente**: direcciones de memoria que usa la app v1.9.4 |
| [LOG_CONFIGURATION.md](LOG_CONFIGURATION.md) | Histórico (v1.5): configuración del LOG en versiones anteriores |
| [PLC_LADDER_EXAMPLES.md](PLC_LADDER_EXAMPLES.md) | Histórico (v1.5): ejemplos ladder del sistema de LOG anterior |
| [RESUMEN_LOG_SYSTEM.md](RESUMEN_LOG_SYSTEM.md) | Histórico (v1.5): resumen del sistema de LOG anterior |

### Para Desarrolladores

| Documento | Contenido |
|-----------|-----------|
| **[ARCHITECTURE.md](ARCHITECTURE.md)** | Arquitectura MVVM, capas, componentes, flujo de datos |
| **[DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)** | Entorno, estructura, convenciones, cómo añadir funciones |
| **[FINS_PROTOCOL.md](FINS_PROTOCOL.md)** | Especificación técnica del protocolo FINS |

### Notas de versión

| Documento | Versión |
|-----------|---------|
| **[RELEASE_NOTES_v1.9.4.md](RELEASE_NOTES_v1.9.4.md)** | 1.9.4 (actual) |
| [RELEASE_NOTES_v1.6.2.md](RELEASE_NOTES_v1.6.2.md) | 1.6.2 |

---

## Instalación y Configuración

### Instalación de la APK

1. **Descargar la APK** de la versión actual (se distribuye aparte; los binarios no se guardan en el repositorio)
2. **Permitir la instalación de orígenes desconocidos** si el sistema lo pide
3. **Instalar** la aplicación
4. **Conceder permisos** cuando se soliciten

### Conexión a un PLC (Ethernet)

1. Conectar el adaptador USB-Ethernet al móvil o tablet y el cable al puerto Ethernet del PLC
2. Abrir **Configuración** en el menú lateral
3. Seleccionar el modelo de PLC (CP2E-N…)
4. Introducir la IP del PLC (por defecto 192.168.250.1; puerto 9600)
5. Probar la conexión y guardar
6. Volver a **Inicio** y pulsar **Conectar**

### Conexión a un ESP32 (USB)

1. Conectar la placa ESP32 por USB (cable OTG)
2. Aceptar el permiso USB y, si se pregunta, abrir la app
3. En **Configuración**, seleccionar el modelo ESP32 (P1, P2, P3 o P4) y el dispositivo USB
4. Conectar desde **Inicio**

Ver [USER_MANUAL.md](USER_MANUAL.md) para instrucciones detalladas.

---

## Uso de la Aplicación

| Pantalla | Función |
|----------|---------|
| **Inicio** | Conectar/desconectar, estado de la conexión |
| **Sinóptico** | E/S en tiempo real y lectura/escritura de direcciones de memoria |
| **Configuración del muelle** | Máquinas activas, número de pedido y posición, número de muelle |
| **Máquinas activas** | Abrigo, Puerta, Plataforma y Semáforos (solo aparecen las activas) |
| **Registro de actividad** | Leer el LOG del PLC, ver eventos, exportar CSV, email o FTP |
| **Configuración** | Modo de conexión, modelo, IP, dispositivo USB, prueba de conexión |
| **Soporte** | Contacto, versión de la aplicación e idioma |

---

## Desarrollo

### Clonar el Repositorio

```bash
git clone https://github.com/Xarosu/INK-Dock-Assistant.git
cd INK-Dock-Assistant
```

### Abrir en Android Studio

1. File → Open → seleccionar la carpeta `INK-Dock-Assistant`
2. Esperar a la sincronización de Gradle
3. Configurar dispositivo o emulador (el modo USB requiere dispositivo físico)

### Build y Run

```bash
./gradlew assembleDebug     # Build debug
./gradlew assembleRelease   # Build release
./gradlew test              # Tests unitarios
./gradlew installDebug      # Instalar en dispositivo
```

> Identificadores internos: el paquete y el `applicationId` siguen siendo `com.example.ink_omron_viewer` y el proyecto Gradle se llama `ink_omron_viewer`. Cambiar el `applicationId` haría que Android lo tratase como otra app (no se actualizaría encima de las versiones ya instaladas).

### Dependencias Principales

Definidas en `gradle/libs.versions.toml` y `app/build.gradle.kts`:

| Dependencia | Versión | Uso |
|-------------|---------|-----|
| androidx.core:core-ktx | 1.13.1 | Base AndroidX |
| androidx.appcompat | 1.7.0 | Compatibilidad |
| com.google.android.material | 1.12.0 | Material Design |
| androidx.constraintlayout | 2.2.0 | Layouts |
| androidx.lifecycle (livedata, viewmodel) | 2.8.3 | MVVM |
| androidx.navigation (fragment, ui) | 2.7.7 | Navegación |
| androidx.activity-ktx / fragment-ktx | 1.9.1 / 1.6.2 | ViewModels por delegado |
| com.github.mik3y:usb-serial-for-android | 3.7.0 | Comunicación USB serie con ESP32 |
| androidx.room | 2.6.1 | Declarada, sin uso actual en el código |
| androidx.work | 2.9.0 | Declarada, sin uso actual en el código |
| commons-net | 3.9.0 | Declarada, sin uso actual (el FTP es implementación propia) |

**Nota**: FINS y FTP están implementados en Kotlin sobre sockets, sin librerías externas.

Ver [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md) para convenciones y cómo añadir pantallas o máquinas.

---

## Notas de Versión

### Versión 1.9.4 (actual)
- ✅ Modo de conexión **USB ESP32** (modelos P1, P2, P3, P4)
- ✅ PLC OMRON **CP2E-N** por Ethernet con adaptador USB-Ethernet
- ✅ Buffer de LOG ampliado a 300 eventos
- ✅ Idiomas español, inglés y francés
- ✅ Pantallas de máquina, sinóptico y soporte rehechos

Detalle en [RELEASE_NOTES_v1.9.4.md](RELEASE_NOTES_v1.9.4.md).

### Versión 1.6.2
- Sistema de LOG circular, menú dinámico de máquinas activas, soporte CP2E. Ver [RELEASE_NOTES_v1.6.2.md](RELEASE_NOTES_v1.6.2.md).

### Versiones 1.4 – 1.5
- Implementación inicial de FINS, pantallas de máquinas, primer sistema de LOG y exportación CSV, email y FTP.

---

## Solución de Problemas

### No conecta al PLC
1. **Conexión física**: adaptador USB-Ethernet conectado y cable al puerto del PLC, o móvil y PLC en la misma red Wi-Fi
2. **PLC**: FINS/TCP habilitado, puerto 9600
3. **App**: IP y modelo correctos en Configuración

### No detecta el ESP32
1. Comprobar que el cable USB transmite datos (no solo carga) y que hay adaptador OTG
2. Aceptar el permiso USB cuando aparezca
3. Chips soportados: CP2102/CP2104, CH340, FTDI

### Error FINS 4363
- Lectura demasiado grande o área de memoria inexistente. Ver [MEMORY_MAP.md](MEMORY_MAP.md).

### La app se cierra
1. Verificar permisos concedidos
2. Revisar Logcat
3. Contactar con soporte adjuntando los logs

---

## Soporte y Contacto

**X. Rodriguez**
- Email: xarosu@gmail.com
- Repositorio: https://github.com/Xarosu/INK-Dock-Assistant

Para reportar errores, incluir: versión de la app, versión de Android, modelo de PLC o ESP32, logs y pasos para reproducir. También se pueden abrir *issues* en GitHub.

---

## Licencia

Copyright (C) 2025-2026 X. Rodriguez

Este programa es software libre: puedes redistribuirlo y/o modificarlo bajo los
términos de la **GNU General Public License** publicada por la Free Software
Foundation, ya sea la **versión 3** de la Licencia o (a tu elección) cualquier
versión posterior.

Este programa se distribuye con la esperanza de que sea útil, pero **SIN NINGUNA
GARANTÍA**; ni siquiera la garantía implícita de COMERCIABILIDAD o IDONEIDAD
PARA UN PROPÓSITO PARTICULAR. Consulta la GNU General Public License para más
detalles.

El texto completo de la licencia está en el archivo [LICENSE](LICENSE) y en
<https://www.gnu.org/licenses/gpl-3.0.html>.

---

## Créditos

**Desarrollo**: X. Rodriguez
**Tecnología**: Android, Kotlin, OMRON FINS, ESP32
**Años**: 2025-2026

---

**Última actualización**: Noviembre 2026 · App v1.9.4
