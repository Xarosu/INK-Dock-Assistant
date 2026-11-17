# Ejemplos de Programación Ladder para Sistema de LOG

> ⚠️ **Documento histórico (app v1.5).** Describe el primer sistema de LOG (150 entradas, PLC CP1E, configuración leída del PLC). Las direcciones y formatos vigentes en la app **v1.9.4** (300 eventos con fecha BCD, activación en H0/H1/D550/D551, switches en H120-H124) están en **[MEMORY_MAP.md](MEMORY_MAP.md)**. Se conserva como referencia de la lógica ladder.

## Estado Actual
Las áreas de memoria D500-D540 y D600-D1199 están accesibles pero devuelven valor 0 (sin inicializar).

## Objetivo
Proporcionar ejemplos de código ladder para:
1. Inicializar la configuración del PLC (D500-D540)
2. Registrar acciones en el log (D600-D1199)
3. Gestionar el puntero circular del log

---

## 1. Inicialización de Configuración del PLC

### 1.1 Escribir Modelo del PLC (D500-D519)

```ladder
;; Rung 1: Inicializar modelo del PLC en primer escaneo
LD      P_First_Cycle           ; Bit de primer ciclo
MOV     #4350 D500              ; "CP" en ASCII (C=0x43, P=0x50)
MOV     #3145 D501              ; "1E" en ASCII (1=0x31, E=0x45)
MOV     #452D D502              ; "-E" en ASCII
MOV     #3330 D503              ; "30" en ASCII
MOV     #4452 D504              ; "DR" en ASCII
MOV     #4100 D505              ; "A" + nulo
MOV     #0000 D506              ; Resto en 0
```

**Tabla de Conversión ASCII a Hexadecimal:**
| Carácter | Hex  | Decimal |
|----------|------|---------|
| C        | 0x43 | 67      |
| P        | 0x50 | 80      |
| 1        | 0x31 | 49      |
| E        | 0x45 | 69      |
| -        | 0x2D | 45      |
| 3        | 0x33 | 51      |
| 0        | 0x30 | 48      |
| D        | 0x44 | 68      |
| R        | 0x52 | 82      |
| A        | 0x41 | 65      |

**Formato en memoria:** Cada palabra contiene 2 caracteres ASCII
- D500 = 0x4350 = "CP"
- D501 = 0x3145 = "1E"
- etc.

### 1.2 Escribir Dirección IP (D520-D529)

```ladder
;; Rung 2: Inicializar dirección IP
LD      P_First_Cycle
MOV     #3139 D520              ; "19" (1=0x31, 9=0x39)
MOV     #322E D521              ; "2." (2=0x32, .=0x2E)
MOV     #3136 D522              ; "16"
MOV     #382E D523              ; "8."
MOV     #3235 D524              ; "25"
MOV     #302E D525              ; "0."
MOV     #3100 D526              ; "1" + nulo
MOV     #0000 D527              ; Resto en 0
```

### 1.3 Escribir Número de Pedido (D530-D539)

```ladder
;; Rung 3: Inicializar número de pedido
LD      P_First_Cycle
MOV     #5045 D530              ; "PE" (P=0x50, E=0x45)
MOV     #442D D531              ; "D-"
MOV     #3230 D532              ; "20"
MOV     #3235 D533              ; "25"
MOV     #2D30 D534              ; "-0"
MOV     #3030 D535              ; "00"
MOV     #3100 D536              ; "1" + nulo
MOV     #0000 D537              ; Resto en 0
```

### 1.4 Escribir Número de Muelle (D540)

```ladder
;; Rung 4: Inicializar número de muelle
LD      P_First_Cycle
MOV     #1 D540                 ; Muelle número 1 (valor entero)
```

---

## 2. Sistema de LOG de Acciones

### 2.1 Variables de Control del LOG

```ladder
;; Área de trabajo para el sistema de LOG
D1190: Puntero actual del log (0-149)
D1191: Contador total de entradas
D1192: Timestamp - parte alta (segundos desde epoch / 65536)
D1193: Timestamp - parte baja (segundos desde epoch % 65536)
```

