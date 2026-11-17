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

import android.content.Context
import android.content.res.Resources
import com.example.ink_omron_viewer.R
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generador de archivos CSV para los logs del sistema
 */
class CsvGenerator(private val context: Context) {

    companion object {
        private const val TAG = "CsvGenerator"
    }

    // Lazy initialization: El mapa se construye automáticamente desde los recursos
    // Solo se calcula una vez y se reutiliza para todas las entradas del log
    private val codeToIndexMap: Map<Int, Int> by lazy {
        val codes = context.resources.getIntArray(R.array.event_codes)
        codes.mapIndexed { index, code -> code to index }.toMap()
    }

    /**
     * Genera un archivo CSV con el log completo del sistema en formato estructurado por secciones
     * Similar a la visualización en pantalla: Configuración PLC, Configuración Muelle, Estadísticas, Registro
     * @return File con el CSV generado o null si hubo error
     */
    fun generateCsv(systemLog: SystemLog, config: ExportConfig = ExportConfig()): File? {
        try {
            // Crear directorio de logs si no existe
            val logsDir = File(context.getExternalFilesDir(null), "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }

            // Crear nombre de archivo con timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "log_muelle_${systemLog.plcConfig.dockNumber}_$timestamp.csv"
            val file = File(logsDir, fileName)

            OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8).use { writer ->
                val d = config.delimiter
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

                // ===== SECCION 1: CONFIGURACION DEL PLC =====
                writer.append("=== CONFIGURACION DEL PLC ===\n")
                writer.append("Campo${d}Valor\n")
                writer.append("Modelo${d}${systemLog.plcConfig.model}\n")
                writer.append("Direccion IP${d}${systemLog.plcConfig.ipAddress}\n")
                writer.append("Numero de Pedido${d}${systemLog.plcConfig.orderNumber}\n")
                writer.append("Numero de Muelle${d}${systemLog.plcConfig.dockNumber}\n")
                writer.append("\n")

                // ===== SECCION 2: CONFIGURACION DEL MUELLE =====
                writer.append("=== CONFIGURACION DEL MUELLE ===\n")
                writer.append("Maquinas Activas${d}${systemLog.dockConfig.machines.size}\n")
                writer.append("\n")

                // Para cada maquina activa, mostrar su configuracion
                for (machine in systemLog.dockConfig.machines) {
                    writer.append("--- ${machine.type} ---\n")
                    writer.append("Estado${d}${if (machine.isActive) "ACTIVA" else "INACTIVA"}\n")
                    writer.append("Numero de Serie${d}${machine.serialNumber}\n")
                    writer.append("Temporizador${d}${machine.timerValue}\n")

                    // Contadores
                    machine.counterValues.forEachIndexed { i, value ->
                        writer.append("Contador ${i+1}${d}${value}\n")
                    }

                    // Switches
                    machine.switchStates.forEachIndexed { i, state ->
                        writer.append("Switch ${i+1}${d}${if (state) "ON" else "OFF"}\n")
                    }
                    writer.append("\n")
                }

                // ===== SECCION 3: ESTADISTICAS =====
                writer.append("=== ESTADISTICAS ===\n")
                writer.append("Campo${d}Valor\n")
                writer.append("Total de Entradas${d}${systemLog.logEntries.size}\n")
                writer.append("Fecha de Generacion${d}${dateFormat.format(Date(systemLog.generatedAt))}\n")

                // Contar eventos por tipo de máquina basado en el rango del eventCode
                val machineEventCounts = mutableMapOf(
                    "Abrigo" to 0, "Puerta" to 0, "Plataforma" to 0, "Semáforos" to 0, "Sistema" to 0
                )
                systemLog.logEntries.forEach { entry ->
                    when (entry.eventCode) {
                        in 100..199 -> machineEventCounts["Abrigo"] = machineEventCounts["Abrigo"]!! + 1
                        in 200..299 -> machineEventCounts["Puerta"] = machineEventCounts["Puerta"]!! + 1
                        in 300..399 -> machineEventCounts["Plataforma"] = machineEventCounts["Plataforma"]!! + 1
                        in 400..499 -> machineEventCounts["Semáforos"] = machineEventCounts["Semáforos"]!! + 1
                        in 900..999 -> machineEventCounts["Sistema"] = machineEventCounts["Sistema"]!! + 1
                    }
                }
                machineEventCounts.forEach { (machine, count) ->
                    if (count > 0) writer.append("Eventos $machine${d}$count\n")
                }
                writer.append("\n")

                // ===== SECCION 4: REGISTRO CICLICO =====
                writer.append("=== REGISTRO CICLICO DE EVENTOS ===\n")
                writer.append("Fecha/Hora${d}Código Evento${d}Descripción\n")

                if (systemLog.logEntries.isEmpty()) {
                    writer.append("${d}${d}*** No hay entradas de log registradas ***\n")
                } else {
                    // Una fila por cada entrada de log (ordenadas por timestamp descendente)
                    for (entry in systemLog.logEntries) {
                        try {
                            writer.append("${entry.getFormattedTimestamp()}${d}")
                            writer.append("${entry.eventCode}${d}") // La descripción se genera aquí
                            writer.append("\"${getDescriptionForEventCode(context.resources, entry.eventCode)}\"\n")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing entry: ${e.message}", e)
                        }
                    }
                }
            }

            Log.i(TAG, "CSV generado exitosamente: ${file.absolutePath}")
            return file

        } catch (e: Exception) {
            Log.e(TAG, "Error generando CSV", e)
            return null
        }
    }

    private fun getDescriptionForEventCode(resources: Resources, code: Int): String {
        val descriptions = resources.getStringArray(R.array.event_descriptions)
        val index = codeToIndexMap[code] ?: -1
        return descriptions.getOrNull(index) ?: "Evento desconocido (Código: $code)"
    }

    /**
     * Función auxiliar para escribir los datos de una máquina en el CSV
     */
    private fun appendMachineData(writer: OutputStreamWriter, machine: MachineConfiguration?, delimiter: String) {
        if (machine != null) {
            // Estado activa
            writer.append("${if (machine.isActive) "SI" else "NO"}${delimiter}")
            // Número de serie
            writer.append("${machine.serialNumber}${delimiter}")
            // Timer
            writer.append("${machine.timerValue}${delimiter}")
            // Contadores
            machine.counterValues.forEach { counter ->
                writer.append("${counter}${delimiter}")
            }
            // Switches
            machine.switchStates.forEach { switch ->
                writer.append("${if (switch) "ON" else "OFF"}${delimiter}")
            }
        } else {
            // Máquina no activa - llenar con valores vacíos
            writer.append("NO${delimiter}${delimiter}${delimiter}") // Activa, SN, Timer
        }
    }

    /**
     * Genera un archivo CSV simplificado (solo log de acciones)
     */
    fun generateSimpleCsv(logEntries: List<LogEntry>, dockNumber: Int = 0): File? {
        try {
            val logsDir = File(context.getExternalFilesDir(null), "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "log_simple_${dockNumber}_$timestamp.csv"
            val file = File(logsDir, fileName)

            OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8).use { writer ->
                writer.append("Fecha/Hora,Código Evento,Descripción\n")

                for (entry in logEntries) {
                    writer.append("${entry.getFormattedTimestamp()},")
                    writer.append("${entry.eventCode},")
                    writer.append("\"${entry.description}\"\n")
                }
            }

            Log.i(TAG, "CSV simple generado: ${file.absolutePath}")
            return file

        } catch (e: Exception) {
            Log.e(TAG, "Error generando CSV simple", e)
            return null
        }
    }
}