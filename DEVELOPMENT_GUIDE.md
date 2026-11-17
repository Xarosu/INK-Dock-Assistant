# Guía de Desarrollo - INK-Dock Assistant

Esta guía proporciona información detallada para desarrolladores que trabajen en el proyecto INK-Dock Assistant.

---

## Índice

1. [Configuración del Entorno](#configuración-del-entorno)
2. [Estructura del Proyecto](#estructura-del-proyecto)
3. [Convenciones de Código](#convenciones-de-código)
4. [Arquitectura MVVM](#arquitectura-mvvm)
5. [Agregar Nuevas Funcionalidades](#agregar-nuevas-funcionalidades)
6. [Sistema de Comunicación FINS](#sistema-de-comunicación-fins)
7. [Testing](#testing)
8. [Build y Despliegue](#build-y-despliegue)
9. [Solución de Problemas](#solución-de-problemas)

---

## Configuración del Entorno

### Requisitos

- **Android Studio**: Hedgehog (2023.1.1) o superior
- **JDK**: 17 (incluido con Android Studio)
- **Kotlin**: 1.9.23 (KSP 1.9.23-1.0.19)
- **Android Gradle Plugin**: 8.5.0
- **Git**: Para control de versiones

### Pasos de Configuración

1. **Clonar el repositorio**:
```bash
git clone https://github.com/Xarosu/INK-Dock-Assistant.git
cd INK-Dock-Assistant
```

2. **Abrir en Android Studio**:
   - File → Open → Seleccionar carpeta `INK-Dock-Assistant` (el proyecto Gradle se llama `ink_omron_viewer`)
   - Esperar a que Gradle sincronice

3. **Configurar SDK**:
   - Android Studio debería detectar automáticamente
   - Si no: File → Project Structure → SDK Location
   - Asegurar Android SDK 34+ instalado

4. **Sincronizar Gradle**:
```bash
./gradlew build
```

5. **Ejecutar la app**:
   - Conectar dispositivo o iniciar emulador (el modo USB ESP32 y el adaptador USB-Ethernet requieren dispositivo físico)
   - Run → Run 'app' (Shift+F10)

---

## Estructura del Proyecto

### Organización de Paquetes

```
com.example.ink_omron_viewer/
├── domain/                         # Comunicación, modelos y ViewModels de negocio
│   ├── IFinsClient.kt              # Interfaz común de lectura/escritura
│   ├── ConnectionManager.kt        # Crea el cliente según el modo
│   ├── ConnectionMode.kt           # ETHERNET / USB_ESP32
│   ├── FinsTcpClient.kt            # PLC por FINS/TCP
│   ├── FinsUdpClient.kt            # Prueba de conexión FINS/UDP
│   ├── FinsProtocol.kt, FinsTcpProtocol.kt, FinsMemoryArea.kt, FinsAddressParser.kt
│   ├── ESP32SerialClient.kt        # ESP32 por USB serie
│   ├── UsbSerialManager.kt, UsbCommandManager.kt
│   ├── PlcModel.kt                 # Modelos CP2E-N y ESP32
│   ├── *ViewModel.kt               # Shelter, Door, Platform, TrafficLight, DockConfiguration, …
│   └── log/                        # LogManager, LogModels, CsvGenerator, EmailExporter, FtpExporter
│
├── ui/                             # Interfaz de usuario
│   ├── home/  configuration/  dock/  machines/  synoptic/  log/  support/  usb/
│   ├── CommunicationViewModel.kt   # ViewModel principal de comunicación
│   └── SharedViewModel.kt
│
├── util/
│   └── LanguageManager.kt          # Idioma ES/EN/FR
│
└── MainActivity.kt                 # Actividad principal
```

### Principios de Organización

1. **Separación por capas**: UI → ViewModel → Domain → Data
2. **Módulos por funcionalidad**: Cada pantalla en su propio paquete
3. **Shared components**: ViewModels compartidos para estado global
4. **Domain puro**: Sin dependencias de Android en domain/

---

## Convenciones de Código

### Nombres de Archivos

- **Fragments**: `NombreFragment.kt` (ej: `HomeFragment.kt`)
- **ViewModels**: `NombreViewModel.kt` (ej: `HomeViewModel.kt`)
- **Layouts**: `fragment_nombre.xml` (ej: `fragment_home.xml`)
- **Adapters**: `NombreAdapter.kt` (ej: `LogEntriesAdapter.kt`)
- **Models**: `Nombre.kt` o `NombreModels.kt` si múltiples

### Estilo Kotlin

```kotlin
// ✅ CORRECTO: Nomenclatura clara y consistente
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ❌ INCORRECTO: Sin lateinit innecesarios, sin !!
class BadFragment : Fragment() {
    lateinit var binding: FragmentHomeBinding  // ❌

    override fun onCreateView(...): View {
        binding = FragmentHomeBinding.inflate(...)
        return binding!!.root  // ❌
    }
}
```

### ViewBinding

**Siempre usar ViewBinding**:

```kotlin
// ✅ CORRECTO
private var _binding: FragmentHomeBinding? = null
private val binding get() = _binding!!

// En onCreateView
_binding = FragmentHomeBinding.inflate(inflater, container, false)

// En onDestroyView
_binding = null
```

### LiveData y Observadores

```kotlin
// ✅ CORRECTO: Observar en viewLifecycleOwner
viewModel.data.observe(viewLifecycleOwner) { data ->
    updateUI(data)
}

// ❌ INCORRECTO: No usar 'this' en Fragments
viewModel.data.observe(this) { }  // ❌ Memory leak
```

### Coroutines

```kotlin
// ✅ CORRECTO: Usar viewModelScope
class HomeViewModel : ViewModel() {
    fun loadData() {
        viewModelScope.launch {
            val result = repository.fetchData()
            _data.postValue(result)
        }
    }
}

// ✅ CORRECTO: Usar lifecycleScope en Fragment
class HomeFragment : Fragment() {
    private fun doWork() {
        viewLifecycleOwner.lifecycleScope.launch {
            // trabajo asíncrono
        }
    }
}
```

### Manejo de Errores

```kotlin
// ✅ CORRECTO: Result pattern
suspend fun readFromPlc(): Result<ByteArray> {
    return try {
        val data = performRead()
        Result.success(data)
    } catch (e: Exception) {
        Log.e(TAG, "Error reading from PLC", e)
        Result.failure(e)
    }
}

// Uso
val result = readFromPlc()
result.onSuccess { data ->
    // procesar
}.onFailure { error ->
    // manejar error
}
```

---

## Arquitectura MVVM

### Flujo de Datos

```
┌──────────────┐
│   Fragment   │ ← Usuario interactúa
└──────┬───────┘
       │ observa LiveData
       ↓
┌──────────────┐
│  ViewModel   │ ← Mantiene estado UI
└──────┬───────┘
       │ llama
       ↓
┌──────────────┐
│   UseCase    │ ← Lógica de negocio
│  / Manager   │
└──────┬───────┘
       │ usa
       ↓
┌──────────────┐
│  Repository  │ ← Fuente de datos
│  / Client    │
└──────────────┘
```

### Ejemplo Completo: Agregar Nueva Pantalla

#### 1. Crear el Model (si es necesario)

```kotlin
// domain/model/MiDato.kt
data class MiDato(
    val id: Int,
    val nombre: String,
    val valor: Int
)
```

#### 2. Crear el ViewModel

```kotlin
// ui/mipantalla/MiPantallaViewModel.kt
class MiPantallaViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _datos = MutableLiveData<List<MiDato>>()
    val datos: LiveData<List<MiDato>> = _datos

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun cargarDatos() {
        viewModelScope.launch {
            _isLoading.postValue(true)
            try {
                val resultado = obtenerDatos()
                _datos.postValue(resultado)
            } catch (e: Exception) {
                _error.postValue("Error: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    private suspend fun obtenerDatos(): List<MiDato> = withContext(Dispatchers.IO) {
        // Lógica para obtener datos
        listOf()
    }
}
```

#### 3. Crear el Layout

```xml
<!-- res/layout/fragment_mi_pantalla.xml -->
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.progressindicator.CircularProgressIndicator
        android:id="@+id/progressBar"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:visibility="gone"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

#### 4. Crear el Fragment

```kotlin
// ui/mipantalla/MiPantallaFragment.kt
class MiPantallaFragment : Fragment() {

    private var _binding: FragmentMiPantallaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MiPantallaViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMiPantallaBinding.inflate(inflater, container, false)

        setupObservers()
        setupListeners()

        // Cargar datos iniciales
        viewModel.cargarDatos()

        return binding.root
    }

    private fun setupObservers() {
        viewModel.datos.observe(viewLifecycleOwner) { datos ->
            // Actualizar UI con datos
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupListeners() {
        // Configurar listeners de botones, etc.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

#### 5. Agregar a Navigation

```xml
<!-- res/navigation/mobile_navigation.xml -->
<navigation ...>
    <!-- Fragments existentes -->

    <fragment
        android:id="@+id/nav_mi_pantalla"
        android:name="com.example.ink_omron_viewer.ui.mipantalla.MiPantallaFragment"
        android:label="@string/menu_mi_pantalla"
        tools:layout="@layout/fragment_mi_pantalla" />
</navigation>
```

#### 6. Agregar al Menú

```xml
<!-- res/menu/activity_main_drawer.xml -->
<menu ...>
    <!-- Items existentes -->

    <item
        android:id="@+id/nav_mi_pantalla"
        android:icon="@drawable/ic_mi_icono"
        android:title="@string/menu_mi_pantalla" />
</menu>
```

#### 7. Agregar Strings

```xml
<!-- res/values/strings.xml -->
<resources>
    <!-- Strings existentes -->
    <string name="menu_mi_pantalla">Mi Pantalla</string>
</resources>
```

---

## Agregar Nuevas Funcionalidades

### Agregar Nueva Máquina

Supongamos que queremos agregar "Roller" (Transportador de Rodillos):

#### 1. Crear el Fragment

```kotlin
// ui/machines/RollerConfigurationFragment.kt
class RollerConfigurationFragment : Fragment() {
    private var _binding: FragmentMachineRollerBinding? = null
    private val binding get() = _binding!!

    private val commsViewModel: CommunicationViewModel by activityViewModels()

    // Direcciones de memoria (ajustar según necesidad)
    private val BASE_ADDRESS = 600

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMachineRollerBinding.inflate(inflater, container, false)

        setupListeners()
        loadConfiguration()

        return binding.root
    }

    private fun setupListeners() {
        // Timer
        binding.buttonSaveTimer.setOnClickListener {
            saveTimer()
        }

        // Contador con reset
        binding.buttonResetCounter.setOnClickListener {
            resetCounter()
        }

        // Switches
        binding.switchRollerEnabled.setOnCheckedChangeListener { _, isChecked ->
            writeSwitch(0, isChecked)
        }
    }

    private fun loadConfiguration() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Leer timer
            val timerResult = commsViewModel.readWord(
                FinsMemoryArea.DM.toInt(),
                BASE_ADDRESS
            )
            timerResult.onSuccess { value ->
                binding.editTimer.setText(value.toString())
            }

            // Leer contador
            val counterResult = commsViewModel.readWord(
                FinsMemoryArea.DM.toInt(),
                BASE_ADDRESS + 1
            )
            counterResult.onSuccess { value ->
                binding.textCounter.text = "Contador: $value"
            }

            // Leer switches
            val switchResult = commsViewModel.readWord(
                FinsMemoryArea.DM.toInt(),
                BASE_ADDRESS + 2
            )
            switchResult.onSuccess { value ->
                binding.switchRollerEnabled.isChecked = (value and 0x01) != 0
            }
        }
    }

    private fun saveTimer() {
        val timerValue = binding.editTimer.text.toString().toIntOrNull() ?: 0

        viewLifecycleOwner.lifecycleScope.launch {
            val result = commsViewModel.writeWord(
                FinsMemoryArea.DM.toInt(),
                BASE_ADDRESS,
                timerValue
            )

            result.onSuccess {
                Toast.makeText(requireContext(), "Timer guardado", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetCounter() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = commsViewModel.writeWord(
                FinsMemoryArea.DM.toInt(),
                BASE_ADDRESS + 1,
                0
            )

            result.onSuccess {
                binding.textCounter.text = "Contador: 0"
                Toast.makeText(requireContext(), "Contador reseteado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun writeSwitch(switchNumber: Int, enabled: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Leer valor actual
            val readResult = commsViewModel.readWord(
                FinsMemoryArea.DM.toInt(),
                BASE_ADDRESS + 2
            )

            readResult.onSuccess { currentValue ->
                val mask = 1 shl switchNumber
                val newValue = if (enabled) {
                    currentValue or mask
                } else {
                    currentValue and mask.inv()
                }

                commsViewModel.writeWord(
                    FinsMemoryArea.DM.toInt(),
                    BASE_ADDRESS + 2,
                    newValue
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

#### 2. Crear el Layout

```xml
<!-- res/layout/fragment_machine_roller.xml -->
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <!-- Timer -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            app:cardElevation="4dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Timer de Operación"
                    android:textSize="18sp"
                    android:textStyle="bold" />

                <com.google.android.material.textfield.TextInputLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:hint="Tiempo (segundos)">

                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/editTimer"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:inputType="number" />
                </com.google.android.material.textfield.TextInputLayout>

                <Button
                    android:id="@+id/buttonSaveTimer"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:text="Guardar Timer" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- Contador -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            app:cardElevation="4dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Contador de Ciclos"
                    android:textSize="18sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/textCounter"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:text="Contador: 0"
                    android:textSize="24sp" />

                <Button
                    android:id="@+id/buttonResetCounter"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:text="Reset" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- Switches -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:cardElevation="4dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Configuración"
                    android:textSize="18sp"
                    android:textStyle="bold" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switchRollerEnabled"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:text="Roller Habilitado" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
    </LinearLayout>
</ScrollView>
```

#### 3. Agregar al Navigation Graph

```xml
<!-- res/navigation/mobile_navigation.xml -->
<fragment
    android:id="@+id/nav_roller_config"
    android:name="com.example.ink_omron_viewer.ui.machines.RollerConfigurationFragment"
    android:label="@string/menu_roller_config"
    tools:layout="@layout/fragment_machine_roller" />
```

#### 4. Agregar al Menú

```xml
<!-- res/menu/activity_main_drawer.xml -->
<item android:id="@+id/nav_group_active_machines"
    android:icon="@drawable/ic_view_list"
    android:title="@string/menu_active_machines">
    <menu>
        <!-- Existentes -->
        <item
            android:id="@+id/nav_roller_config"
            android:title="@string/menu_roller_config" />
    </menu>
</item>
```

#### 5. Agregar Strings

```xml
<!-- res/values/strings.xml -->
<string name="menu_roller_config">Configuración Roller</string>
```

---

## Sistema de Comunicación FINS

### Lectura de Datos

```kotlin
// Leer una palabra (16 bits)
val result = commsViewModel.readWord(
    memoryArea = FinsMemoryArea.DM.toInt(),
    address = 100
)

result.onSuccess { value ->
    Log.d(TAG, "Valor leído: $value")
}.onFailure { error ->
    Log.e(TAG, "Error: ${error.message}")
}

// Leer múltiples palabras
val result = commsViewModel.readWords(
    memoryArea = FinsMemoryArea.DM.toInt(),
    address = 100,
    count = 10
)

result.onSuccess { values ->
    values.forEachIndexed { index, value ->
        Log.d(TAG, "D${100+index}: $value")
    }
}
```

### Escritura de Datos

```kotlin
// Escribir una palabra
val result = commsViewModel.writeWord(
    memoryArea = FinsMemoryArea.DM.toInt(),
    address = 100,
    value = 1234
)

// Escribir múltiples palabras
val result = commsViewModel.writeWords(
    memoryArea = FinsMemoryArea.DM.toInt(),
    address = 100,
    values = listOf(100, 200, 300)
)
```

### Áreas de Memoria FINS

```kotlin
enum class FinsMemoryArea(val code: Int) {
    CIO(0xB0),      // CIO Area
    WR(0xB1),       // Work Area
    HR(0xB2),       // Holding Area
    AR(0xB3),       // Auxiliary Area
    DM(0x82),       // Data Memory
    EM(0xA0);       // Extended Memory

    fun toInt() = code
}
```

### Manejo de Errores FINS

```kotlin
// En CommunicationViewModel
when (errorCode) {
    0x0000 -> "Operación exitosa"
    0x0101 -> "Comando no soportado"
    0x0201 -> "Área de memoria no accesible"
    0x110B -> "Error 4363: Formato de comando incorrecto"
    else -> "Error FINS: 0x${errorCode.toString(16)}"
}
```

---

## Testing

### Unit Tests

```kotlin
// test/.../ExampleUnitTest.kt
class LogManagerTest {

    @Test
    fun `parseLogEntry should extract action code correctly`() {
        val hexValue = "01000000"
        val entry = LogEntry.fromHexValue(hexValue, System.currentTimeMillis())

        assertEquals(0x01, entry.actionCode)
        assertEquals(0x00, entry.machineType)
    }
}
```

### Instrucciones Tests

```kotlin
// androidTest/.../ExampleInstrumentedTest.kt
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.ink_omron_viewer", appContext.packageName)
    }
}
```

### Ejecutar Tests

```bash
# Unit tests
./gradlew test

# Instrumentation tests
./gradlew connectedAndroidTest

# Specific test
./gradlew test --tests LogManagerTest
```

---

## Build y Despliegue

### Build Debug

```bash
./gradlew assembleDebug
```

APK generado en: `app/build/outputs/apk/debug/app-debug.apk`

### Build Release

```bash
./gradlew assembleRelease
```

APK generado en: `app/build/outputs/apk/release/app-release.apk`

### Firmar APK

1. **Crear keystore** (una vez):
```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias release
```

2. **Configurar en build.gradle.kts**:
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "contraseña"
            keyAlias = "release"
            keyPassword = "contraseña"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ...
        }
    }
}
```

3. **Build con firma**:
```bash
./gradlew assembleRelease
```

### ProGuard / R8

Para build de release, configurar en `proguard-rules.pro`:

```proguard
# Keep model classes
-keep class com.example.ink_omron_viewer.domain.** { *; }

# Keep FINS protocol classes
-keep class com.example.ink_omron_viewer.domain.fins.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
```

---

## Solución de Problemas

### Gradle Sync Falla

1. **Limpiar proyecto**:
```bash
./gradlew clean
```

2. **Invalidar caché** de Android Studio:
   - File → Invalidate Caches / Restart

3. **Verificar versión de Gradle**:
   - gradle/wrapper/gradle-wrapper.properties

### Build Falla

1. **Ver error completo** en Build tab
2. **Verificar dependencias** en build.gradle.kts
3. **Limpiar y rebuild**:
```bash
./gradlew clean build
```

### App Se Cierra al Iniciar

1. **Ver Logcat** en Android Studio
2. **Buscar** excepciones (FilterclassNotFoundException, etc.)
3. **Verificar permisos** en AndroidManifest.xml

### ViewBinding No Funciona

1. **Verificar** que está habilitado en build.gradle.kts:
```kotlin
android {
    buildFeatures {
        viewBinding = true
    }
}
```

2. **Sync Gradle**
3. **Rebuild Project**

---

## Mejores Prácticas

### 1. Manejo de Estado

```kotlin
// ✅ CORRECTO: Usar sealed class para estados
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class MyViewModel : ViewModel() {
    private val _state = MutableLiveData<UiState<List<Item>>>()
    val state: LiveData<UiState<List<Item>>> = _state

    fun loadData() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                val data = repository.getData()
                _state.value = UiState.Success(data)
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Error")
            }
        }
    }
}
```

### 2. Dependency Injection Manual

```kotlin
// Singleton objects para compartir
object AppContainer {
    val finsTcpClient: FinsTcpClient by lazy {
        FinsTcpClient()
    }
}

// Usar en ViewModel
class MyViewModel(application: Application) : AndroidViewModel(application) {
    private val client = AppContainer.finsTcpClient
}
```

### 3. Resource Management

```kotlin
// ✅ CORRECTO: Cerrar recursos
viewModelScope.launch {
    val client = FinsTcpClient()
    try {
        client.connect(ip, port)
        // usar client
    } finally {
        client.disconnect()
    }
}
```

### 4. String Resources

```kotlin
// ✅ CORRECTO: Usar string resources
binding.textView.text = getString(R.string.welcome_message)

// ❌ INCORRECTO: Hardcoded strings
binding.textView.text = "Welcome"  // ❌
```

---

## Recursos Adicionales

- [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html)
- [Android Architecture Components](https://developer.android.com/topic/architecture)
- [Material Design 3](https://m3.material.io/)
- [OMRON FINS Protocol](https://industrial.omron.es/)

---

**Última actualización**: Noviembre 2026 (app v1.9.4)
**Versión del documento**: 1.1