### 2.2 Inicializar Sistema de LOG

```ladder
;; Rung 5: Inicializar sistema de LOG
LD      P_First_Cycle
BSET    D600 600                ; Borrar área de log (600 palabras)
MOV     #0 D1190                ; Puntero en 0
MOV     #0 D1191                ; Contador en 0

;; Rung 6: Registrar acción "Inicio de Sistema" en primer escaneo
LD      P_First_Cycle
SET     W100.00                 ; Activar flag de registro (acción 0x09, máquina 0x00)
MOV     #0900 D1195             ; Preparar código de acción
MOV     #0000 D1196
```

### 2.3 Calcular Timestamp Actual

**IMPORTANTE:** Este ejemplo asume que el PLC tiene un reloj configurado.

```ladder
;; Rung 7: Calcular timestamp cuando se solicita registro
LD      W100.00                 ; Flag de solicitud de registro
;; Leer fecha/hora del PLC
READ    A351                    ; Año (BCD)
READ    A352                    ; Mes (BCD)
READ    A353                    ; Día (BCD)
READ    A354                    ; Hora (BCD)
READ    A355                    ; Minuto (BCD)
READ    A356                    ; Segundo (BCD)

;; NOTA: La conversión de fecha/hora a timestamp Unix requiere
;; cálculos complejos. Ver "Opción Simplificada" más abajo.
```

**Opción Simplificada:** Usar contador de segundos relativo

```ladder
;; Rung 8: Usar contador de segundos desde inicio (más simple)
LD      P_1_Second              ; Pulso de 1 segundo
INC     D1192                   ; Incrementar contador de segundos (parte baja)
LD      CY                      ; Si hay carry
INC     D1193                   ; Incrementar parte alta
```

### 2.4 Registrar Entrada en el LOG

```ladder
;; Rung 9: Función de registro de LOG
;; Entrada: W100.00 = solicitud de registro
;;          D1195-D1196 = valor hexadecimal (32 bits)
LD      W100.00

;; Calcular dirección base: D600 + (puntero × 4)
MOV     D1190 D1198             ; Copiar puntero
MUL     D1198 #4                ; Multiplicar por 4 palabras/entrada
ADD     D1198 #600              ; Sumar base (D600)

;; Escribir timestamp
MOVD    D1192 @D1198            ; Escribir timestamp (32 bits)

;; Avanzar puntero (+2 palabras)
ADD     D1198 #2

;; Escribir valor hexadecimal
MOVD    D1195 @D1198            ; Escribir hex value (32 bits)

;; Incrementar puntero circular
INC     D1190
LD      D1190 >= #150           ; Si llegó a 150
MOV     #0 D1190                ; Volver a 0 (circular)

;; Incrementar contador total (no circular)
INC     D1191

;; Limpiar flag de solicitud
RST     W100.00
```

---

## 3. Ejemplos de Registro de Acciones Específicas

### 3.1 Inicio de Operación - Shelter

```ladder
;; Rung 10: Registrar inicio de Shelter
LD      W0.00                   ; Botón de inicio Shelter
AND NOT W100.00                 ; No hay registro en curso
SET     W100.00                 ; Solicitar registro

;; Construir código hex: 0x01000000
;; Bits 31-24: 0x01 (Inicio operación)
;; Bits 23-16: 0x00 (Shelter)
;; Bits 15-0:  0x0000 (sin datos adicionales)
MOV     #0100 D1195             ; Parte alta: 0x0100
MOV     #0000 D1196             ; Parte baja: 0x0000
```

### 3.2 Fin de Operación - Door

```ladder
;; Rung 11: Registrar fin de Door
LD      W0.01                   ; Botón de fin Door
AND NOT W100.00
SET     W100.00

;; Código hex: 0x02010000
;; 0x02 = Fin operación
;; 0x01 = Door
MOV     #0201 D1195
MOV     #0000 D1196
```

### 3.3 Reset Contador - Platform

