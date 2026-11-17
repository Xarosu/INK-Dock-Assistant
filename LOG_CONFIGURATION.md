# Configuración del Sistema de LOG en el PLC OMRON

> ⚠️ **Documento histórico (app v1.5).** Describe el primer sistema de LOG (150 entradas, PLC CP1E, configuración leída del PLC). Las direcciones y formatos vigentes en la app **v1.9.4** (300 eventos con fecha BCD, activación en H0/H1/D550/D551, switches en H120-H124) están en **[MEMORY_MAP.md](MEMORY_MAP.md)**. Se conserva como referencia de la lógica ladder.

## Error FINS 4363 (0x110B) - Command Format Error

Este error ocurre cuando se intenta acceder a áreas de memoria que no existen o no están configuradas en el PLC.

## Solución

### 1. Direcciones de Memoria Actualizadas

El sistema ha sido actualizado para usar direcciones más compatibles:

#### **Configuración del PLC** (41 palabras totales)
```
D500-D519  : Modelo del PLC (20 palabras = 40 caracteres ASCII)
D520-D529  : Dirección IP (10 palabras = 20 caracteres ASCII)
D530-D539  : Número de Pedido/Posición (10 palabras = 20 caracteres ASCII)
D540       : Número de Muelle (1 palabra, valor entero)
```

#### **Registro de Actividad** (600 palabras para 150 entradas)
```
D600-D1199 : Log de acciones (150 entradas × 4 palabras/entrada)
```

Cada entrada de log ocupa 4 palabras:
- **Palabras 0-1**: Timestamp en segundos (32 bits, DWORD)
- **Palabras 2-3**: Valor hexadecimal de la acción (32 bits, DWORD)

### 2. Configuración en CX-Programmer

1. **Abrir CX-Programmer** y conectar al PLC

2. **Verificar Área DM**:
   - Ir a: `PLC` → `Memory` → `Data Memory (DM)`
   - Verificar que el área DM tiene al menos 1200 palabras configuradas
   - Si no es suficiente: `PLC` → `Edit` → `I/O Table and Unit Setup` → aumentar DM

3. **Inicializar las áreas** (programa ladder):

```ladder
;; Inicialización del área de configuración
LD ALWAYS_ON_BIT
MOV #0 D500    ; Inicializar área de modelo
MOV #0 D520    ; Inicializar área de IP
MOV #0 D530    ; Inicializar área de pedido
MOV #1 D540    ; Número de muelle por defecto = 1

;; Inicializar área de log (poner a 0)
LD FIRST_SCAN_BIT
BSET D600 600  ; Borrar 600 palabras desde D600
```

4. **Escribir datos de configuración**:

Desde CX-Programmer, ir a: `PLC` → `Memory` → `DM` y escribir manualmente:

**D500-D519 (Modelo PLC)**:
- Escribir en formato ASCII hexadecimal
- Ejemplo: "CP1E-E30DRA" → convertir cada carácter a hex
  - 'C' = 0x43, 'P' = 0x50, '1' = 0x31, etc.

**D520-D529 (Dirección IP)**:
- Ejemplo: "192.168.250.1"

**D530-D539 (Número de Pedido)**:
- Ejemplo: "PED-2025-001"

**D540 (Número de Muelle)**:
- Valor decimal: 1, 2, 3, etc.

### 3. Formato de Entrada de Log

Cada acción del PLC debe escribir 4 palabras en el área de log:

```ladder
;; Ejemplo: Registrar "Inicio de operación - Shelter"
;; D600+offset = Timestamp (high word)
;; D601+offset = Timestamp (low word)
;; D602+offset = Hex value (high word)
;; D603+offset = Hex value (low word)

;; Calcular timestamp actual (segundos desde epoch)
;; Nota: Requiere reloj del PLC configurado

;; Construir valor hexadecimal:
;; Bits 31-24: Código de acción (0x01 = Inicio)
;; Bits 23-16: Tipo de máquina (0x00 = Shelter)
;; Bits 15-0:  Datos adicionales (0x0000 = ninguno)

MOV #0100 D602    ; High word = 0x0100 (acción 01, máquina 00)
MOV #0000 D603    ; Low word = 0x0000 (sin datos adicionales)
```

### 4. Códigos de Acción Definidos

```
0x01 : Inicio de operación
0x02 : Fin de operación
0x03 : Reset contador
0x04 : Cambio configuración
0x05 : Cambio switch
0x06 : Error
0x07 : Alarma
0x08 : Mantenimiento
0x09 : Inicio sistema
0x0A : Parada sistema
```

### 5. Códigos de Tipo de Máquina

```
0x00 : Shelter (Abrigo Hinchable)
0x01 : Door (Puerta Seccional)
0x02 : Platform (Plataforma)
0x03 : Traffic Light (Semáforos)
```

## Verificación

Para verificar que las direcciones están correctamente configuradas:

1. **Desde CX-Programmer**:
   - `PLC` → `Memory` → `DM`
   - Intentar leer D500, D540, D600, D1199
   - Si alguna dirección no existe, ajustar el tamaño del área DM

2. **Desde la aplicación Android**:
   - Ir a `Sinóptico`
   - Intentar leer manualmente:
     - Dirección: `D500`
     - Dirección: `D540`
     - Dirección: `D600`
   - Si funciona, el área está correctamente configurada

## Capacidad de Memoria del PLC

El PLC dispone de **16,370 palabras** en el área D, más que suficiente para el sistema de LOG.

**Uso actual:**
- D500-D540: 41 palabras (configuración)
- D600-D1199: 600 palabras (150 entradas de log)
- **Total usado: 641 palabras (3.9% de la memoria disponible)**

**Memoria disponible restante: 15,729 palabras (96.1%)**

### Opciones de Expansión

Con la memoria disponible, se pueden implementar mejoras:

#### Opción 1: Más entradas de log
```kotlin
// En LogManager.kt, cambiar:
private const val LOG_ENTRIES_COUNT = 500  // En lugar de 150
private const val LOG_ENTRIES_START_ADDRESS = 600
```
Esto usaría 2,000 palabras para 500 entradas (D600-D2599).

#### Opción 2: Log por máquina
Crear áreas separadas para cada tipo de máquina:
```
D600-D999:   Log de Shelter (100 entradas × 4 palabras)
D1000-D1399: Log de Door (100 entradas × 4 palabras)
D1400-D1799: Log de Platform (100 entradas × 4 palabras)
D1800-D2199: Log de Traffic Light (100 entradas × 4 palabras)
```

#### Opción 3: Histórico extendido
Mantener un área de "histórico importante" que no se sobrescribe:
```
D600-D1199:  Log circular normal (150 entradas)
D1200-D2199: Histórico de eventos críticos (250 entradas)
```

#### Opción 4: Datos de configuración extendidos
Almacenar más información:
```
D2000-D2099: Configuración detallada de cada máquina
D2100-D2199: Parámetros de calibración
D2200-D2299: Histórico de mantenimientos
```

## Solución Temporal: Deshabilitar LOG

Si necesitas que la app funcione sin el sistema de LOG:

1. Simplemente no accedas al menú "Registro de Actividad"
2. Las demás funciones de la app seguirán funcionando normalmente
3. El error 4363 solo aparece al intentar leer las áreas de LOG

## Contacto

Para más ayuda con la configuración del PLC, contactar a:
- X. Rodriguez: xarosu@gmail.com
- *Issues* del repositorio en GitHub