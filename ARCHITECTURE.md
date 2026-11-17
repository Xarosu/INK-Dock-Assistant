# Arquitectura del Proyecto - INK-Dock Assistant

Este documento describe la arquitectura técnica detallada del proyecto INK-Dock Assistant.

---

## Índice

1. [Visión General](#visión-general)
2. [Patrón Arquitectónico](#patrón-arquitectónico)
3. [Capas de la Aplicación](#capas-de-la-aplicación)
4. [Componentes Principales](#componentes-principales)
5. [Flujo de Datos](#flujo-de-datos)
6. [Comunicación FINS](#comunicación-fins)
7. [Sistema de LOG](#sistema-de-log)
8. [Gestión de Estado](#gestión-de-estado)
9. [Decisiones Arquitectónicas](#decisiones-arquitectónicas)

---

## Visión General

INK-Dock Assistant está construido siguiendo el patrón **MVVM (Model-View-ViewModel)** recomendado por Google para aplicaciones Android, con una arquitectura limpia que separa responsabilidades en capas bien definidas.

### Principios Fundamentales

1. **Separación de Responsabilidades**: Cada componente tiene una responsabilidad única y bien definida
2. **Unidirectional Data Flow**: Los datos fluyen en una sola dirección
3. **Single Source of Truth**: Cada dato tiene una única fuente de verdad
4. **Testability**: Código fácilmente testeable gracias a la separación de capas
5. **Maintainability**: Código mantenible y extensible

---

## Patrón Arquitectónico

### MVVM (Model-View-ViewModel)

```
┌─────────────────────────────────────────────────────────────┐
│                        PRESENTATION LAYER                    │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                      View (UI)                        │   │
│  │                                                       │   │
│  │  - Fragments                                         │   │
│  │  - Activities                                        │   │
│  │  - ViewBinding                                       │   │
│  │  - Adapters                                          │   │
│  │  - XML Layouts                                       │   │
│  │                                                       │   │
│  │  Responsabilidad: Renderizar UI, capturar eventos   │   │
│  └──────────────┬───────────────────────────────────────┘   │
│                 │                                            │
│                 │ Observa (LiveData/StateFlow)              │
│                 ↓                                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                    ViewModel                          │   │
│  │                                                       │   │
│  │  - Mantiene estado de UI                            │   │
│  │  - Procesa eventos de usuario                       │   │
│  │  - Expone LiveData para observación                 │   │
│  │  - Maneja lógica de presentación                    │   │
│  │  - Sobrevive a cambios de configuración             │   │
│  │                                                       │   │
│  │  Responsabilidad: Preparar datos para la UI         │   │
│  └──────────────┬───────────────────────────────────────┘   │
└─────────────────┼────────────────────────────────────────────┘
                  │
                  │ Llama métodos
                  ↓
┌─────────────────────────────────────────────────────────────┐
│                        DOMAIN LAYER                          │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Use Cases / Managers                     │   │
│  │                                                       │   │
│  │  - LogManager                                        │   │
│  │  - CsvGenerator                                      │   │
│  │  - EmailExporter                                     │   │
│  │  - FtpExporter                                       │   │
│  │                                                       │   │
│  │  Responsabilidad: Lógica de negocio                  │   │
│  └──────────────┬───────────────────────────────────────┘   │
└─────────────────┼────────────────────────────────────────────┘
                  │
                  │ Usa
                  ↓
┌─────────────────────────────────────────────────────────────┐
│                        DATA LAYER                            │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                Data Sources                           │   │
│  │                                                       │   │
│  │  - FinsTcpClient (Network)                           │   │
│  │  - FinsProtocol (Protocol)                           │   │
│  │  - SharedPreferences (Local Storage)                 │   │
│  │                                                       │   │
│  │  Responsabilidad: Acceso a datos                     │   │
│  └──────────────┬───────────────────────────────────────┘   │
└─────────────────┼────────────────────────────────────────────┘
                  │
                  │ Conecta a
                  ↓
┌─────────────────────────────────────────────────────────────┐
│                      EXTERNAL SYSTEMS                        │
│                                                              │
│  - PLC OMRON (FINS TCP/IP)                                  │
│  - Email Client (Intent)                                     │
│  - FTP Server (Socket)                                       │
│  - File System                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Capas de la Aplicación

### 1. Presentation Layer (UI)

**Ubicación**: `com.example.ink_omron_viewer.ui/`

**Responsabilidades**:
- Renderizar la interfaz de usuario
- Capturar eventos del usuario
- Observar cambios en ViewModels
- Actualizar UI en respuesta a datos

**Componentes**:
- **Fragments**: Unidades de UI reutilizables
- **ViewBinding**: Acceso type-safe a las vistas
- **Adapters**: Para RecyclerViews
- **Custom Views**: Componentes personalizados

**Ejemplo**:
```kotlin
class HomeFragment : Fragment() {
    // ViewBinding
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // ViewModel
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(...): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        // Observar datos
        viewModel.connectionState.observe(viewLifecycleOwner) { state ->
            updateConnectionUI(state)
        }

        // Configurar listeners
        binding.buttonConnect.setOnClickListener {
            viewModel.connect()
        }

        return binding.root
    }
}
```

### 2. ViewModel Layer

**Ubicación**: `com.example.ink_omron_viewer.ui/*/ViewModels`

**Responsabilidades**:
- Mantener estado de UI
- Procesar eventos de usuario
- Llamar a la capa de dominio
- Exponer datos mediante LiveData
- Sobrevivir a cambios de configuración

**Características**:
- Usa `viewModelScope` para coroutines
- No contiene referencias a Views/Context
- Expone LiveData (inmutable) para observación
- Usa MutableLiveData (privado) internamente

**Ejemplo**:
```kotlin
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    // Estado privado
    private val _connectionState = MutableLiveData<ConnectionState>()
    // Exposición pública inmutable
    val connectionState: LiveData<ConnectionState> = _connectionState

    private val client = FinsTcpClient()

    fun connect() {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Connecting
            try {
                client.connect(ip, port)
                _connectionState.value = ConnectionState.Connected
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message)
            }
        }
    }
}
```

### 3. Domain Layer

**Ubicación**: `com.example.ink_omron_viewer.domain/`

**Responsabilidades**:
- Implementar lógica de negocio
- Casos de uso de la aplicación
- Modelos de datos
- Managers y coordinadores

**Características**:
- Independiente de Android Framework
- Puro Kotlin
- Testeable sin dependencias Android
- Reutilizable

**Sub-componentes**:

#### a) Comunicación (`domain/`)
- `IFinsClient`: interfaz común `readWords` / `writeWords` / `disconnect`
- `ConnectionManager`: crea el cliente según el modo y mantiene el estado (`ConnectionState`)
- `ConnectionMode`: `ETHERNET` (PLC) o `USB_ESP32`
- `FinsTcpClient`: cliente FINS/TCP con handshake de nodo (PLC CP2E-N, puerto 9600)
- `FinsUdpClient`: cliente FINS/UDP, usado en la prueba de conexión de la pantalla Configuración
- `FinsProtocol`, `FinsTcpProtocol`, `FinsMemoryArea`, `FinsAddressParser`: construcción de tramas, códigos de área y parseo de direcciones ("D100", "H120.03"…)
- `ESP32SerialClient`: implementa `IFinsClient` sobre USB serie (115200 baudios, tramas tipo Host Link con FCS)
- `UsbSerialManager`, `UsbCommandManager`: detección de dispositivos USB y permisos

#### b) Modelos y ViewModels de máquina (`domain/`)
- `PlcModel` / `PlcRepository`: modelos CP2E-N y ESP32 con sus canales de E/S
- `ShelterViewModel`, `DoorViewModel`, `PlatformViewModel`, `TrafficLightViewModel`: temporizadores, contadores, switches, resets y números de serie
- `DockConfigurationViewModel`, `ActiveMachinesViewModel`, `ConfigurationViewModel`, `SynopticViewModel`, `LogViewModel`

#### c) LOG System (`domain/log/`)
- `LogManager`: lectura del buffer de 300 eventos (bloques de 50 palabras)
- `LogModels`: modelos de datos
- `CsvGenerator`: generación CSV
- `EmailExporter`: exportación por email (Intent + FileProvider)
- `FtpExporter`: cliente FTP propio sobre sockets

> Nota: los ViewModels de máquina están en el paquete `domain/` y no en `ui/`; es la organización actual del código.

### 4. Data Layer

**Responsabilidades**:
- Acceso a fuentes de datos
- Implementación de protocolos
- Persistencia local
- Comunicación de red

**Fuentes de Datos**:

#### a) Network (PLC) y USB (ESP32)
```kotlin
interface IFinsClient {
    suspend fun readWords(memoryArea: Byte, address: Int, count: Int, network: Network? = null): FinsResponse
    suspend fun writeWords(memoryArea: Byte, address: Int, data: ShortArray, network: Network? = null): FinsResponse
    fun disconnect()
}

class FinsTcpClient(ip: String, port: Int = 9600, clientNode: Int = 0) : IFinsClient
class ESP32SerialClient(context: Context, serialPort: UsbSerialPort) : IFinsClient
```

#### b) Local Storage
```kotlin
// SharedPreferences para configuración
val prefs = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
prefs.edit().putString("plc_ip", "192.168.250.1").apply()
```

#### c) File System
```kotlin
// Para archivos CSV
val logsDir = File(context.getExternalFilesDir(null), "logs")
val csvFile = File(logsDir, "log_muelle_1.csv")
```

---

## Componentes Principales

### CommunicationViewModel

**Rol**: ViewModel central para comunicación con PLC

```kotlin
class CommunicationViewModel(application: Application) : AndroidViewModel(application) {
    // Gestiona el cliente activo (FinsTcpClient o ESP32SerialClient)
    private val connectionManager = ConnectionManager(application)

    // Conectar según el tipo (Ethernet o USB)
    fun connect(connectionType: ConnectionType, serialPort: UsbSerialPort? = null)
    fun disconnect()

    // Lectura / escritura de palabras y bits
    suspend fun readWords(memoryArea: Int, address: Int, count: Int): Result<ShortArray>
    suspend fun writeWords(memoryArea: Int, address: Int, data: ShortArray): Result<Unit>
    suspend fun setBit(memoryArea: Int, address: Int, bitNumber: Int, value: Boolean): Result<Unit>

    // Conversión BCD (números de serie, pedido, muelle)
    fun decimalToBcd(decimal: Long, wordCount: Int): ShortArray
    fun bcdToDecimal(bcdWords: ShortArray): Long

    fun isConnected(): Boolean
}
```

Además registra un `NetworkCallback` para usar la red Ethernet del adaptador USB cuando está disponible.

### LogViewModel

**Rol**: ViewModel para sistema de LOG

```kotlin
class LogViewModel(application: Application) : AndroidViewModel(application) {
    private val csvGenerator = CsvGenerator(getApplication())
    private val emailExporter = EmailExporter(getApplication())
    private val ftpExporter = FtpExporter()

    // Estado
    private val _systemLog = MutableLiveData<SystemLog>()
    val systemLog: LiveData<SystemLog> = _systemLog

    private val _generatedFile = MutableLiveData<File>()
    val generatedFile: LiveData<File> = _generatedFile

    // Cargar LOG desde PLC
    fun loadSystemLog(commsViewModel: CommunicationViewModel)

    // Generar CSV
    fun generateCsvFile(exportConfig: ExportConfig = ExportConfig())

    // Exportar por email
    fun exportByEmail(emailConfig: EmailConfig)

    // Exportar por FTP
    fun exportByFtp(ftpConfig: FtpConfig)

    // Obtener estadísticas
    fun getStatistics(): Map<String, Int>
}
```

### SharedViewModel

**Rol**: Compartir estado entre fragments

```kotlin
class SharedViewModel : ViewModel() {
    // Estado compartido entre múltiples fragments
    private val _selectedMachine = MutableLiveData<MachineType>()
    val selectedMachine: LiveData<MachineType> = _selectedMachine

    fun selectMachine(type: MachineType) {
        _selectedMachine.value = type
    }
}
```

---

## Flujo de Datos

### Lectura de Datos del PLC

```
┌──────────────┐
│   Fragment   │
└──────┬───────┘
       │ 1. Click button "Leer"
       ↓
┌──────────────────┐
│   ViewModel      │
│                  │
│  fun readData()  │
└──────┬───────────┘
       │ 2. viewModelScope.launch
       ↓
┌────────────────────────┐
│ CommunicationViewModel │
│                        │
│ suspend fun readWord() │
└──────┬─────────────────┘
       │ 3. Llamada suspendida
       ↓
┌──────────────────┐
│  FinsTcpClient   │
│                  │
│  sendCommand()   │
└──────┬───────────┘
       │ 4. Socket TCP/IP
       ↓
┌──────────────────┐
│    PLC OMRON     │
│  (FINS Server)   │
└──────┬───────────┘
       │ 5. Respuesta
       ↓
┌──────────────────┐
│  FinsTcpClient   │
│                  │
│  parseResponse() │
└──────┬───────────┘
       │ 6. Result<Int>
       ↓
┌────────────────────────┐
│ CommunicationViewModel │
│                        │
│ Result.onSuccess {}    │
└──────┬─────────────────┘
       │ 7. postValue()
       ↓
┌──────────────────┐
│   ViewModel      │
│                  │
│  _data.value =   │
└──────┬───────────┘
       │ 8. LiveData emite
       ↓
┌──────────────┐
│   Fragment   │
│              │
│  observe {}  │
└──────┬───────┘
       │ 9. Actualizar UI
       ↓
┌──────────────┐
│   View       │
│  (TextView)  │
└──────────────┘
```

### Escritura de Datos al PLC

```
User Input → Fragment → ViewModel → CommViewModel → FinsTcpClient → PLC
                                                                      │
Fragment ← ViewModel ← CommViewModel ← Result ← Response ← ─────────┘
   │
   └→ Update UI (feedback)
```

### Generación y Exportación de LOG

```
1. User: "Cargar LOG"
   └→ LogFragment.buttonLoadLog.onClick()
      └→ LogViewModel.loadSystemLog()
         └→ LogManager.generateSystemLog()
            └→ CommunicationViewModel.readWords() × N
               └→ Parse datos → SystemLog
                  └→ _systemLog.postValue()

2. User: "Generar CSV"
   └→ LogFragment.buttonGenerateCsv.onClick()
      └→ LogViewModel.generateCsvFile()
         └→ CsvGenerator.generateCsv()
            └→ File.write()
               └→ _generatedFile.postValue()

3. User: "Enviar Email"
   └→ LogFragment.buttonExportEmail.onClick()
      └→ LogViewModel.exportByEmail()
         └→ EmailExporter.sendByEmail()
            └→ Intent(ACTION_SEND)
               └→ startActivity()
```

---

## Comunicación FINS

### Arquitectura del Cliente FINS

```
┌───────────────────────────────┐
│     CommunicationViewModel    │
└───────────────┬───────────────┘
                ↓
┌───────────────────────────────┐
│       ConnectionManager       │  modo: ETHERNET | USB_ESP32
└───────────────┬───────────────┘
                ↓ IFinsClient
      ┌─────────┴──────────┐
      ↓                    ↓
┌──────────────┐   ┌──────────────────┐
│FinsTcpClient │   │ ESP32SerialClient│
│ socket TCP   │   │ usb-serial       │
│ handshake    │   │ 115200 baudios   │
│ nodo FINS    │   │ trama Host Link  │
└──────┬───────┘   └────────┬─────────┘
       ↓                    ↓
  FinsProtocol /       Placa ESP32
  FinsTcpProtocol      (NVS)
       ↓
  PLC OMRON CP2E-N
```

La prueba de conexión de la pantalla Configuración usa `FinsUdpClient` (FINS/UDP).

### Formato de Frame FINS

```
┌──────────────────────────────────────────────────────────┐
│              FINS TCP/IP Frame                           │
├──────────────────────────────────────────────────────────┤
│                                                           │
│  Header (16 bytes):                                      │
│    - Magic: "FINS" (4 bytes)                            │
│    - Length: Frame length (4 bytes)                     │
│    - Command: 0x00000002 (4 bytes)                      │
│    - Error Code: 0x00000000 (4 bytes)                   │
│                                                           │
│  FINS Header (10 bytes):                                 │
│    - ICF: Information Control Field (1 byte)            │
│    - RSV: Reserved (1 byte)                             │
│    - GCT: Gateway Count (1 byte)                        │
│    - DNA: Destination Network Address (1 byte)          │
│    - DA1: Destination Node (1 byte)                     │
│    - DA2: Destination Unit (1 byte)                     │
│    - SNA: Source Network Address (1 byte)               │
│    - SA1: Source Node (1 byte)                          │
│    - SA2: Source Unit (1 byte)                          │
│    - SID: Service ID (1 byte)                           │
│                                                           │
│  Command Code (2 bytes):                                 │
│    - Main: 0x01 (Memory Area)                           │
│    - Sub: 0x01 (Read) / 0x02 (Write)                    │
│                                                           │
│  Parameters (variable):                                  │
│    - Memory Area Code (1 byte)                          │
│    - Address (2 bytes, big-endian)                      │
│    - Bit (1 byte)                                        │
│    - Count (2 bytes, big-endian)                        │
│    - Data (if write)                                     │
│                                                           │
└──────────────────────────────────────────────────────────┘
```

---

## Sistema de LOG

### Arquitectura del Sistema de LOG

```
┌──────────────────────────────────────────────────────────┐
│                      LogViewModel                         │
└──────────────────┬───────────────────────────────────────┘
                   │
        ┌──────────┼──────────┬──────────┬──────────┐
        │          │          │          │          │
        ↓          ↓          ↓          ↓          ↓
┌──────────┐ ┌──────────┐ ┌────────┐ ┌───────┐ ┌───────┐
│LogManager│ │  Csv     │ │ Email  │ │  FTP  │ │Filter │
│          │ │Generator │ │Exporter│ │Export.│ │       │
└────┬─────┘ └────┬─────┘ └───┬────┘ └───┬───┘ └───┬───┘
     │            │            │          │         │
     │            │            │          │         │
     ↓            ↓            ↓          ↓         ↓
   PLC         File        Intent     Socket   LiveData
               System      (Email)    (FTP)
```

### Modelos de Datos

```kotlin
// Configuración del PLC (se toma de la app: modelo, IP, pedido, muelle)
data class PlcConfiguration(
    val model: String,
    val ipAddress: String,
    val orderNumber: String,   // "1120005678/20"
    val dockNumber: Int
)

// Configuración de cada máquina
data class MachineConfiguration(
    val type: String,
    val isActive: Boolean,
    val serialNumber: String,
    val timerValue: Short,
    val counterValues: List<Short>,
    val switchStates: List<Boolean>
)

data class DockConfiguration(val machines: List<MachineConfiguration>)

// Entrada de log (5 palabras en el PLC: YYMM, DDHH, MMSS en BCD, reserva, código)
data class LogEntry(
    val timestamp: String,     // "dd/MM/yy HH:mm:ss"
    val eventCode: Int,        // código decimal (ver MEMORY_MAP.md)
    val description: String
)

// Log completo
data class SystemLog(
    val generatedAt: Long,
    val plcConfig: PlcConfiguration,
    val dockConfig: DockConfiguration,
    val logEntries: List<LogEntry>   // hasta 300
)
```

Direcciones de memoria: ver [MEMORY_MAP.md](MEMORY_MAP.md).

---

## Gestión de Estado

### Estados de Conexión

```kotlin
enum class ConnectionMode { ETHERNET, USB_ESP32 }

data class ConnectionState(
    val mode: ConnectionMode = ConnectionMode.ETHERNET,
    val isConnected: Boolean = false,
    val status: String = "Desconectado",
    val deviceInfo: String? = null
) {
    fun supportsSheltersAndDoors(): Boolean = mode == ConnectionMode.ETHERNET
}
```

### Estados de UI

```kotlin
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
    object Empty : UiState<Nothing>()
}
```

### Uso en ViewModel

```kotlin
class MyViewModel : ViewModel() {
    private val _uiState = MutableLiveData<UiState<List<Item>>>()
    val uiState: LiveData<UiState<List<Item>>> = _uiState

    fun loadData() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val data = repository.getData()
                _uiState.value = if (data.isEmpty()) {
                    UiState.Empty
                } else {
                    UiState.Success(data)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Error")
            }
        }
    }
}
```

---

## Decisiones Arquitectónicas

### 1. ¿Por qué MVVM?

**Ventajas**:
- ✅ Recomendado por Google
- ✅ Separación clara UI/lógica
- ✅ ViewModel sobrevive a cambios de configuración
- ✅ Testeable (UI y lógica independientes)
- ✅ LiveData maneja ciclo de vida automáticamente

**Alternativas consideradas**:
- ❌ MVP: Más acoplamiento, referencias a View
- ❌ MVI: Demasiado complejo para este proyecto

### 2. ¿Por qué No Usar Retrofit/OkHttp?

**Razón**:
- FINS es un protocolo propietario binario
- No es REST/HTTP
- Socket TCP directo es más apropiado
- Implementación nativa da más control

### 3. ¿Por qué No Usar Room/SQLite?

**Razón**:
- Los datos se almacenan en el PLC (fuente de verdad)
- No hay necesidad de caché local persistente
- SharedPreferences suficiente para configuración

> Room y WorkManager están declarados en `build.gradle.kts` (v1.9.4) pero el código no los usa todavía.

### 4. ¿Por qué No Usar Hilt/Dagger?

**Razón**:
- Proyecto relativamente pequeño
- DI manual suficiente y más simple
- Menos dependencias = menos complejidad
- Singleton pattern funciona bien

### 5. ¿Por qué LiveData en lugar de Flow?

**Razón**:
- ✅ LiveData maneja lifecycle automáticamente
- ✅ Más simple para UI reactive
- ✅ Menos boilerplate
- ⚠️ Flow se podría considerar para futuras versiones

---

## Diagrama de Clases Principales

```
┌──────────────────────────────────────────────────────────────┐
│                         MainActivity                          │
├──────────────────────────────────────────────────────────────┤
│ - drawer: DrawerLayout                                       │
│ - navController: NavController                               │
├──────────────────────────────────────────────────────────────┤
│ + onCreate()                                                  │
│ + onNavigationItemSelected()                                 │
└──────────────────────────────────────────────────────────────┘
                              │
                              │ contiene
                              ↓
┌──────────────────────────────────────────────────────────────┐
│                         Fragments                             │
├──────────────────────────────────────────────────────────────┤
│ - HomeFragment                                               │
│ - ConfigurationFragment                                      │
│ - SynopticFragment                                           │
│ - DockConfigurationFragment                                  │
│ - Machines (Shelter, Door, Platform, TrafficLight)          │
│ - LogFragment                                                │
│ - SupportFragment                                            │
└──────────────────┬───────────────────────────────────────────┘
                   │ usa
                   ↓
┌──────────────────────────────────────────────────────────────┐
│                      ViewModels                               │
├──────────────────────────────────────────────────────────────┤
│ - CommunicationViewModel (shared)                            │
│ - LogViewModel (shared)                                      │
│ - SharedViewModel (shared)                                   │
│ - HomeViewModel                                              │
│ - ConfigurationViewModel                                     │
│ - SynopticViewModel                                          │
│ - SupportViewModel                                           │
│ - Shelter/Door/Platform/TrafficLightViewModel                │
│ - DockConfigurationViewModel, ActiveMachinesViewModel        │
└──────────────────┬───────────────────────────────────────────┘
                   │ usa
                   ↓
┌──────────────────────────────────────────────────────────────┐
│                    Domain Layer                               │
├──────────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────────┐   │
│ │      ConnectionManager → IFinsClient                   │   │
│ ├────────────────────────────────────────────────────────┤   │
│ │ FinsTcpClient (PLC)  ·  ESP32SerialClient (USB)        │   │
│ │ + readWords(), writeWords(), disconnect()              │   │
│ └────────────────────────────────────────────────────────┘   │
│                                                              │
│ ┌────────────────────────────────────────────────────────┐   │
│ │                   LogManager                           │   │
│ ├────────────────────────────────────────────────────────┤   │
│ │ + readDockConfiguration()                              │   │
│ │ + readLogEntries()                                     │   │
│ │ + generateSystemLog()                                  │   │
│ └────────────────────────────────────────────────────────┘   │
│                                                              │
│ ┌────────────────────────────────────────────────────────┐   │
│ │             CsvGenerator, EmailExporter, FtpExporter   │   │
│ └────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

---

## Patrones de Diseño Utilizados

### 1. **MVVM Pattern**
- Separación View/ViewModel/Model

### 2. **Repository / Strategy de comunicación**
- `IFinsClient` abstrae la fuente de datos
- `ConnectionManager` elige `FinsTcpClient` (PLC) o `ESP32SerialClient` (ESP32)

### 3. **Observer Pattern**
- LiveData/Observer para reactive UI

### 4. **Builder Pattern**
- `FinsProtocol` / `FinsTcpProtocol` construyen las tramas FINS

### 5. **Singleton Pattern**
- Cliente FINS único (en algunos casos)

### 6. **Result Pattern**
- `Result<T>` para manejo de errores funcional

### 7. **Strategy Pattern**
- Diferentes exporters (Email, FTP, CSV)

---

**Última actualización**: Noviembre 2026 (app v1.9.4)
**Versión del documento**: 1.1