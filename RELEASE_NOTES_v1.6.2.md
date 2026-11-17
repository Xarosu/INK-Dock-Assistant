# INK-Dock_assistant - Versión 1.6.2

**Fecha de lanzamiento**: 6 de Noviembre de 2025

## Novedades principales

### 1. Sistema de LOG circular completo
- Implementado sistema de registro circular de eventos con buffer de 150 entradas
- Cada entrada utiliza 5 palabras de memoria (timestamp + código hex + datos adicionales)
- Buffer ubicado en D600-D1349 (750 palabras totales)

### 2. Arquitectura de memoria actualizada
- **Áreas de activación de máquinas**:
  - D110: Abrigo (0=inactivo, 1=activo)
  - D120: Puerta (0=inactivo, 1=activo)
  - D130: Plataforma (valores 1-4 según modo)
  - D140: Semáforos (valores 0-5 según secuencia)

- **Switches movidos a área W**:
  - W0: Switches Abrigo (5 bits)
  - W1: Switches Puerta (5 bits)
  - W2: Switches Plataforma (7 bits)
  - W3: Switches Semáforos (5 bits)

- **Área de control LOG (solo PLC)**:
  - D0-D2: Control del buffer circular
  - H100-H102: Flags de control de eventos
  - W90-W99: Área de trabajo del sistema de LOG

### 3. Exportación de LOG mejorada
- Formato CSV estructurado por secciones
- Eliminación de acentos para mejor compatibilidad
- Secciones:
  1. Configuración del PLC
  2. Configuración del muelle
  3. Estadísticas de eventos
  4. Registro circular de eventos

### 4. Menú de navegación dinámico
- El menú lateral ahora muestra solo las máquinas activas
- Si una máquina se desactiva, su opción de configuración desaparece del menú
- Mejora en la experiencia de usuario

### 5. Documentación completa
- MEMORY_MAP.md: Mapa completo de áreas de memoria utilizadas
- Documentación de protocolo FINS
- Guías de desarrollo y arquitectura

## Mejoras técnicas

### Lectura de datos optimizada
- Lectura del buffer LOG en bloques de 50 palabras (15 bloques)
- Evita error FINS 4363 por exceso de datos
- Mejor manejo de errores y logs de diagnóstico

### Compatibilidad PLC
- Soporte para todos los modelos CP2E (N14, N20, N30, N40, N60)
- Adaptación automática según capacidades del PLC
- Documentación de entradas/salidas por modelo

## Archivos importantes

- **APK**: `INK-Dock_assistant_v1.6.2_release.apk` (6.1 MB)
- **Nombre de aplicación**: INK-Dock_assistant
- **Versión**: 1.6.2 (versionCode: 6)
- **Requisitos mínimos**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 34)

## Notas de instalación

1. El APK no está firmado digitalmente (unsigned)
2. Requiere habilitar "Instalar desde fuentes desconocidas"
3. Permisos necesarios:
   - Acceso a red
   - Acceso a almacenamiento (para exportar CSV)
   - Acceso USB (para comunicación serie, si aplica)

## Arquitectura de memoria PLC

### Áreas de lectura/escritura (App ↔ PLC)
- D500-D549: Configuración del PLC
- D1010-D1049: Números de serie (formato BCD)
- D100: Control de máquinas activas
- W0-W3: Switches de control

### Áreas de solo lectura (App ← PLC)
- D600-D1349: Buffer circular de LOG
- D0-D2: Control del registro circular
- H100-H102: Flags de eventos

## Cambios internos

### LogManager.kt
- Actualizado para leer buffer de 5 palabras por entrada
- Total: 750 palabras (150 × 5)
- Soporte para palabra adicional (reservada para uso futuro)

### CsvGenerator.kt
- Formato de exportación sin acentos
- Estructura por secciones mejorada
- Mejor legibilidad en Excel y editores de texto

### MainActivity.kt
- Observador de estado de máquinas activas
- Actualización dinámica del menú de navegación
- Mejor experiencia de usuario

## Problemas conocidos

- El APK no está firmado (unsigned), requiere firma manual para distribución
- Warning de versión SDK (se puede ignorar)

## Próximos pasos sugeridos

1. Firmar el APK para distribución
2. Implementar funcionalidad de lectura de eventos específicos usando D300-D414
3. Añadir interfaz para comandar flags H102 (leer último evento, borrar historial)
4. Probar comunicación con PLC real

## Contacto y soporte

Para reportar problemas o sugerencias, contactar al equipo de desarrollo.

---

**Compilado**: Noviembre 6, 2025
**Build**: Release
**Tamaño**: 6.1 MB