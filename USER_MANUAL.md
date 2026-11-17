# Manual de Usuario - INK-Dock Assistant

Guía completa para usuarios de la aplicación INK-Dock Assistant.

---

## Índice

1. [Introducción](#introducción)
2. [Instalación](#instalación)
3. [Primera Configuración](#primera-configuración)
4. [Pantallas de la Aplicación](#pantallas-de-la-aplicación)
5. [Tareas Comunes](#tareas-comunes)
6. [Solución de Problemas](#solución-de-problemas)
7. [Preguntas Frecuentes](#preguntas-frecuentes)

---

## Introducción

### ¿Qué es INK-Dock Assistant?

INK-Dock Assistant (en el dispositivo aparece como *INK-Dock_assistant*) es una aplicación Android para monitorizar y configurar muelles de carga equipados con PLC OMRON CP2E-N o con controladores ESP32. La aplicación permite:

- ✅ Conectar al PLC por Ethernet (adaptador USB-Ethernet o Wi-Fi)
- ✅ Conectar a una placa ESP32 por USB (solo Plataforma y Semáforos)
- ✅ Monitorizar el estado en tiempo real
- ✅ Configurar máquinas (Shelter, Door, Platform, Traffic Light)
- ✅ Registrar actividad del muelle
- ✅ Exportar logs a CSV, email o FTP

### Requisitos

- **Dispositivo**: Tablet o smartphone Android 7.0+ con USB OTG
- **Controlador**: PLC OMRON CP2E-N (N14/N20/N30/N40/N60) o placa ESP32 de plataforma (modelos P1, P2, P3, P4)
- **Conectividad**:
  - **Recomendado**: Adaptador USB-ETHERNET para conexión directa al PLC
  - **Alternativo**: Red Wi-Fi compartida con el PLC
- **Permisos**: Internet, almacenamiento (concedidos en instalación)

---

## Instalación

### Paso 1: Obtener la APK

La APK de INK-Dock Assistant se distribuye aparte del código fuente (no se incluye en el repositorio). Solicitarla a soporte o descargarla de la sección *Releases* del repositorio de GitHub cuando esté publicada.

### Paso 2: Habilitar Instalación de Orígenes Desconocidos

Si es la primera vez que instala una app fuera de Google Play:

1. Abrir **Ajustes** del dispositivo
2. Ir a **Seguridad** o **Aplicaciones**
3. Buscar **Orígenes desconocidos** o **Instalar apps desconocidas**
4. Habilitar para el navegador o gestor de archivos

### Paso 3: Instalar la APK

1. Abrir el archivo APK (por ejemplo **INK-Dock_assistant_v1.9.4.apk**)
2. Tocar **Instalar**
3. Esperar a que la instalación complete
4. Tocar **Abrir** para iniciar la app

### Paso 4: Conceder Permisos

Al abrir por primera vez, la app solicitará permisos:
- **Internet**: ✅ Necesario (comunicación con PLC)
- **Almacenamiento**: ✅ Necesario en Android antiguos (guardar archivos CSV)
- **USB**: se pide al conectar el adaptador o la placa ESP32

Tocar **Permitir** en todos los casos.

---

## Primera Configuración

### Conectar al PLC

#### 0. Preparar la Conectividad

**Método Recomendado - Adaptador USB-ETHERNET**:
1. Conectar el adaptador USB-ETHERNET al dispositivo Android
2. Conectar cable Ethernet del adaptador al puerto Ethernet del PLC
3. El sistema Android detectará automáticamente la conexión Ethernet
4. La conexión directa proporciona mayor estabilidad y velocidad

**Método Alternativo - Wi-Fi**:
1. Asegurar que el PLC tiene módulo Ethernet conectado a un router Wi-Fi
2. Conectar el dispositivo Android a la misma red Wi-Fi que el PLC
3. Verificar que ambos dispositivos están en el mismo rango de red

#### 1. Abrir la Aplicación

Al abrir la app, se muestra la pantalla **Home**.

#### 2. Ir a Configuración

- Abrir el **menú lateral** (icono ☰ arriba a la izquierda)
- Seleccionar **Configuración**

#### 3. Configurar Parámetros de Conexión

En la pantalla **Configuración**:

1. **Modelo**: elegir el PLC (CP2E-N14DR-A … CP2E-N60DR-A) o el controlador ESP32 (P1, P2, P3, P4)
2. **Tipo de conexión**: *Ethernet (PLC Omron)* o *USB (ESP32)*
3. **Ethernet**: introducir la IP del PLC (por defecto **192.168.250.1**) y tocar **Guardar IP**. La app usa FINS/TCP en el puerto 9600.
4. **USB**: tocar **Buscar dispositivos USB** y seleccionar la placa en la lista
5. Tocar **Probar conexión**: si todo va bien, el estado muestra el valor leído del PLC

> Con conexión directa al PLC, configure una IP estática en los ajustes de Ethernet de Android dentro de la misma red que el PLC.

#### 4. Guardar Configuración

- La IP y el modelo quedan guardados en el dispositivo para las próximas conexiones

#### 5. Volver a Home

- Tocar la flecha **←** para volver
- O usar el menú lateral → **Home**

#### 6. Conectar

1. En la pantalla **Home**, tocar el botón **Conectar**
2. Esperar mientras se establece la conexión
3. Cuando conecte, verá:
   - ✅ Estado: **Conectado**
   - ✅ Botón cambia a **Desconectar**

**Si la conexión falla**, ver [Solución de Problemas](#solución-de-problemas).

---

## Pantallas de la Aplicación

### 1. Home (Inicio)

**Función**: Pantalla principal para conectar/desconectar del PLC.

**Elementos**:
- **Estado de conexión**: Indica si está conectado o desconectado
- **Botón Conectar/Desconectar**: Control de conexión
- **Información del PLC**: Muestra IP y estado

**Uso**:
1. Tocar **Conectar** para establecer conexión
2. Esperar confirmación
3. Una vez conectado, acceder a otras funciones del menú

---

### 2. Sinóptico

**Función**: Leer y escribir direcciones de memoria del PLC directamente.

**Elementos**:
- **Área de memoria**: Seleccionar (DM, CIO, WR, HR, etc.)
- **Dirección**: Número de dirección de memoria
- **Valor**: Valor a leer/escribir
- **Botón Leer**: Lee el valor actual
- **Botón Escribir**: Escribe un nuevo valor

**Uso - Leer un Valor**:
1. Seleccionar **Área de memoria** (ej: DM)
2. Introducir **Dirección** (ej: 100)
3. Tocar **Leer**
4. El valor aparecerá en **Valor**

**Uso - Escribir un Valor**:
1. Seleccionar **Área de memoria** (ej: DM)
2. Introducir **Dirección** (ej: 100)
3. Introducir **Valor** (ej: 1234)
4. Tocar **Escribir**
5. Confirmar el mensaje de éxito

**Nota**: Esta pantalla es para usuarios avanzados. Para operación normal, usar las pantallas de configuración de máquinas.

---

### 3. Configuración de Muelle

**Función**: Configurar qué máquinas tiene el muelle y los datos de la instalación.

**Elementos**:
- **Abrigo** (switch): activo / inactivo
- **Puerta seccional** (switch): activa / inactiva
- **Plataforma** (lista): Ninguna, P1, P2, P3 o P4
- **Semáforos** (lista): Ninguno o la configuración de semáforos instalada
- **Número de pedido**: formato `1120005678/20` (10 dígitos de pedido / 2 de posición)
- **Número de muelle**: de 0 a 999
- Botones **Guardar** y **Resetear configuración**

**Uso**:
1. Activar las máquinas instaladas y elegir el modelo de plataforma y semáforos
2. Introducir pedido y número de muelle
3. Tocar **Guardar**: los datos se escriben en el PLC
4. Solo las máquinas activas aparecerán en el menú "Máquinas Activas"

**Resetear configuración** desactiva todas las máquinas y borra pedido y número de muelle en el PLC.

> En modo ESP32 solo se pueden configurar Plataforma y Semáforos.

---

### 4. Configuración de Máquinas

Submenu **"Máquinas Activas"** con 4 opciones:

#### a) Configuración Shelter

**Función**: Configurar el abrigo hinchable.

**Parámetros**:
- **Timer de Inflado** (segundos): Tiempo de inflado
- **Timer de Desinflado** (segundos): Tiempo de desinflado
- **Contador de Ciclos**: Número de ciclos realizados
- **Switches**:
  - Shelter Habilitado
  - Auto Mode
  - Emergency Stop

**Uso**:
1. **Modificar Timer**:
   - Introducir nuevo valor en segundos
   - Tocar **Guardar Timer**

2. **Reset Contador**:
   - Tocar **Reset** debajo del contador
   - Confirmar acción

3. **Cambiar Switch**:
   - Activar/desactivar el switch
   - El cambio se envía automáticamente al PLC

#### b) Configuración Door

**Función**: Configurar la puerta seccional.

**Parámetros**: Similar a Shelter
- Timer de Apertura
- Timer de Cierre
- Contador de Operaciones
- Switches (Door Enabled, Auto Mode)

#### c) Configuración Platform

**Función**: Configurar la plataforma niveladora.

**Parámetros**:
- Timer de Subida
- Timer de Bajada
- Contador de Movimientos
- Switches (Platform Enabled, Auto Level)

#### d) Configuración Traffic Light

**Función**: Configurar los semáforos.

**Parámetros**:
- Timer de Rojo
- Timer de Verde
- Contador de Cambios
- Switches (Lights Enabled, Auto Mode)

---

### 5. Registro de Actividad

**Función**: Ver, exportar y gestionar el log de actividad del muelle.

**Secciones**:

#### a) Información del PLC

Muestra:
- Modelo del PLC
- Dirección IP
- Número de Pedido/Posición
- Número de Muelle

#### b) Estadísticas

- Total de entradas registradas
- Máquinas activas
- Últimas 24 horas

#### c) Vista Previa del Log

Muestra las últimas 10 acciones registradas:
- Timestamp (fecha y hora)
- Descripción de la acción
- Código de evento (ver tabla de códigos en MEMORY_MAP.md)

#### d) Acciones

**Botón "Cargar LOG del PLC"**:
1. Tocar el botón
2. Esperar mientras se descargan los datos
3. Las secciones se llenarán con información

**Botón "Generar Archivo CSV"**:
1. Primero, cargar el LOG
2. Tocar **Generar Archivo CSV**
3. El archivo se guarda en: `/Android/data/.../files/logs/`
4. Nombre: `log_muelle_[N]_[fecha].csv`

**Botón "Enviar por Email"**:
1. Primero, generar el CSV
2. Tocar **Enviar por Email**
3. Aparece un formulario:
   - **Destinatario**: Introducir email
   - **Asunto**: Opcional (se genera automático)
4. Tocar **Confirmar**
5. Se abre el cliente de email con el archivo adjunto
6. Completar y enviar el email

**Botón "Subir a FTP"**:
1. Primero, generar el CSV
2. Tocar **Subir a FTP**
3. Aparece un formulario:
   - **Servidor FTP**: Dirección del servidor
   - **Puerto**: 21 (predeterminado)
   - **Usuario**: Usuario FTP
   - **Contraseña**: Contraseña FTP
   - **Ruta remota**: Carpeta en el servidor (ej: `/logs`)
4. (Opcional) Tocar **Probar Conexión** primero
5. Tocar **Confirmar** para subir
6. Esperar mensaje de éxito

---

### 6. Configuración

**Función**: Configurar parámetros de la aplicación y conexión PLC.

**Parámetros**:
- Modelo de PLC o ESP32
- Tipo de conexión (Ethernet / USB)
- IP del PLC (modo Ethernet)
- Dispositivo USB (modo ESP32)
- Prueba de conexión y lectura rápida de una dirección de memoria

Ver [Primera Configuración](#primera-configuración) para detalles.

---

### 7. Soporte

**Función**: Información de contacto y ayuda.

**Elementos**:
- Información de contacto
- Versión de la aplicación
- Selector de idioma: sistema, español, inglés o francés

---

## Tareas Comunes

### Conectar al PLC

1. Abrir la app
2. Ir a **Home**
3. Tocar **Conectar**
4. Esperar confirmación

### Configurar Timer de una Máquina

1. Conectar al PLC
2. Menú → **Máquinas Activas** → Seleccionar máquina (ej: Shelter)
3. En el campo **Timer**, introducir nuevo valor en segundos
4. Tocar **Guardar Timer**
5. Confirmar mensaje de éxito

### Resetear Contador

1. Conectar al PLC
2. Menú → **Máquinas Activas** → Seleccionar máquina
3. En la sección **Contador**, tocar **Reset**
4. El contador vuelve a 0
5. Confirmar mensaje de éxito

### Activar/Desactivar un Switch

1. Conectar al PLC
2. Menú → **Máquinas Activas** → Seleccionar máquina
3. En la sección **Configuración**, tocar el switch deseado
4. El estado se envía automáticamente al PLC
5. Ver feedback visual (color del switch)

### Exportar LOG por Email

1. Conectar al PLC
2. Menú → **Registro de Actividad**
3. Tocar **Cargar LOG del PLC**
4. Esperar a que cargue
5. Tocar **Generar Archivo CSV**
6. Tocar **Enviar por Email**
7. Introducir destinatario
8. Tocar **Confirmar**
9. Se abre cliente de email
10. Enviar el email

### Ver Última Actividad

1. Conectar al PLC
2. Menú → **Registro de Actividad**
3. Tocar **Cargar LOG del PLC**
4. Ver **Vista Previa del Log** (últimas 10 acciones)
5. Desplazar hacia abajo para ver todas

---

## Solución de Problemas

### No Puedo Conectar al PLC

**Síntoma**: Al tocar "Conectar", aparece error o queda en "Conectando..."

**Causas posibles**:

1. **Problema de conectividad física**
   - **Si usa USB-ETHERNET**:
     - Verificar que el adaptador USB-ETHERNET está correctamente conectado
     - Verificar que el cable Ethernet está enchufado al PLC
     - Algunos adaptadores requieren controladores adicionales en Android
     - Probar con otro cable Ethernet si está disponible
   - **Si usa Wi-Fi**:
     - Verificar que el dispositivo Android y el PLC están en la misma red Wi-Fi
     - Verificar que el router permite comunicación entre dispositivos (algunos routers tienen aislamiento de clientes)
     - Hacer ping al PLC desde otro dispositivo en la misma red

2. **IP incorrecta**
   - **Solución**: Verificar la IP del PLC
   - Menú → Configuración → Verificar **IP del PLC**
   - Cambiar si es necesario
   - **Con USB-ETHERNET**: La IP suele ser una IP estática del PLC (ej: 192.168.250.1)
   - **Con Wi-Fi**: Consultar la IP asignada por DHCP o configurar IP estática

3. **Puerto FINS distinto de 9600**
   - La app usa siempre el puerto FINS TCP 9600
   - Comprobar en CX-Programmer que el PLC escucha FINS/TCP en el puerto 9600

4. **FINS TCP no habilitado en PLC**
   - **Solución**: Verificar en CX-Programmer que FINS TCP/IP está habilitado
   - Contactar al técnico de PLC si es necesario

5. **Firewall bloqueando**
   - **Solución**: Verificar que no hay firewall bloqueando el puerto 9600

### No Detecta la Placa ESP32

**Síntoma**: La lista de dispositivos USB aparece vacía o la conexión USB falla.

**Soluciones**:
1. Usar un cable USB de datos (no solo de carga) y un adaptador OTG
2. Aceptar el permiso USB cuando Android lo solicite
3. Tocar **Buscar dispositivos USB** de nuevo con la placa conectada
4. Chips USB-serie soportados: CP2102/CP2104, CH340 y FTDI
5. Comprobar que el ESP32 tiene cargado el firmware de plataforma compatible (115200 baudios)

### La App Se Cierra Inesperadamente

**Síntoma**: La app se cierra al abrir una pantalla o realizar una acción

**Soluciones**:

1. **Reiniciar la app**
   - Cerrar completamente (Recientes → Deslizar)
   - Volver a abrir

2. **Limpiar caché**
   - Ajustes → Aplicaciones → INK-Dock Assistant → Almacenamiento → Limpiar caché

3. **Reinstalar la app**
   - Desinstalar
   - Reinstalar APK

4. **Contactar soporte**
   - Si persiste, contactar con: [email de soporte]
   - Incluir: Modelo de dispositivo, versión Android, acciones que causan el cierre

### Error al Cargar LOG

**Síntoma**: Al tocar "Cargar LOG del PLC", aparece error

**Causas posibles**:

1. **Áreas de memoria no configuradas**
   - **Error**: "Error FINS 4363"
   - **Solución**: El PLC debe tener las áreas D500-D1199 configuradas
   - Ver: [LOG_CONFIGURATION.md](LOG_CONFIGURATION.md)
   - Contactar al técnico de PLC

2. **Áreas vacías**
   - **Síntoma**: Carga correctamente pero no muestra datos
   - **Solución**: El PLC debe escribir datos en las áreas de memoria
   - Ver: [PLC_LADDER_EXAMPLES.md](PLC_LADDER_EXAMPLES.md)
   - Contactar al técnico de PLC

### No Puedo Enviar Email

**Síntoma**: Al tocar "Confirmar" en formulario de email, no pasa nada o error

**Soluciones**:

1. **No hay cliente de email instalado**
   - Instalar Gmail, Outlook u otro cliente de email

2. **Email mal formado**
   - Verificar formato: usuario@dominio.com

3. **Archivo no generado**
   - Asegurarse de haber tocado **Generar Archivo CSV** primero

### No Puedo Subir a FTP

**Síntoma**: Error al intentar subir archivo a FTP

**Causas posibles**:

1. **Credenciales incorrectas**
   - Verificar usuario y contraseña FTP

2. **Servidor FTP no accesible**
   - Tocar **Probar Conexión** primero
   - Verificar que el servidor está en línea

3. **Puerto incorrecto**
   - Puerto FTP predeterminado es 21
   - Si el servidor usa otro, especificarlo

4. **Permisos insuficientes**
   - El usuario FTP debe tener permisos de escritura en la carpeta destino

---

## Preguntas Frecuentes

### ¿Puedo usar la app sin conexión?

No, la app requiere conexión al PLC para funcionar. Todas las operaciones requieren comunicación activa con el PLC.

### ¿Los datos se guardan en el dispositivo?

Solo la configuración de conexión (IP, puerto) se guarda localmente. Los datos operacionales (timers, contadores, logs) se leen del PLC en tiempo real y no se almacenan permanentemente en el dispositivo.

### ¿Puedo conectar a múltiples PLCs?

La app puede conectarse a un PLC a la vez. Para cambiar de PLC:
1. Desconectar del PLC actual
2. Ir a Configuración
3. Cambiar la IP
4. Volver a Home y conectar

### ¿Qué significa "Error FINS 4363"?

Este error indica que el área de memoria que la app intenta leer no existe o no está configurada en el PLC. Solución: Configurar las áreas de memoria en el PLC (ver [LOG_CONFIGURATION.md](LOG_CONFIGURATION.md)).

### ¿Puedo modificar el programa del PLC desde la app?

No, la app solo lee y escribe valores en la memoria del PLC. No puede modificar el programa ladder del PLC. Para eso se necesita CX-Programmer de OMRON.

### ¿Cuántos registros de LOG se pueden almacenar?

El sistema almacena los últimos 300 eventos en el PLC (buffer circular). Cuando llega a 300, sobrescribe los más antiguos. Para conservar histórico completo, exportar regularmente a CSV.

### ¿Dónde se guardan los archivos CSV?

Los archivos CSV se guardan en:
```
/Android/data/com.example.ink_omron_viewer/files/logs/
```

Para acceder:
1. Usar un explorador de archivos
2. O conectar el dispositivo a PC vía USB

### ¿La app funciona offline una vez conectada?

No, la app requiere conexión activa todo el tiempo. Si se pierde la conexión, deberá reconectar manualmente.

### ¿Puedo usar la app en modo horizontal?

Sí, la app soporta orientación horizontal y vertical. La interfaz se adapta automáticamente.

### ¿Cómo actualizo la app?

Las actualizaciones se distribuyen mediante nuevas versiones de APK. Proceso:
1. Descargar nueva APK
2. Instalar sobre la versión existente (no desinstalar)
3. La configuración se conserva

---

## Contacto y Soporte

### Soporte Técnico

**X. Rodriguez**

- **Email**: xarosu@gmail.com
- **GitHub**: abrir un *issue* en el repositorio

### Información para Reportar Problemas

Al contactar soporte, incluir:
- Versión de la app (ver en Soporte)
- Modelo y versión de Android del dispositivo
- Descripción detallada del problema
- Pasos para reproducir
- Capturas de pantalla (si es posible)

---

## Apéndices

### A. Glosario de Términos

| Término | Significado |
|---------|-------------|
| **PLC** | Programmable Logic Controller - Controlador Lógico Programable |
| **FINS** | Factory Interface Network Service - Protocolo de comunicación OMRON |
| **TCP/IP** | Transmission Control Protocol/Internet Protocol |
| **DM** | Data Memory - Memoria de datos del PLC |
| **Timer** | Temporizador - Controla duración de operaciones |
| **Contador** | Registro de número de ciclos/operaciones |
| **Switch** | Interruptor de configuración (activado/desactivado) |
| **CSV** | Comma-Separated Values - Formato de archivo para datos tabulares |
| **FTP** | File Transfer Protocol - Protocolo de transferencia de archivos |

### B. Códigos de Error FINS

| Código | Hex    | Significado |
|--------|--------|-------------|
| 0 | 0x0000 | Operación exitosa |
| 257 | 0x0101 | Comando no soportado |
| 513 | 0x0201 | Área de memoria no accesible |
| 4363 | 0x110B | Formato de comando incorrecto / área no existe |

### C. Valores Predeterminados

| Parámetro | Valor Predeterminado |
|-----------|---------------------|
| Puerto FINS TCP | 9600 |
| IP del PLC | 192.168.250.1 |
| Velocidad USB (ESP32) | 115200 baudios |
| Puerto FTP | 21 |
| Carpeta FTP | /logs |
| Entradas LOG | 300 (máximo) |

---

**Última actualización**: Noviembre 2026
**Versión del documento**: 1.1
**Versión de la app**: 1.9.4