/*
 * INK OMRON Viewer - Monitorización y control de muelles de carga.
 * Copyright (C) 2026 X. Rodriguez <xarosu@gmail.com>
 *
 * Este programa es software libre: puedes redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General GNU publicada por la
 * Free Software Foundation, ya sea la versión 3 de la Licencia, o
 * cualquier versión posterior.
 *
 * Este programa se distribuye con la esperanza de que sea útil, pero
 * SIN GARANTÍA ALGUNA; ni siquiera la garantía implícita de
 * MERCANTIBILIDAD o APTITUD PARA UN PROPÓSITO DETERMINADO.
 * Consulte la Licencia Pública General GNU para más detalles.
 *
 * Debería haber recibido una copia de la Licencia Pública General GNU
 * junto con este programa. Si no es así, consulte <https://www.gnu.org/licenses/>.
 *
 */

package com.example.ink_omron_viewer.domain.log

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Configuración del PLC
 */
data class PlcConfiguration(
    val model: String,              // Modelo del PLC
    val ipAddress: String,          // Dirección IP
    val orderNumber: String,        // Número de pedido/posición
    val dockNumber: Int             // Número de muelle
)

/**
 * Configuración de una máquina individual
 */
data class MachineConfiguration(
    val type: String,               // Tipo: "Shelter", "Door", "Platform", "TrafficLight"
    val isActive: Boolean,          // Si está activada
    val serialNumber: String,       // Número de serie (12 dígitos)
    val timerValue: Short,          // Valor del temporizador
    val counterValues: List<Short>, // Valores de contadores (variable según tipo)
    val switchStates: List<Boolean> // Estados de los switches (variable según tipo)
)

/**
 * Configuración completa del muelle
 */
data class DockConfiguration(
    val machines: List<MachineConfiguration>
)

/**
 * Entrada de registro de actividad
 * El PLC almacena las acciones en formato hexadecimal de 8 caracteres
 */
data class LogEntry(
    val timestamp: String,          // Fecha y hora en formato "dd/MM/yy HH:mm:ss"
    val eventCode: Int,             // Código de evento numérico (ej: 100, 201, etc.)
    val description: String         // Descripción textual del evento
) {
    companion object {
    }

    fun getFormattedTimestamp(): String {
        // El timestamp ya viene formateado desde el LogManager
        return timestamp
    }
}

/**
 * Registro completo del sistema
 */
data class SystemLog(
    val generatedAt: Long,                      // Timestamp de generación del log
    val plcConfig: PlcConfiguration,            // Configuración del PLC
    val dockConfig: DockConfiguration,          // Configuración del muelle
    val logEntries: List<LogEntry>              // Últimas 300 acciones
) {
    fun getFormattedGeneratedAt(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(generatedAt))
    }
}

data class EmailConfig(
    val recipientEmail: String,
    val subject: String = "Log del Sistema - Muelle ${System.currentTimeMillis()}",
    val body: String = "Adjunto encontrará el registro de actividad del muelle de carga."
)

/**
 * Configuración para la exportación de archivos CSV.
 */
data class ExportConfig(
    val delimiter: String = ";" // Delimitador por defecto para CSV
)

/**
 * Configuración para la conexión a un servidor FTP.
 */
data class FtpConfig(
    val host: String,
    val port: Int = 21,
    val username: String,
    val password: String,
    val remotePath: String = "/" // Directorio remoto donde se subirá el archivo
)