```ladder
;; Rung 12: Registrar reset de contador Platform
LD      W0.02                   ; Botón reset Platform
AND NOT W100.00
SET     W100.00

;; Código hex: 0x03020000
;; 0x03 = Reset contador
;; 0x02 = Platform
MOV     #0302 D1195
MOV     #0000 D1196
```

### 3.4 Cambio de Switch con Datos Adicionales

```ladder
;; Rung 13: Registrar cambio de switch con número de switch
LD      W0.03                   ; Cambio en switch
AND NOT W100.00
SET     W100.00

;; Código hex: 0x05030005
;; 0x05 = Cambio switch
;; 0x03 = Traffic Light
;; 0x0005 = Switch número 5
MOV     #0503 D1195
MOV     #0005 D1196             ; Datos adicionales: número de switch
```

### 3.5 Registro de Error con Código

```ladder
;; Rung 14: Registrar error con código de error
LD      W0.04                   ; Error detectado
AND NOT W100.00
SET     W100.00

;; Código hex: 0x0600001F
;; 0x06 = Error
;; 0x00 = Shelter (o la máquina que generó el error)
;; 0x001F = Código de error 31
MOV     #0600 D1195
MOV     D500 D1196              ; D500 contiene el código de error
```

---

## 4. Función Reutilizable de LOG

Para facilitar el registro desde múltiples puntos del programa:

```ladder
;; Subrutina: LOG_ACTION
;; Entrada: D1200 = action code (byte)
;;          D1201 = machine type (byte)
;;          D1202 = additional data (word)
;; Salida:  Ninguna

;; Rung 100 (en subrutina SBR_LOG)
LD      ALWAYS_ON

;; Esperar a que no haya registro en curso
LD      W100.00
JMP0    @LOG_START
RET                             ; Si hay registro en curso, salir

LOG_START:
;; Construir valor hexadecimal
MOV     D1200 D1197             ; Action code
SHL     D1197 #8                ; Desplazar 8 bits
OR      D1197 D1201             ; Agregar machine type
MOV     D1197 D1195             ; Parte alta lista

MOV     D1202 D1196             ; Parte baja (datos adicionales)

;; Solicitar registro
SET     W100.00

RET
```

**Uso de la subrutina:**

```ladder
;; Rung 50: Ejemplo de uso
LD      W5.00                   ; Alguna condición
MOV     #1 D1200                ; Action: Inicio (0x01)
MOV     #0 D1201                ; Machine: Shelter (0x00)
MOV     #0 D1202                ; Sin datos adicionales
CALL    SBR_LOG                 ; Llamar a subrutina
```

---

## 5. Códigos de Referencia

### 5.1 Action Codes (Byte alto de palabra alta)

| Código | Hex  | Descripción           |
|--------|------|-----------------------|
| 1      | 0x01 | Inicio operación      |
| 2      | 0x02 | Fin operación         |
| 3      | 0x03 | Reset contador        |
| 4      | 0x04 | Cambio configuración  |
| 5      | 0x05 | Cambio switch         |
| 6      | 0x06 | Error                 |
| 7      | 0x07 | Alarma                |
| 8      | 0x08 | Mantenimiento         |
| 9      | 0x09 | Inicio sistema        |
| 10     | 0x0A | Parada sistema        |

### 5.2 Machine Types (Byte bajo de palabra alta)

| Código | Hex  | Descripción      |
|--------|------|------------------|
| 0      | 0x00 | Shelter          |
| 1      | 0x01 | Door             |
| 2      | 0x02 | Platform         |
| 3      | 0x03 | Traffic Light    |

### 5.3 Ejemplos de Valores Hexadecimales Completos

| Acción                               | Hex        | Binario                            |
|--------------------------------------|------------|------------------------------------|
| Inicio Shelter                       | 0x01000000 | 00000001 00000000 00000000 00000000 |
| Fin Door                             | 0x02010000 | 00000010 00000001 00000000 00000000 |
| Reset Platform                       | 0x03020000 | 00000011 00000010 00000000 00000000 |
| Cambio switch 5 Traffic Light        | 0x05030005 | 00000101 00000011 00000000 00000101 |
| Error 31 en Shelter                  | 0x0600001F | 00000110 00000000 00000000 00011111 |
| Mantenimiento Door                   | 0x08010000 | 00001000 00000001 00000000 00000000 |

