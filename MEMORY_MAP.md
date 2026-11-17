# MAPA DE MEMORIA - INK-Dock Assistant v1.9.4

Este documento describe las áreas de memoria que usa la aplicación (versión 1.9.4) para comunicarse por FINS con los PLC OMRON CP2E-N. El programa ladder del PLC debe usar estas mismas direcciones.

> **Fuente**: direcciones tomadas del código de la app v1.9.4 (ViewModels de `domain/` y `domain/log/LogManager.kt`). Si el programa ladder usa otras, manda el código de la app: hay que adaptar el ladder o cambiar las constantes en el código.

---

## RESUMEN RÁPIDO

| Dirección | Contenido | App |
|-----------|-----------|-----|
| H0.00 | Abrigo activo | Lee / escribe |
| H1.00 | Puerta activa | Lee / escribe |
| D550 | Plataforma seleccionada (0 = ninguna) | Lee / escribe |
| D551 | Semáforos seleccionados (0 = ninguno) | Lee / escribe |
| D110-D113 | Abrigo: temporizador y contadores | Lee / escribe temporizador |
| D120-D124 | Puerta: temporizador y contadores | Lee / escribe temporizador |
| D130-D138 | Plataforma: temporizadores y contadores | Lee / escribe temporizadores |
| D140-D143 | Semáforos: temporizador y contadores | Lee / escribe temporizador |
| D145 | Semáforos: switches | Lee / escribe |
| H120 | Abrigo: switches | Lee / escribe |
| H122 | Puerta: switches | Lee / escribe |
| H124 | Plataforma: switches | Lee / escribe |
| W150.00-W150.15 | Bits de reset de contadores | Pone a 1 |
| D500-D519 | Modelo del PLC (ASCII) | Lee / escribe |
| D520-D529 | IP del PLC (ASCII) | Lee / escribe |
| D530-D532 | Número de pedido (BCD) | Lee / escribe |
| D535 | Posición del pedido (BCD) | Lee / escribe |
| D540 | Número de muelle (BCD) | Lee / escribe |
| D600-D2099 | Buffer circular de eventos (300 × 5 palabras) | Solo lee |
| D2100-D2132 | Números de serie (BCD) | Lee / escribe |
| CIO 0-109 | E/S físicas para el sinóptico | Solo lee |

---

## ACTIVACIÓN DE MÁQUINAS

| Máquina | Dirección | Valores |
|---------|-----------|---------|
| Abrigo | **H0.00** | 0 = inactivo, 1 = activo |
| Puerta | **H1.00** | 0 = inactiva, 1 = activa |
| Plataforma | **D550** | 0 = ninguna; 1 = P1, 2 = P2, 3 = P3, 4 = P4 |
| Semáforos | **D551** | 0 = ninguno; >0 = configuración seleccionada |

- Escritura desde la pantalla *Configuración del muelle* (`DockConfigurationViewModel`).
- El menú lateral solo muestra las máquinas activas.
- En modo ESP32 se usan D550 y D551 con el mismo significado.

---

## AREA DE MEMORIA DE DATOS (DM)

### D0-D20: Control del Registro Circular (LOG) — uso interno del PLC

