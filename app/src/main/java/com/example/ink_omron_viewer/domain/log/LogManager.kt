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

import android.util.Log
import com.example.ink_omron_viewer.ui.SharedViewModel
import com.example.ink_omron_viewer.domain.DockConfigurationViewModel
import com.example.ink_omron_viewer.domain.FinsMemoryArea
import com.example.ink_omron_viewer.ui.CommunicationViewModel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Gestiona la lectura y el parseo de toda la información de log desde el PLC.
 */
class LogManager(
    private val commsViewModel: CommunicationViewModel,
    private val sharedViewModel: SharedViewModel,
    private val dockConfigViewModel: DockConfigurationViewModel
) {

    companion object {
        private const val TAG = "LogManager"
        // --- VALORES ACTUALIZADOS SEGÚN MEMORY_MAP.md ---
        private const val LOG_START_ADDRESS = 600
        private const val LOG_ENTRY_COUNT = 300 // Ahora 300 entradas
        private const val WORDS_PER_LOG_ENTRY = 5 // Ahora 5 palabras por entrada
        private const val MAX_WORDS_PER_READ = 50 // Límite para evitar error FINS 4363
    }

    /**
     * Orquesta la lectura de toda la información para generar un SystemLog completo.
     */
    suspend fun generateSystemLog(): Result<SystemLog> {
        // NUEVA ESTRATEGIA: La configuración se obtiene de los ViewModels, no se lee del PLC.
        // Esto asegura consistencia y evita lecturas innecesarias.
        return try {
            // El PLC Config ahora viene del SharedViewModel y DockConfigViewModel
            val plcConfigResult = buildPlcConfigurationFromViewModels()
            if (plcConfigResult.isFailure) return Result.failure(plcConfigResult.exceptionOrNull()!!)

            val dockConfigResult = readDockConfiguration()
            if (dockConfigResult.isFailure) return Result.failure(dockConfigResult.exceptionOrNull()!!)

            val logEntriesResult = readLogEntries()
            if (logEntriesResult.isFailure) return Result.failure(logEntriesResult.exceptionOrNull()!!)

            val systemLog = SystemLog(
                plcConfig = plcConfigResult.getOrThrow(),
                dockConfig = dockConfigResult.getOrThrow(),
                logEntries = logEntriesResult.getOrThrow(),
                generatedAt = System.currentTimeMillis()
            )
            Result.success(systemLog)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate system log", e)
            Result.failure(e)
        }
    }

    /**
     * CONSTRUYE la configuración del PLC a partir de los datos en los ViewModels.
     * Ya no lee esta información del PLC para la pantalla de logs.
     */
    private fun buildPlcConfigurationFromViewModels(): Result<PlcConfiguration> {
        return try {
            val model = sharedViewModel.selectedPlcModel.value?.name ?: "No definido"
            val ipAddress = sharedViewModel.plcIpAddress.value ?: "No definida"
            val orderNumber = dockConfigViewModel.orderNumber.value ?: ""
            val dockNumber = dockConfigViewModel.dockPosition.value?.toIntOrNull() ?: 0

            val config = PlcConfiguration(model, ipAddress, orderNumber, dockNumber)
            Result.success(config)
        } catch (e: Exception) {
            Log.e(TAG, "Error building PlcConfiguration from ViewModels", e)
            Result.failure(e)
        }
    }

    /**
     * Lee la configuración del muelle (máquinas activas y sus modos).
     */
    private suspend fun readDockConfiguration(): Result<DockConfiguration> {
        return try {
            val shelterConfig = readMachineConfig("Abrigo", 0, 110, 2100)
            val doorConfig = readMachineConfig("Puerta", 1, 120, 2110)
            val platformConfig = readMachineConfig("Plataforma", 550, 130, 2120) // HR address es ahora DM address
            val trafficLightConfig = readMachineConfig("Semáforos", 551, 140, 2130) // HR address es ahora DM address

            val machines = listOf(shelterConfig, doorConfig, platformConfig, trafficLightConfig)
            Result.success(DockConfiguration(machines))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lee la configuración completa de una máquina específica.
     */
    private suspend fun readMachineConfig(type: String, hrAddress: Int, dmBaseAddress: Int, serialAddress: Int): MachineConfiguration {
        val isActive: Boolean = if (type == "Abrigo" || type == "Puerta") {
            // Lógica para Abrigo y Puerta: leer un bit del área HR
            val activeResult = commsViewModel.readWords(FinsMemoryArea.HR.toInt(), hrAddress, 1)
            activeResult.isSuccess && (activeResult.getOrThrow()[0].toInt() and 1) != 0
        } else {
            // Lógica para Plataforma y Semáforos: leer el índice de selección de DM
            // hrAddress aquí contiene la dirección DM (550 o 551)
            val activeResult = commsViewModel.readWords(FinsMemoryArea.DM.toInt(), hrAddress, 1)
            // Se considera activo si el índice seleccionado es > 0 ("Ninguna" es 0)
            activeResult.isSuccess && activeResult.getOrThrow()[0].toInt() > 0
        }

        // Leer temporizador y contadores (4 palabras)
        val timerCountersResult = commsViewModel.readWords(FinsMemoryArea.DM.toInt(), dmBaseAddress, 4)
        val timerValue = timerCountersResult.getOrNull()?.getOrNull(0) ?: 0.toShort()
        val counterValues = timerCountersResult.getOrNull()?.slice(1..3) ?: listOf(0.toShort(), 0.toShort(), 0.toShort())

        // Leer switches
        val switchesResult = if (type == "Abrigo" || type == "Puerta") commsViewModel.readWords(FinsMemoryArea.HR.toInt(), hrAddress, 1) else Result.success(shortArrayOf(0))
        val switchStates = if (switchesResult.isSuccess) {
            val wordValue = switchesResult.getOrThrow()[0].toInt()
            List(16) { i -> (wordValue shr i) and 1 == 1 }
        } else {
            List(16) { false }
        }

        // Leer número de serie
        val serialResult = commsViewModel.readWords(FinsMemoryArea.DM.toInt(), serialAddress, 3)
        val serialNumber = if (serialResult.isSuccess) {
            val serialValue = commsViewModel.bcdToDecimal(serialResult.getOrThrow().sliceArray(0..2))
            if (serialValue > 0) serialValue.toString().padStart(9, '0') else "N/A"
        } else {
            "Error"
        }

        return MachineConfiguration(
            type = type,
            isActive = isActive,
            serialNumber = serialNumber,
            timerValue = timerValue,
            counterValues = counterValues,
            switchStates = switchStates
        )
    }

    /**
     * Lee las entradas del log desde el buffer circular del PLC.
     */
    suspend fun readLogEntries(): Result<List<LogEntry>> {
        return try {
            val allLogData = mutableListOf<Short>()
            val totalWordsToRead = LOG_ENTRY_COUNT * WORDS_PER_LOG_ENTRY
            var wordsRead = 0

            // Leer en bloques para evitar el error FINS 4363
            while (wordsRead < totalWordsToRead) {
                val wordsToReadInChunk = minOf(MAX_WORDS_PER_READ, totalWordsToRead - wordsRead)
                val currentAddress = LOG_START_ADDRESS + wordsRead

                val result = commsViewModel.readWords(FinsMemoryArea.DM.toInt(), currentAddress, wordsToReadInChunk)
                if (result.isSuccess) {
                    allLogData.addAll(result.getOrThrow().toList())
                    wordsRead += wordsToReadInChunk
                } else {
                    // Si un bloque falla, devolvemos el error inmediatamente
                    return Result.failure(result.exceptionOrNull() ?: Exception("Unknown error during chunked read"))
                }
            }

            Log.i(TAG, "Successfully read all ${allLogData.size} log words in chunks.")

            val entries = mutableListOf<LogEntry>()
            for (i in 0 until LOG_ENTRY_COUNT) {
                val offset = i * WORDS_PER_LOG_ENTRY
                if (offset + WORDS_PER_LOG_ENTRY > allLogData.size) break // Evitar OutOfBounds

                // --- NUEVA LÓGICA DE PARSEO DE FECHA/HORA BCD ---
                // Palabra 0: Año y Mes (YYMM)
                // Palabra 1: Día y Hora (DDHH)
                // Palabra 2: Minuto y Segundo (MMSS)
                val yymm = allLogData.getOrNull(offset) ?: 0
                val ddhh = allLogData.getOrNull(offset + 1) ?: 0
                val mmss = allLogData.getOrNull(offset + 2) ?: 0

                // Solo procesar entradas con datos válidos (al menos la fecha/hora no es cero)
                if (yymm != 0.toShort() || ddhh != 0.toShort() || mmss != 0.toShort()) {
                    val year = bcdToDecimal(yymm.toInt() shr 8)
                    val month = bcdToDecimal(yymm.toInt() and 0xFF)
                    val day = bcdToDecimal(ddhh.toInt() shr 8)
                    val hour = bcdToDecimal(ddhh.toInt() and 0xFF)
                    val min = bcdToDecimal(mmss.toInt() shr 8)
                    val sec = bcdToDecimal(mmss.toInt() and 0xFF)

                    val formattedTimestamp = String.format("%02d/%02d/%02d %02d:%02d:%02d", day, month, year, hour, min, sec)

                    // --- NUEVA LÓGICA DE CÓDIGO DE EVENTO ---
                    val eventCode = allLogData.getOrNull(offset + 4)?.toInt() ?: -1

                    entries.add(LogEntry(
                        timestamp = formattedTimestamp,
                        eventCode = eventCode,
                        description = "" // La descripción se genera en la UI (Adapter) y en el CSV.
                    ))
                }
            }
            // Ordenar por timestamp descendente para mostrar los más nuevos primero
            Result.success(entries.sortedByDescending { it.timestamp })
        } catch (e: Exception) {
            Log.e(TAG, "Error in readLogEntries logic", e)
            Result.failure(e)
        }
    }

    /**
     * Convierte un byte en formato BCD a un número decimal.
     * Ejemplo: 0x25 -> 25
     */
    private fun bcdToDecimal(bcd: Int): Int {
        // Asegurarse de que solo procesamos un byte
        val byte = bcd and 0xFF
        val highNibble = (byte shr 4) and 0x0F
        val lowNibble = byte and 0x0F
        if (highNibble > 9 || lowNibble > 9) {
            return -1 // Valor BCD inválido
        }
        return highNibble * 10 + lowNibble
    }

    /**
     * Parsea un array de Short (palabras) a un String ASCII, limpiando caracteres nulos.
     */
    private fun parseAsciiString(words: ShortArray): String {
        val byteBuffer = ByteBuffer.allocate(words.size * 2).order(ByteOrder.BIG_ENDIAN)
        words.forEach { byteBuffer.putShort(it) }
        // Limpiar caracteres nulos (\u0000) que el PLC usa para rellenar
        return String(byteBuffer.array(), StandardCharsets.US_ASCII).replace("\u0000", "").trim()
    }
}