---

## 6. Verificación desde CX-Programmer

### 6.1 Escribir Datos Manualmente (para pruebas)

1. **Abrir Monitor de Memoria:**
   - `PLC` → `Work` → `Memory`
   - O presionar `Ctrl + W`

2. **Navegar a área DM:**
   - Seleccionar área: `DM`
   - Ir a dirección: `D500`

3. **Escribir valores de prueba:**

**Configuración del PLC:**
```
D500: 4350  (CP)
D501: 3145  (1E)
D502: 452D  (-E)
D503: 3330  (30)
D504: 4452  (DR)
D505: 4100  (A)
D506-D519: 0000

D520: 3139  (19)
D521: 322E  (2.)
D522: 3136  (16)
D523: 382E  (8.)
D524: 3235  (25)
D525: 302E  (0.)
D526: 3100  (1)
D527-D529: 0000

D530: 5045  (PE)
D531: 442D  (D-)
D532: 3230  (20)
D533: 3235  (25)
D534: 2D30  (-0)
D535: 3030  (00)
D536: 3100  (1)
D537-D539: 0000

D540: 1     (Muelle 1)
```

**Entrada de LOG de ejemplo:**
```
D600: 0000  (Timestamp parte alta - simulado)
D601: 003C  (Timestamp parte baja - 60 segundos)
D602: 0100  (Hex parte alta: 0x0100 = Inicio Shelter)
D603: 0000  (Hex parte baja: 0x0000)

D604: 0000  (Segunda entrada - timestamp)
D605: 0078  (120 segundos)
D606: 0201  (Fin Door)
D607: 0000
```

### 6.2 Monitorear Escritura desde Ladder

1. Poner PLC en modo **MONITOR**
2. Activar bits de prueba (W0.00, W0.01, etc.)
3. Observar que:
   - D1190 incrementa (puntero)
   - D1191 incrementa (contador total)
   - D600+ se llenan con datos

---

## 7. Programa Completo de Ejemplo

```ladder
;===========================================
; PROGRAMA DE EJEMPLO - SISTEMA DE LOG
;===========================================

;; SECCIÓN 1: INICIALIZACIÓN
;===========================================
LD      P_First_Cycle
; Inicializar configuración
MOV     #4350 D500              ; Modelo: CP1E-E30DRA
MOV     #3145 D501
MOV     #452D D502
MOV     #3330 D503
MOV     #4452 D504
MOV     #4100 D505

MOV     #3139 D520              ; IP: 192.168.250.1
MOV     #322E D521
MOV     #3136 D522
MOV     #382E D523
MOV     #3235 D524
MOV     #302E D525
MOV     #3100 D526

MOV     #5045 D530              ; Pedido: PED-2025-001
MOV     #442D D531
MOV     #3230 D532
MOV     #3235 D533
MOV     #2D30 D534
MOV     #3030 D535
MOV     #3100 D536

MOV     #1 D540                 ; Muelle 1

BSET    D600 600                ; Limpiar LOG
MOV     #0 D1190                ; Puntero = 0
MOV     #0 D1191                ; Contador = 0
MOV     #0 D1192                ; Timestamp = 0
MOV     #0 D1193

;; SECCIÓN 2: CONTADOR DE TIEMPO
;===========================================
LD      P_1_Second              ; Pulso cada segundo
INC     D1192                   ; Incrementar segundos
LD      CY
INC     D1193

;; SECCIÓN 3: REGISTRO DE LOG
;===========================================
LD      W100.00                 ; Flag de solicitud
MOV     D1190 D1198
MUL     D1198 #4
ADD     D1198 #600
MOVD    D1192 @D1198
ADD     D1198 #2
MOVD    D1195 @D1198
INC     D1190
LD      D1190 >= #150
MOV     #0 D1190
INC     D1191
RST     W100.00

;; SECCIÓN 4: EVENTOS DE EJEMPLO
;===========================================
; Inicio Shelter
LD      W0.00
AND NOT W100.00
SET     W100.00
MOV     #0100 D1195
MOV     #0000 D1196

; Fin Door
LD      W0.01
AND NOT W100.00
SET     W100.00
MOV     #0201 D1195
MOV     #0000 D1196

; Reset Platform
LD      W0.02
AND NOT W100.00
SET     W100.00
MOV     #0302 D1195
MOV     #0000 D1196

END
```