- **D0**: Puntero circular (0-299), próxima posición de escritura
- **D1**: Límite del buffer (#300)
- **D2**: Total de eventos registrados
- **D3-D10**: Cálculos internos temporales
- **D20**: Buffer temporal adicional

La app **no lee ni escribe** esta área.

---

### D110-D149: Temporizadores y Contadores de Máquinas

#### Abrigo (Shelter) - D110-D113
| Dirección | Contenido | App |
|-----------|-----------|-----|
| D110 | Temporizador | Lee / escribe |
| D111 | Horas desde mantenimiento | Lee (reset con W150.00) |
| D112 | Horas totales del soplador | Lee (reset con W150.01) |
| D113 | Reserva | Lee |

#### Puerta Seccional (Door) - D120-D124
| Dirección | Contenido | App |
|-----------|-----------|-----|
| D120 | Temporizador | Lee / escribe |
| D121 | Maniobras de subida | Lee (reset con W150.03) |
| D122 | Maniobras de bajada | Lee (reset con W150.04) |
| D123 | Horas desde mantenimiento | Lee (reset con W150.05) |
| D124 | Horas totales | Lee |

#### Plataforma (Platform) - D130-D138
| Dirección | Contenido | App |
|-----------|-----------|-----|
| D130 | Temporizador 1 | Lee / escribe |
| D131 | Temporizador 2 | Lee / escribe |
| D132 | Reserva | — |
| D133 | Pulsaciones de subida | Lee (reset con W150.06) |
| D134 | Maniobras sacar labio | Lee (reset con W150.07) |
| D135 | Maniobras recoger labio | Lee (reset con W150.08) |
| D136 | Activaciones de KM1 | Lee (reset con W150.09) |
| D137 | Horas desde mantenimiento | Lee (reset con W150.10) |
| D138 | Horas totales de la bomba | Lee |

#### Semáforos (Traffic Light) - D140-D145
| Dirección | Contenido | App |
|-----------|-----------|-----|
| D140 | Temporizador | Lee / escribe |
| D141-D143 | Contadores 1-3 | Lee (reset con W150.13-W150.15) |
| D145 | Switches de semáforos (un bit por switch) | Lee / escribe |

> El reparto de bits de reset sigue los comentarios del código: el contador *n* de cada máquina pone a 1 el bit `base + n` (Abrigo base 0, Puerta base 3, Plataforma base 6, Semáforos base 13). El ladder debe volver el bit a 0 tras ejecutar el reset.

---

### D500-D549: Configuración del PLC e Instalación

| Dirección | Contenido | Formato |
|-----------|-----------|---------|
| D500-D519 | Modelo del PLC (ej. "CP2E-N30DR-A") | ASCII, 2 caracteres por palabra (máx. 40) |
| D520-D529 | Dirección IP del PLC | ASCII, 2 caracteres por palabra (máx. 20) |
| D530-D532 | Número de pedido (10 dígitos) | BCD, 3 palabras |
| D533-D534 | Reserva | — |
| D535 | Posición del pedido (2 dígitos) | BCD, 1 palabra |
| D540 | Número de muelle (0-999) | BCD, 1 palabra |
| D541-D549 | Reserva | — |
| D550 | Plataforma seleccionada | Ver *Activación de máquinas* |
| D551 | Semáforos seleccionados | Ver *Activación de máquinas* |

- En la app el pedido se introduce como `1120005678/20` (pedido/posición).
- Al resetear la configuración del muelle la app pone a 0 H0.00, H1.00, D550, D551, D530-D534 y D540.

---

### D600-D2099: Buffer Circular de Eventos (LOG)

**300 entradas × 5 palabras = 1500 palabras.** La app lo lee en bloques de 50 palabras.

| Palabra | Contenido | Formato |
|---------|-----------|---------|
| 0 | Año y mes | BCD `YYMM` (ej. 0x2611 = nov. 2026) |
| 1 | Día y hora | BCD `DDHH` |
| 2 | Minuto y segundo | BCD `MMSS` |
| 3 | Reserva | — |
| 4 | Código de evento | Decimal (ver tabla de códigos) |

- Entrada *n*: D(600 + 5·n) a D(604 + 5·n). Entrada 0: D600-D604; entrada 299: D2095-D2099.
- Las entradas con palabras 0-2 a cero se consideran vacías y no se muestran.
- El PLC escribe secuencialmente usando el puntero D0 y vuelve a la entrada 0 al llegar a 300.

---

### D2100-D2149: Números de Serie (BCD)

| Dirección | Máquina | Formato |
|-----------|---------|---------|
| D2100-D2102 | Abrigo | 3 palabras BCD |
| D2110-D2112 | Puerta | 3 palabras BCD |
| D2120-D2122 | Plataforma | 3 palabras BCD |
| D2130-D2132 | Semáforos | 3 palabras BCD |
| D2140-D2149 | Reserva | — |

La app muestra el número de serie con 9 dígitos (relleno con ceros a la izquierda).

---

## AREA DE RETENCIÓN (HR)

### H0 y H1: Activación de Abrigo y Puerta
- **H0.00**: Abrigo activo
- **H1.00**: Puerta activa

### H120-H125: Switches de Máquinas
| Dirección | Máquina | App |
|-----------|---------|-----|
| H120 (H121 reserva) | Abrigo: un bit por switch | Lee / escribe la palabra |
| H122 (H123 reserva) | Puerta: un bit por switch | Lee / escribe la palabra |
| H124 (H125 reserva) | Plataforma: un bit por switch | Lee / escribe la palabra |

Los switches de Semáforos están en **D145** (área DM), no en HR.

Al estar en HR, los switches se conservan ante cortes de alimentación.

### H100-H109: Flags del Sistema de LOG — uso interno del PLC
- **H100.00-H100.15**: flags de flanco (DIFU) para generar eventos
- **H101.00**: guardar evento en el buffer circular
- **H102.00-H102.03**: reservados para comandos de lectura/borrado del historial

La versión 1.9.4 de la app **no usa** estos flags.

---

## AREA DE TRABAJO (WR)

### W150: Bits de Reset de Contadores
| Bits | Máquina |
|------|---------|
| W150.00-W150.02 | Abrigo |
| W150.03-W150.05 | Puerta |
| W150.06-W150.10 | Plataforma |
| W150.13-W150.15 | Semáforos |

La app pone el bit a 1; el ladder ejecuta el reset del contador y lo devuelve a 0.

---

## MODO ESP32

En modo USB ESP32 la app usa las mismas direcciones lógicas para Plataforma y Semáforos (D130-D138, D550, D551, H124…). El ESP32 las traduce a su almacenamiento interno (NVS). Abrigo y Puerta no están disponibles en este modo.

---

## AREAS DE E/S (CIO - Channel I/O)

### Entradas Digitales (I)
- **I0.00 - I1.15**: Entradas físicas del PLC
  - I0.00 - I0.15: Canal 0 (hasta 16 bits según modelo)
  - I1.00 - I1.15: Canal 1 (hasta 16 bits según modelo)
  - **Uso**: Lectura en tiempo real del estado de entradas en el sinóptico

### Salidas Digitales (Q)
- **Q100.00 - Q101.15**: Salidas físicas del PLC
  - Q100.00 - Q100.15: Canal 100 (hasta 16 bits según modelo)
  - Q101.00 - Q101.15: Canal 101 (hasta 16 bits según modelo)
  - **Uso**: Lectura en tiempo real del estado de salidas en el sinóptico

**Nota**: Las salidas usan CIO con offset +100 según convención OMRON

---

## TABLA DE CÓDIGOS DE EVENTOS

Mapeo de números decimales a eventos del sistema:

### Eventos del Abrigo (Shelter) - Códigos 100-199

| Código | Evento | Descripción |
|--------|--------|-------------|
| 100 | Inicio operación Abrigo | El abrigo inicia una operación |
| 101 | Fin operación Abrigo | El abrigo finaliza operación exitosamente |
| 102 | Activación Switch 1 Abrigo | Switch 1 activado |
| 103 | Desactivación Switch 1 Abrigo | Switch 1 desactivado |
| 104 | Activación Switch 2 Abrigo | Switch 2 activado |
| 105 | Desactivación Switch 2 Abrigo | Switch 2 desactivado |
| 106 | Activación Switch 3 Abrigo | Switch 3 activado |
| 107 | Desactivación Switch 3 Abrigo | Switch 3 desactivado |
| 108 | Activación Switch 4 Abrigo | Switch 4 activado |
| 109 | Desactivación Switch 4 Abrigo | Switch 4 desactivado |
| 110 | Activación Switch 5 Abrigo | Switch 5 activado |
| 111 | Desactivación Switch 5 Abrigo | Switch 5 desactivado |
| 120 | Error Abrigo | Error en operación del abrigo |
| 121 | Alarma Abrigo | Alarma activada en el abrigo |
| 122 | Reset contador Abrigo | Contador reseteado |
| 150 | Abrigo activado | La máquina Abrigo fue activada (H0.00=1) |
| 151 | Abrigo desactivado | La máquina Abrigo fue desactivada (H0.00=0) |

### Eventos de la Puerta (Door) - Códigos 200-299

| Código | Evento | Descripción |
|--------|--------|-------------|
| 200 | Inicio operación Puerta | La puerta inicia una operación |
| 201 | Fin operación Puerta | La puerta finaliza operación exitosamente |
| 202 | Activación Switch 1 Puerta | Switch 1 activado |
| 203 | Desactivación Switch 1 Puerta | Switch 1 desactivado |
| 204 | Activación Switch 2 Puerta | Switch 2 activado |
| 205 | Desactivación Switch 2 Puerta | Switch 2 desactivado |
| 206 | Activación Switch 3 Puerta | Switch 3 activado |
| 207 | Desactivación Switch 3 Puerta | Switch 3 desactivado |
| 208 | Activación Switch 4 Puerta | Switch 4 activado |
| 209 | Desactivación Switch 4 Puerta | Switch 4 desactivado |
| 210 | Activación Switch 5 Puerta | Switch 5 activado |
| 211 | Desactivación Switch 5 Puerta | Switch 5 desactivado |
| 220 | Error Puerta | Error en operación de la puerta |
| 221 | Alarma Puerta | Alarma activada en la puerta |
| 222 | Reset contador Puerta | Contador reseteado |
| 250 | Puerta activada | La máquina Puerta fue activada (H1.00=1) |
| 251 | Puerta desactivada | La máquina Puerta fue desactivada (H1.00=0) |

### Eventos de la Plataforma (Platform) - Códigos 300-399

| Código | Evento | Descripción |
|--------|--------|-------------|
| 300 | Inicio operación Plataforma | La plataforma inicia una operación |
| 301 | Fin operación Plataforma | La plataforma finaliza operación exitosamente |
| 302 | Activación Switch 1 Plataforma | Switch 1 activado |
| 303 | Desactivación Switch 1 Plataforma | Switch 1 desactivado |
| 304 | Activación Switch 2 Plataforma | Switch 2 activado |
| 305 | Desactivación Switch 2 Plataforma | Switch 2 desactivado |
| 306 | Activación Switch 3 Plataforma | Switch 3 activado |
| 307 | Desactivación Switch 3 Plataforma | Switch 3 desactivado |
| 308 | Activación Switch 4 Plataforma | Switch 4 activado |
| 309 | Desactivación Switch 4 Plataforma | Switch 4 desactivado |
| 310 | Activación Switch 5 Plataforma | Switch 5 activado |
| 311 | Desactivación Switch 5 Plataforma | Switch 5 desactivado |
| 312 | Activación Switch 6 Plataforma | Switch 6 activado |
| 313 | Desactivación Switch 6 Plataforma | Switch 6 desactivado |
| 314 | Activación Switch 7 Plataforma | Switch 7 activado |
| 315 | Desactivación Switch 7 Plataforma | Switch 7 desactivado |
| 320 | Error Plataforma | Error en operación de la plataforma |
| 321 | Alarma Plataforma | Alarma activada en la plataforma |
| 322 | Reset contador Plataforma | Contador reseteado |
| 350 | Plataforma modo 1 | Plataforma cambiada a modo 1 (D550=1, P1) |
| 351 | Plataforma modo 2 | Plataforma cambiada a modo 2 (D550=2, P2) |
| 352 | Plataforma modo 3 | Plataforma cambiada a modo 3 (D550=3, P3) |
| 353 | Plataforma modo 4 | Plataforma cambiada a modo 4 (D550=4, P4) |
| 354 | Plataforma desactivada | Plataforma desactivada (D550=0) |

### Eventos de Semáforos (Traffic Light) - Códigos 400-499

| Código | Evento | Descripción |
|--------|--------|-------------|
| 400 | Inicio operación Semáforos | Los semáforos inician una operación |
| 401 | Fin operación Semáforos | Los semáforos finalizan operación exitosamente |
| 402 | Activación Switch 1 Semáforos | Switch 1 activado |
| 403 | Desactivación Switch 1 Semáforos | Switch 1 desactivado |
| 404 | Activación Switch 2 Semáforos | Switch 2 activado |
| 405 | Desactivación Switch 2 Semáforos | Switch 2 desactivado |
| 406 | Activación Switch 3 Semáforos | Switch 3 activado |
| 407 | Desactivación Switch 3 Semáforos | Switch 3 desactivado |
| 408 | Activación Switch 4 Semáforos | Switch 4 activado |
| 409 | Desactivación Switch 4 Semáforos | Switch 4 desactivado |
| 410 | Activación Switch 5 Semáforos | Switch 5 activado |
| 411 | Desactivación Switch 5 Semáforos | Switch 5 desactivado |
| 420 | Error Semáforos | Error en operación de los semáforos |
| 421 | Alarma Semáforos | Alarma activada en los semáforos |
| 422 | Reset contador Semáforos | Contador reseteado |
| 450 | Semáforos secuencia 1 | Semáforos cambiados a secuencia 1 (D551=1) |
| 451 | Semáforos secuencia 2 | Semáforos cambiados a secuencia 2 (D551=2) |
| 452 | Semáforos secuencia 3 | Semáforos cambiados a secuencia 3 (D551=3) |
| 453 | Semáforos secuencia 4 | Semáforos cambiados a secuencia 4 (D551=4) |
| 454 | Semáforos secuencia 5 | Semáforos cambiados a secuencia 5 (D551=5) |
| 455 | Semáforos desactivados | Semáforos desactivados (D551=0) |

### Eventos del Sistema - Códigos 900-999

| Código | Evento | Descripción |
|--------|--------|-------------|
| 900 | Inicio sistema | El sistema PLC se inició |
| 901 | Parada sistema | El sistema PLC se detuvo |
| 902 | Reset general | Reset general del sistema |
| 903 | Cambio configuración | Cambio en configuración del sistema |
| 910 | Conexión Android | Dispositivo Android conectado |
| 911 | Desconexión Android | Dispositivo Android desconectado |
| 920 | Error comunicación | Error en comunicación FINS |
| 921 | Timeout comunicación | Timeout en comunicación |

---

## RESUMEN DE CAPACIDADES POR MODELO

### CP2E-N14DR-A
- Entradas: I0.00 - I0.07 (8 puntos)
- Salidas: Q100.00 - Q100.05 (6 puntos)

### CP2E-N20DR-A
- Entradas: I0.00 - I0.11 (12 puntos)
- Salidas: Q100.00 - Q100.07 (8 puntos)

### CP2E-N30DR-A
- Entradas: I0.00 - I0.11, I1.00 - I1.05 (18 puntos)
- Salidas: Q100.00 - Q100.07, Q101.00 - Q101.03 (12 puntos)

### CP2E-N40DR-A
- Entradas: I0.00 - I0.15, I1.00 - I1.07 (24 puntos)
- Salidas: Q100.00 - Q100.15 (16 puntos)

### CP2E-N60DR-A
- Entradas: I0.00 - I0.15, I1.00 - I1.15, I2.00 - I2.03 (36 puntos)
- Salidas: Q100.00 - Q100.15, Q101.00 - Q101.07 (24 puntos)

---

## NOTAS IMPORTANTES

1. **Texto**: los strings en DM se guardan en ASCII, 2 caracteres por palabra
2. **BCD**: números de serie, pedido, posición, número de muelle y fecha/hora del LOG
3. **Código de evento**: número decimal directo (16 bits)
4. **Endianness**: big-endian (byte alto primero), según FINS
5. **Límite de lectura**: máximo 50 palabras por comando FINS para evitar el error 4363
6. **LOG circular**: al llenarse sobrescribe las entradas más antiguas

### Discrepancias conocidas en el código v1.9.4
- `LogManager.readMachineConfig()` lee los switches de Abrigo y Puerta para el CSV desde **H0/H1** (palabras de activación), mientras que las pantallas de máquina usan **H120/H122**. El CSV puede mostrar estados de switch incorrectos.
- `LogManager` lee 4 palabras desde D120 y D130 para el CSV; la pantalla de Puerta usa 5 (D120-D124) y la de Plataforma 9 (D130-D138).

---

## ARCHIVOS RELACIONADOS

- `DockConfigurationViewModel.kt`: activación de máquinas, pedido, muelle, modelo e IP
- `ShelterViewModel.kt`, `DoorViewModel.kt`, `PlatformViewModel.kt`, `TrafficLightViewModel.kt`: temporizadores, contadores, switches, resets y números de serie
- `ConfigurationViewModel.kt`: lectura de modelo e IP (D500, D520)
- `log/LogManager.kt`: lectura del buffer de eventos
- `SynopticViewModel.kt`: lectura de CIO para E/S
- `CommunicationViewModel.kt`: lectura/escritura de bajo nivel
- `FinsMemoryArea.kt`: códigos de área FINS
- `ESP32SerialClient.kt`: traducción de direcciones en modo ESP32

---

**Última actualización**: Noviembre 2026 · App v1.9.4
