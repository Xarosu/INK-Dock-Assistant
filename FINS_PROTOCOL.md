# Protocolo FINS - Documentación Técnica

Documentación técnica del protocolo FINS (Factory Interface Network Service) de OMRON implementado en INK-Dock Assistant.

---

## Índice

1. [Introducción](#introducción)
2. [Arquitectura del Protocolo](#arquitectura-del-protocolo)
3. [Formato de Frame](#formato-de-frame)
4. [Comandos Implementados](#comandos-implementados)
5. [Áreas de Memoria](#áreas-de-memoria)
6. [Códigos de Error](#códigos-de-error)
7. [Ejemplos Prácticos](#ejemplos-prácticos)
8. [Implementación en Kotlin](#implementación-en-kotlin)

---

## Introducción

### ¿Qué es FINS?

**FINS (Factory Interface Network Service)** es un protocolo de comunicación propietario de OMRON para intercambio de datos entre PLCs y otros dispositivos en redes industriales.

### Características

- **Protocolo binario**: No es basado en texto
- **Orientado a comandos**: Request/Response pattern
- **Multi-plataforma**: Soporta Ethernet TCP/IP, UDP, USB, serial
- **Robusto**: Incluye checksums y control de errores
- **Eficiente**: Transmisión compacta de datos

### Versiones

- **FINS/TCP**: Sobre TCP/IP (usado en esta app)
- **FINS/UDP**: Sobre UDP
- **FINS/Serial**: Sobre RS-232/RS-422/RS-485

---

## Arquitectura del Protocolo

### Modelo OSI

```
┌─────────────────────────────────────────┐
│  7. Application    │  FINS Commands     │
├────────────────────┼────────────────────┤
│  6. Presentation   │  FINS Frame Format │
├────────────────────┼────────────────────┤
│  5. Session        │  FINS Header       │
├────────────────────┼────────────────────┤
│  4. Transport      │  TCP               │
├────────────────────┼────────────────────┤
│  3. Network        │  IP                │
├────────────────────┼────────────────────┤
│  1-2. Physical     │  Ethernet          │
└─────────────────────────────────────────┘
```

### Flujo de Comunicación

```
┌──────────────┐                          ┌──────────────┐
│   Cliente    │                          │     PLC      │
│  (Android)   │                          │   (OMRON)    │
└──────┬───────┘                          └──────┬───────┘
       │                                         │
       │ 1. TCP Connect                          │
       │────────────────────────────────────────>│
       │                                         │
       │ 2. TCP Connection Established           │
       │<────────────────────────────────────────│
       │                                         │
       │ 3. FINS Request                         │
       │    (Read Memory Command)                │
       │────────────────────────────────────────>│
       │                                         │
       │                    4. PLC Procesa       │
       │                       Comando           │
       │                                    ┌────┤
       │                                    │    │
       │                                    └───>│
       │                                         │
       │ 5. FINS Response                        │
       │    (Data + Status)                      │
       │<────────────────────────────────────────│
       │                                         │
       │ 6. Parse Response                       │
       │    Extract Data                         │
  ┌────┤                                         │
  │    │                                         │
  └───>│                                         │
       │                                         │
```

---

## Formato de Frame

### Estructura Completa

Un frame FINS TCP/IP consta de:

1. **TCP Header** (automático, manejado por socket)
2. **FINS TCP Header** (16 bytes)
3. **FINS Command Header** (10 bytes)
4. **FINS Command Code** (2 bytes)
5. **FINS Parameters** (variable)
6. **FINS Data** (variable, solo en write)

### 1. FINS TCP Header (16 bytes)

```
Offset  Size  Field           Description
──────────────────────────────────────────────────
0       4     Magic           "FINS" (0x46494E53)
4       4     Length          Frame length (bytes after this field)
8       4     Command         0x00000002 (Command Frame)
12      4     Error Code      0x00000000 (No error)
```

**Ejemplo en Hex**:
```
46 49 4E 53    Magic: "FINS"
00 00 00 1A    Length: 26 bytes
00 00 00 02    Command: 0x02
00 00 00 00    Error: 0x00
```

### 2. FINS Command Header (10 bytes)

```
Offset  Size  Field  Description
───────────────────────────────────────────────────────────
0       1     ICF    Information Control Field
1       1     RSV    Reserved (0x00)
2       1     GCT    Gateway Count (0x02)
3       1     DNA    Destination Network Address
4       1     DA1    Destination Node Number
5       1     DA2    Destination Unit Address (0x00)
6       1     SNA    Source Network Address
7       1     SA1    Source Node Number
8       1     SA2    Source Unit Address (0x00)
9       1     SID    Service ID (0x00)
```

**ICF - Information Control Field**:
```
Bit 7: Response (0=Request, 1=Response)
Bit 6: Command (0=Command, 1=Response)
Bit 5-0: Reserved (0x00)
```

**Ejemplo**:
```
ICF: 0x80  (Response bit set)
RSV: 0x00
GCT: 0x02
DNA: 0x00  (Local network)
DA1: 0x00  (Node 0)
DA2: 0x00  (Unit 0)
SNA: 0x00  (Local network)
SA1: 0x00  (Node 0)
SA2: 0x00  (Unit 0)
SID: 0x00
```

### 3. FINS Command Code (2 bytes)

```
Offset  Size  Field      Description
──────────────────────────────────────────
0       1     Main Code  Command type
1       1     Sub Code   Command variant
```

**Main Codes**:
```
0x01: Memory Area Access
0x02: Parameter Area Access
0x03: Program Area Access
0x04: Operating Mode Access
0x05: Machine Configuration Read
0x06: Status Read
...
```

**Sub Codes (para Main Code 0x01)**:
```
0x01: Memory Area Read
0x02: Memory Area Write
0x03: Memory Area Fill
0x04: Multiple Area Read
```

### 4. FINS Parameters (variable)

Depende del comando específico. Para **Memory Area Read**:

```
Offset  Size  Field         Description
──────────────────────────────────────────────────
0       1     Memory Area   Código de área de memoria
1       2     Address       Dirección (big-endian)
3       1     Bit Number    Número de bit (0x00 para words)
4       2     Count         Número de elementos (big-endian)
```

**Ejemplo - Leer D100-D109 (10 palabras)**:
```
Memory Area: 0x82  (DM)
Address:     0x00 0x64  (100 en decimal)
Bit:         0x00
Count:       0x00 0x0A  (10 en decimal)
```

---

## Comandos Implementados

### 1. Memory Area Read (0x0101)

**Request**:
```
┌─────────────────────────────────────────────────┐
│ TCP Header (16 bytes)                           │
│   Magic: FINS                                   │
│   Length: 26                                    │
│   Command: 0x02                                 │
│   Error: 0x00                                   │
├─────────────────────────────────────────────────┤
│ Command Header (10 bytes)                       │
│   ICF: 0x80                                     │
│   RSV: 0x00                                     │
│   GCT: 0x02                                     │
│   DNA: 0x00, DA1: 0x00, DA2: 0x00               │
│   SNA: 0x00, SA1: 0x00, SA2: 0x00               │
│   SID: 0x00                                     │
├─────────────────────────────────────────────────┤
│ Command Code (2 bytes)                          │
│   Main: 0x01                                    │
│   Sub: 0x01                                     │
├─────────────────────────────────────────────────┤
│ Parameters (6 bytes)                            │
│   Memory Area: 0x82 (DM)                        │
│   Address: 0x00 0x64 (D100)                     │
│   Bit: 0x00                                     │
│   Count: 0x00 0x0A (10 words)                   │
└─────────────────────────────────────────────────┘
```

**Response**:
```
┌─────────────────────────────────────────────────┐
│ TCP Header (16 bytes)                           │
│   Magic: FINS                                   │
│   Length: 30                                    │
│   Command: 0x02                                 │
│   Error: 0x00                                   │
├─────────────────────────────────────────────────┤
│ Command Header (10 bytes)                       │
│   [Same as request with response bits set]     │
├─────────────────────────────────────────────────┤
│ Command Code (2 bytes)                          │
│   Main: 0x01                                    │
│   Sub: 0x01                                     │
├─────────────────────────────────────────────────┤
│ Status (2 bytes)                                │
│   Main Status: 0x00 (Success)                   │
│   Sub Status: 0x00                              │
├─────────────────────────────────────────────────┤
│ Data (variable)                                 │
│   Word 0: 0x01 0x23                             │
│   Word 1: 0x04 0x56                             │
│   ...                                           │
│   Word 9: 0x0A 0xBC                             │
└─────────────────────────────────────────────────┘
```

### 2. Memory Area Write (0x0102)

**Request**:
```
┌─────────────────────────────────────────────────┐
│ TCP Header                                      │
│ Command Header                                  │
│ Command Code: 0x01 0x02                         │
├─────────────────────────────────────────────────┤
│ Parameters (6 bytes)                            │
│   Memory Area: 0x82                             │
│   Address: 0x00 0x64                            │
│   Bit: 0x00                                     │
│   Count: 0x00 0x01                              │
├─────────────────────────────────────────────────┤
│ Data (2 bytes per word)                         │
│   Word 0: 0x01 0x23 (Value to write)            │
└─────────────────────────────────────────────────┘
```

**Response**:
```
┌─────────────────────────────────────────────────┐
│ TCP Header                                      │
│ Command Header                                  │
│ Command Code: 0x01 0x02                         │
├─────────────────────────────────────────────────┤
│ Status (2 bytes)                                │
│   Main Status: 0x00 (Success)                   │
│   Sub Status: 0x00                              │
└─────────────────────────────────────────────────┘
```

---

## Áreas de Memoria

### Códigos de Área

```kotlin
enum class FinsMemoryArea(val code: Int) {
    CIO(0xB0),      // CIO Area (Common I/O)
    WR(0xB1),       // Work Area
    HR(0xB2),       // Holding Area
    AR(0xB3),       // Auxiliary Area
    DM(0x82),       // Data Memory
    EM_0(0xA0),     // Extended Memory Bank 0
    EM_1(0xA1),     // Extended Memory Bank 1
    EM_2(0xA2),     // Extended Memory Bank 2
    EM_3(0xA3),     // Extended Memory Bank 3
    EM_4(0xA4),     // Extended Memory Bank 4
    EM_5(0xA5),     // Extended Memory Bank 5
    EM_6(0xA6),     // Extended Memory Bank 6
    EM_7(0xA7),     // Extended Memory Bank 7
    EM_8(0xA8),     // Extended Memory Bank 8
    EM_9(0xA9),     // Extended Memory Bank 9
    EM_A(0xAA),     // Extended Memory Bank A
    EM_B(0xAB),     // Extended Memory Bank B
    EM_C(0xAC),     // Extended Memory Bank C
    TIM(0x09),      // Timer
    CNT(0x09);      // Counter

    fun toInt() = code
}
```

### Características por Área

| Área | Código | Tamaño Típico | Retención | Uso |
|------|--------|---------------|-----------|-----|
| CIO | 0xB0 | 6144 words | No | I/O, flags temporales |
| WR | 0xB1 | 512 words | No | Trabajo temporal |
| HR | 0xB2 | 512 words | Sí | Retención de datos |
| AR | 0xB3 | Varía | Parcial | Flags del sistema |
| DM | 0x82 | 32768 words | Sí | Almacenamiento datos |
| EM | 0xA0-0xAC | 32768 words/banco | Sí | Almacenamiento extendido |

---

## Códigos de Error

### Main Status Codes

| Código | Hex | Descripción |
|--------|-----|-------------|
| 0 | 0x00 | Normal completion |
| 1 | 0x01 | Service cancelled |
| 2 | 0x02 | Parameter error |
| 3 | 0x03 | Not executable in current mode |
| 4 | 0x04 | Route table error |
| 5 | 0x05 | Destination node error |
| 16 | 0x10 | Timeout |
| 17 | 0x11 | Communications error |
| 18 | 0x12 | Destination node not found |
| 19 | 0x13 | Unit error |

### Sub Status Codes (para Main Status 0x01)

| Código | Hex | Descripción |
|--------|-----|-------------|
| 1 | 0x01 | Command too long |
| 2 | 0x02 | Command too short |
| 3 | 0x03 | Elements/data don't match |
| 4 | 0x04 | Command format error |
| 5 | 0x05 | Header error |

### Sub Status Codes (para Main Status 0x02)

| Código | Hex | Descripción |
|--------|-----|-------------|
| 1 | 0x01 | Memory area code invalid |
| 2 | 0x02 | Access size error |
| 3 | 0x03 | Address out of range |
| 4 | 0x04 | Command not supported |
| 11 | 0x0B | **Command format error (4363)** |

**Error 4363 (0x110B)**:
- Main Status: 0x11 (Communications error)
- Sub Status: 0x0B (Command format error)
- Causa común: Área de memoria no existe o no está configurada

---

## Ejemplos Prácticos

### Ejemplo 1: Leer D100 (1 palabra)

**Request (Hex)**:
```
46 49 4E 53              // Magic: "FINS"
00 00 00 1A              // Length: 26 bytes
00 00 00 02              // Command: Frame
00 00 00 00              // Error Code: None

80 00 02                 // ICF, RSV, GCT
00 00 00                 // DNA, DA1, DA2
00 00 00 00              // SNA, SA1, SA2, SID

01 01                    // Command: Memory Area Read

82                       // Memory Area: DM
00 64                    // Address: 100
00                       // Bit: 0
00 01                    // Count: 1 word
```

**Response (Hex)**:
```
46 49 4E 53              // Magic
00 00 00 12              // Length: 18 bytes
00 00 00 02              // Command
00 00 00 00              // Error

C0 00 02                 // ICF (response), RSV, GCT
00 00 00                 // DNA, DA1, DA2
00 00 00 00              // SNA, SA1, SA2, SID

01 01                    // Command

00 00                    // Status: Success

01 23                    // Data: D100 = 0x0123 (291 decimal)
```

**Código Kotlin**:
```kotlin
// Leer D100
val result = commsViewModel.readWord(
    memoryArea = FinsMemoryArea.DM.toInt(),
    address = 100
)

result.onSuccess { value ->
    println("D100 = $value")  // Output: D100 = 291
}
```

### Ejemplo 2: Escribir D200 = 1000

**Request (Hex)**:
```
46 49 4E 53              // Magic
00 00 00 1C              // Length: 28 bytes
00 00 00 02              // Command
00 00 00 00              // Error

80 00 02                 // ICF, RSV, GCT
00 00 00                 // DNA, DA1, DA2
00 00 00 00              // SNA, SA1, SA2, SID

01 02                    // Command: Memory Area Write

82                       // Memory Area: DM
00 C8                    // Address: 200
00                       // Bit: 0
00 01                    // Count: 1 word

03 E8                    // Data: 1000 (0x03E8)
```

**Response (Hex)**:
```
46 49 4E 53
00 00 00 10              // Length: 16 bytes
00 00 00 02
00 00 00 00

C0 00 02
00 00 00
00 00 00 00

01 02                    // Command

00 00                    // Status: Success
```

**Código Kotlin**:
```kotlin
// Escribir D200 = 1000
val result = commsViewModel.writeWord(
    memoryArea = FinsMemoryArea.DM.toInt(),
    address = 200,
    value = 1000
)

result.onSuccess {
    println("D200 escrito correctamente")
}
```

### Ejemplo 3: Leer D300-D304 (5 palabras)

**Código Kotlin**:
```kotlin
val result = commsViewModel.readWords(
    memoryArea = FinsMemoryArea.DM.toInt(),
    address = 300,
    count = 5
)

result.onSuccess { values ->
    values.forEachIndexed { index, value ->
        println("D${300+index} = $value")
    }
}
```

**Output**:
```
D300 = 100
D301 = 200
D302 = 300
D303 = 400
D304 = 500
```

---

## Implementación en Kotlin

> Los fragmentos de esta sección son **ilustrativos**. En el código de la v1.9.4 la implementación está repartida así:
> - `FinsProtocol` (objeto): `buildReadCommand(...)` y `buildWriteCommand(...)` con nodo cliente, nodo servidor y SID
> - `FinsTcpProtocol` (objeto): handshake de nodo FINS/TCP (`buildHandshakeCommand`, `parseHandshakeResponse`) y cabecera TCP (`wrapCommand`, `unwrapResponse`)
> - `FinsTcpClient`: implementa `IFinsClient` (`readWords`, `writeWords`, `disconnect`) sobre un socket TCP al puerto 9600
> - `FinsUdpClient`: variante FINS/UDP usada en la prueba de conexión

### FinsTcpClient

```kotlin
class FinsTcpClient {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    suspend fun connect(ip: String, port: Int) = withContext(Dispatchers.IO) {
        socket = Socket().apply {
            connect(InetSocketAddress(ip, port), 5000)
        }
        inputStream = socket?.getInputStream()
        outputStream = socket?.getOutputStream()
    }

    suspend fun readMemory(
        area: Int,
        address: Int,
        count: Int
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val command = buildReadCommand(area, address, count)
            val response = sendCommand(command)
            parseReadResponse(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildReadCommand(
        area: Int,
        address: Int,
        count: Int
    ): ByteArray {
        val builder = FinsFrameBuilder()
        return builder.buildReadCommand(area, address, count)
    }

    private suspend fun sendCommand(command: ByteArray): ByteArray {
        outputStream?.write(command)
        outputStream?.flush()

        val buffer = ByteArray(2048)
        val bytesRead = inputStream?.read(buffer) ?: 0
        return buffer.copyOf(bytesRead)
    }

    private fun parseReadResponse(response: ByteArray): Result<ByteArray> {
        // Verificar longitud mínima
        if (response.size < 30) {
            return Result.failure(Exception("Response too short"))
        }

        // Extraer status codes
        val mainStatus = response[28].toInt() and 0xFF
        val subStatus = response[29].toInt() and 0xFF

        // Verificar éxito
        if (mainStatus != 0x00 || subStatus != 0x00) {
            val errorCode = (mainStatus shl 8) or subStatus
            return Result.failure(Exception("FINS Error: $errorCode"))
        }

        // Extraer datos
        val dataLength = response.size - 30
        val data = ByteArray(dataLength)
        System.arraycopy(response, 30, data, 0, dataLength)

        return Result.success(data)
    }
}
```

### FinsFrameBuilder

```kotlin
class FinsFrameBuilder {
    fun buildReadCommand(
        area: Int,
        address: Int,
        count: Int
    ): ByteArray {
        val commandSize = 34  // Fixed size for read command

        val buffer = ByteArray(commandSize)
        var offset = 0

        // TCP Header
        buffer[offset++] = 'F'.code.toByte()
        buffer[offset++] = 'I'.code.toByte()
        buffer[offset++] = 'N'.code.toByte()
        buffer[offset++] = 'S'.code.toByte()

        // Length (26 bytes after this field)
        offset = writeInt(buffer, offset, 26)

        // Command (0x02)
        offset = writeInt(buffer, offset, 0x02)

        // Error Code (0x00)
        offset = writeInt(buffer, offset, 0x00)

        // FINS Command Header
        buffer[offset++] = 0x80.toByte()  // ICF
        buffer[offset++] = 0x00           // RSV
        buffer[offset++] = 0x02           // GCT
        buffer[offset++] = 0x00           // DNA
        buffer[offset++] = 0x00           // DA1
        buffer[offset++] = 0x00           // DA2
        buffer[offset++] = 0x00           // SNA
        buffer[offset++] = 0x00           // SA1
        buffer[offset++] = 0x00           // SA2
        buffer[offset++] = 0x00           // SID

        // Command Code
        buffer[offset++] = 0x01           // Memory Area Read
        buffer[offset++] = 0x01           // Read

        // Parameters
        buffer[offset++] = area.toByte()                    // Memory Area
        buffer[offset++] = ((address shr 8) and 0xFF).toByte()  // Address high
        buffer[offset++] = (address and 0xFF).toByte()           // Address low
        buffer[offset++] = 0x00                                  // Bit
        buffer[offset++] = ((count shr 8) and 0xFF).toByte()     // Count high
        buffer[offset++] = (count and 0xFF).toByte()             // Count low

        return buffer
    }

    private fun writeInt(buffer: ByteArray, offset: Int, value: Int): Int {
        var off = offset
        buffer[off++] = ((value shr 24) and 0xFF).toByte()
        buffer[off++] = ((value shr 16) and 0xFF).toByte()
        buffer[off++] = ((value shr 8) and 0xFF).toByte()
        buffer[off++] = (value and 0xFF).toByte()
        return off
    }
}
```

---

## Referencias

### Documentación Oficial OMRON

- **W342-E1-14**: CS/CJ/CP Series FINS Commands Reference Manual
- **W342-E1-18**: CS1/CJ1/CP1 Series Communications Commands Reference Manual
- **W483-E1-08**: CP1E/CP2E Operation Manual

### Recursos Adicionales

- [OMRON Industrial Automation](https://industrial.omron.es/)
- [FINS Protocol Specification](https://industrial.omron.es/en/products/fins)

---

**Última actualización**: Noviembre 2026 (app v1.9.4)
**Versión del documento**: 1.1