---

## 8. Opciones de Expansión (Memoria Disponible)

El PLC tiene **16,370 palabras en el área D**. El sistema actual usa solo **641 palabras (3.9%)**, dejando **15,729 palabras (96.1%) disponibles** para expansiones futuras.

### 8.1 Expandir a 500 Entradas de LOG

```ladder
;; Cambiar constantes en área de control
D1190: Puntero (0-499)
D1191: Contador total
LOG_ENTRIES_COUNT = 500
LOG_START = D600
LOG_END = D2599  ; 500 × 4 = 2000 palabras

;; Rung: Inicializar área extendida
LD      P_First_Cycle
BSET    D600 2000           ; Borrar 2000 palabras
MOV     #0 D1190
MOV     #0 D1191

;; Rung: Registro con límite actualizado
LD      D1190 >= #500       ; Comparar con 500 en lugar de 150
MOV     #0 D1190
```

### 8.2 LOG Separado por Máquina

Mantener logs independientes para cada tipo de máquina:

```ladder
;; Área de memoria:
;; D600-D999:   Shelter (100 entradas × 4)
;; D1000-D1399: Door (100 entradas × 4)
;; D1400-D1799: Platform (100 entradas × 4)
;; D1800-D2199: Traffic Light (100 entradas × 4)

;; Variables de control (una por máquina):
D1190: Puntero Shelter (0-99)
D1191: Puntero Door (0-99)
D1192: Puntero Platform (0-99)
D1193: Puntero Traffic Light (0-99)

;; Subrutina modificada: LOG_ACTION_BY_MACHINE
;; Entrada: D1200 = action, D1201 = machine, D1202 = data

LD      ALWAYS_ON

;; Determinar base y puntero según máquina
LD      D1201 = #0          ; Si es Shelter
MOV     #600 D1203          ; Base = D600
MOV     D1190 D1204         ; Puntero actual

LD      D1201 = #1          ; Si es Door
MOV     #1000 D1203         ; Base = D1000
MOV     D1191 D1204

LD      D1201 = #2          ; Si es Platform
MOV     #1400 D1203         ; Base = D1400
MOV     D1192 D1204

LD      D1201 = #3          ; Si es Traffic Light
MOV     #1800 D1203         ; Base = D1800
MOV     D1193 D1204

;; Calcular dirección: base + (puntero × 4)
MUL     D1204 #4
ADD     D1203 D1204         ; D1203 = dirección final

;; Escribir entrada
MOVD    D1195 @D1203        ; Timestamp
ADD     D1203 #2
MOVD    D1196 @D1203        ; Hex value

;; Incrementar puntero correspondiente
LD      D1201 = #0
INC     D1190
LD      D1190 >= #100
MOV     #0 D1190

LD      D1201 = #1
INC     D1191
LD      D1191 >= #100
MOV     #0 D1191

LD      D1201 = #2
INC     D1192
LD      D1192 >= #100
MOV     #0 D1192

LD      D1201 = #3
INC     D1193
LD      D1193 >= #100
MOV     #0 D1193

RET
```

### 8.3 Histórico de Eventos Críticos

Mantener un área permanente para eventos importantes:

```ladder
;; Área de memoria:
;; D600-D1199:  Log circular normal (150 entradas)
;; D2200-D3199: Histórico crítico (250 entradas, no circular)

;; Variables de control:
D1190: Puntero log normal (circular)
D1194: Puntero histórico crítico (no circular, hasta 250)

;; Rung: Decidir si es evento crítico
LD      W100.00             ; Solicitud de registro

;; Registrar en log normal (siempre)
CALL    SBR_LOG_NORMAL

;; Si es evento crítico, registrar también en histórico
LD      D1200 = #6          ; Error
OR      D1200 = #7          ; Alarma
OR      D1200 = #8          ; Mantenimiento
CALL    SBR_LOG_CRITICAL

;; Subrutina: LOG_CRITICAL
LD      D1194 < #250        ; Si no está lleno
MOV     D1194 D1198
MUL     D1198 #4
ADD     D1198 #2200         ; Base histórico crítico
MOVD    D1192 @D1198        ; Timestamp
ADD     D1198 #2
MOVD    D1195 @D1198        ; Hex value
INC     D1194               ; No circular, incrementa hasta 250
RET
```

### 8.4 Datos de Mantenimiento

Registrar información adicional de mantenimiento:

```ladder
;; Área de memoria:
;; D3000-D3099: Último mantenimiento (fecha, operador, componente)
;; D3100-D3199: Histórico de calibraciones
;; D3200-D3299: Contadores de ciclos por máquina

;; Registro de mantenimiento
;; D3000: Año (BCD)
;; D3001: Mes y día (BCD)
;; D3002: Hora y minuto (BCD)
;; D3003-D3012: ID del operador (20 chars)
;; D3013-D3022: Componente mantenido (20 chars)
;; D3023: Tipo de mantenimiento (1=preventivo, 2=correctivo)

;; Rung: Registrar mantenimiento
LD      W5.00               ; Botón de registro de mantenimiento
READ    A351 D3000          ; Año
READ    A352 D3001          ; Mes (parte alta)
READ    A353 D3001          ; Día (parte baja)
READ    A354 D3002          ; Hora (parte alta)
READ    A355 D3002          ; Minuto (parte baja)
; ... copiar datos de operador y componente
```

### 8.5 Configuración Extendida por Máquina

```ladder
;; Área de memoria:
;; D4000-D4099: Configuración Shelter
;; D4100-D4199: Configuración Door
;; D4200-D4299: Configuración Platform
;; D4300-D4399: Configuración Traffic Light

;; Estructura por máquina (100 palabras cada una):
;; +0 a +19:  Número de serie (40 chars ASCII)
;; +20 a +24: Parámetros de timers (5 valores)
;; +25 a +34: Estados de switches (10 valores)
;; +35 a +39: Contadores operacionales
;; +40 a +59: Reservado para expansión
```

## 9. Notas Importantes

1. **Timestamp Real vs Relativo:**
   - El ejemplo usa un contador relativo de segundos (más simple)
   - Para timestamp Unix real, se necesita conversión compleja de fecha/hora del PLC
   - La app Android funciona con ambos formatos

2. **Buffer Circular:**
   - El puntero vuelve a 0 cuando llega a 150
   - Las entradas antiguas se sobrescriben
   - El contador total (D1191) sigue incrementando

3. **Sincronización:**
   - El flag W100.00 evita registros simultáneos
   - Esperar a que W100.00 = 0 antes de solicitar nuevo registro

4. **Optimización:**
   - Para producción, usar subrutina reutilizable (SBR_LOG)
   - Centraliza la lógica de registro
   - Reduce duplicación de código

---

## 9. Prueba Completa

### Secuencia de Prueba:

1. **Descargar programa al PLC**
2. **Verificar inicialización** (en CX-Programmer):
   - Leer D500-D506: debe mostrar "CP1E-E30DRA"
   - Leer D540: debe ser 1
3. **Activar eventos de prueba**:
   - SET W0.00 (Inicio Shelter)
   - Esperar 1 segundo
   - SET W0.01 (Fin Door)
4. **Verificar LOG**:
   - Leer D600-D607: debe contener 2 entradas
   - Leer D1190: debe ser 2 (puntero)
   - Leer D1191: debe ser 2 (contador)
5. **Probar en app Android**:
   - Abrir "Registro de Actividad"
   - Presionar "Cargar LOG del PLC"
   - Verificar que aparecen las 2 entradas

---

## Soporte

Para más ayuda:
- X. Rodriguez (xarosu@gmail.com) o *issues* en GitHub
- Documentación: LOG_CONFIGURATION.md