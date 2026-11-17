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

package com.example.ink_omron_viewer.domain

import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.net.Network
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "ESP32SerialClient"

/**
 * Cliente para comunicarse con un ESP32 vía USB Serial.
 *
 * Implementa la interfaz IFinsClient para mantener compatibilidad con el resto de la aplicación,
 * pero traduce las operaciones FINS a un protocolo serial simple que el ESP32 puede entender.
 *
 * Protocolo de Comunicación:
 * - El ESP32 almacena la configuración en NVS (Non-Volatile Storage)
 * - Se usa un protocolo de texto simple para compatibilidad con Host Link
 * - Formato de comando: @<CMD><AREA><ADDR><DATA>FCS*\r
 *   - CMD: RD (read) o WR (write)
 *   - AREA: área de memoria (DM, HR)
 *   - ADDR: dirección de 5 dígitos
 *   - DATA: datos (solo para escritura)
 *   - FCS: checksum XOR de 2 dígitos hex
 *
 * Mapeo de Memoria ESP32:
 * - D130-D138: Contadores y timers de plataforma (almacenados en NVS)
 * - D550: Índice de plataforma (1=P1, 2=P2, 3=P3, 4=P4)
 * - D551: Índice de semáforos
 * - H120-H125: Estados de interruptores de plataforma
 */
class ESP32SerialClient(
    private val context: Context,
    private val serialPort: UsbSerialPort
) : IFinsClient {

    private var connection: UsbDeviceConnection? = null
    private var isConnected = false
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    /**
     * Conecta con el ESP32 vía USB Serial.
     * @return true si la conexión fue exitosa
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val device = serialPort.driver.device

            // Verificar permisos USB
            if (!usbManager.hasPermission(device)) {
                Log.e(TAG, "No USB permission for device")
                return@withContext false
            }

            // Abrir conexión USB
            connection = usbManager.openDevice(device)
            if (connection == null) {
                Log.e(TAG, "Failed to open USB device")
                return@withContext false
            }

            // Configurar puerto serial
            serialPort.open(connection)
            serialPort.setParameters(
                115200,                         // Baud rate (DEBE coincidir con ESP32: 115200)
                8,                              // Data bits
                UsbSerialPort.STOPBITS_1,      // Stop bits
                UsbSerialPort.PARITY_NONE      // Parity
            )

            isConnected = true
            Log.i(TAG, "ESP32 connected via USB Serial at 115200 baud")

            // Pequeña pausa para estabilizar la conexión
            delay(100)

            // Verificar conectividad enviando comando de identificación
            val response = sendHostLinkCommand("@00IC", useFcs = false)
            if (response != null) {
                Log.i(TAG, "ESP32 identification: $response")
                return@withContext true
            } else {
                Log.w(TAG, "No response from ESP32 identification command")
                disconnect()
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to ESP32", e)
            disconnect()
            return@withContext false
        }
    }

    /**
     * Lee palabras (words) del ESP32.
     * Traduce la solicitud FINS a comandos Host Link que el ESP32 entiende.
     */
    override suspend fun readWords(
        memoryArea: Byte,
        address: Int,
        count: Int,
        network: Network?
    ): FinsResponse = withContext(Dispatchers.IO) {
        if (!isConnected || connection == null) {
            return@withContext FinsResponse.NetworkError(Exception("ESP32 not connected"))
        }

        try {
            val areaCode = memoryAreaToString(memoryArea)
            val addressStr = String.format("%05d", address)
            val countStr = String.format("%04d", count)

            // Comando Host Link: @00RD<área><dirección><cantidad>
            val commandBody = "@00RD$areaCode$addressStr$countStr"

            Log.d(TAG, "Reading $count words from $areaCode$addressStr")

            val response = sendHostLinkCommand(commandBody)
            if (response == null) {
                Log.e(TAG, "No response from ESP32 for read command")
                return@withContext FinsResponse.TimeoutError
            }

            // Parsear respuesta
            // Formato esperado: @00RD00<endCode><data>
            if (response.length < 8) {
                Log.e(TAG, "Response too short: $response")
                return@withContext FinsResponse.NetworkError(Exception("Invalid response format"))
            }

            val endCode = response.substring(6, 8).toIntOrNull(16) ?: 0xFF
            if (endCode != 0) {
                Log.w(TAG, "ESP32 returned error code: 0x${endCode.toString(16).uppercase()}")
                return@withContext FinsResponse.FinsError(endCode)
            }

            // Extraer datos (4 dígitos hex por palabra)
            val dataStr = response.substring(8)
            val data = parseHexData(dataStr, count)

            return@withContext FinsResponse.Success(data)

        } catch (e: Exception) {
            Log.e(TAG, "Read failed", e)
            return@withContext FinsResponse.NetworkError(e)
        }
    }

    /**
     * Escribe palabras (words) al ESP32.
     * Traduce la solicitud FINS a comandos Host Link que el ESP32 entiende.
     */
    override suspend fun writeWords(
        memoryArea: Byte,
        address: Int,
        data: ShortArray,
        network: Network?
    ): FinsResponse = withContext(Dispatchers.IO) {
        if (!isConnected || connection == null) {
            return@withContext FinsResponse.NetworkError(Exception("ESP32 not connected"))
        }

        try {
            val areaCode = memoryAreaToString(memoryArea)
            val addressStr = String.format("%05d", address)

            // Convertir datos a formato hex (4 dígitos por palabra)
            val dataStr = data.joinToString("") { String.format("%04X", it.toInt() and 0xFFFF) }

            // Comando Host Link: @00WR<área><dirección><datos>
            val commandBody = "@00WR$areaCode$addressStr$dataStr"

            Log.d(TAG, "Writing ${data.size} words to $areaCode$addressStr: $dataStr")

            val response = sendHostLinkCommand(commandBody)
            if (response == null) {
                Log.e(TAG, "No response from ESP32 for write command")
                return@withContext FinsResponse.TimeoutError
            }

            // Parsear respuesta
            if (response.length < 8) {
                Log.e(TAG, "Response too short: $response")
                return@withContext FinsResponse.NetworkError(Exception("Invalid response format"))
            }

            val endCode = response.substring(6, 8).toIntOrNull(16) ?: 0xFF
            if (endCode != 0) {
                Log.w(TAG, "ESP32 returned error code: 0x${endCode.toString(16).uppercase()}")
                return@withContext FinsResponse.FinsError(endCode)
            }

            return@withContext FinsResponse.Success(byteArrayOf())

        } catch (e: Exception) {
            Log.e(TAG, "Write failed", e)
            return@withContext FinsResponse.NetworkError(e)
        }
    }

    override fun disconnect() {
        try {
            serialPort.close()
        } catch (e: Exception) {
            Log.e(TAG, "Exception closing serial port", e)
        }
        connection?.close()
        connection = null
        isConnected = false
        Log.i(TAG, "ESP32 disconnected")
    }

    /**
     * Envía un comando Host Link al ESP32 y espera la respuesta.
     *
     * @param commandBody El cuerpo del comando sin el delimitador final (ej. "@00RD00000001")
     * @param useFcs Si es true, calcula y añade el FCS (checksum)
     * @return La respuesta del ESP32 (sin el delimitador final) o null si hay timeout
     */
    private suspend fun sendHostLinkCommand(commandBody: String, useFcs: Boolean = true): String? = withContext(Dispatchers.IO) {
        try {
            // Construir el comando completo
            val commandString = if (useFcs) {
                val fcs = calculateFcs(commandBody)
                "$commandBody$fcs*\r"
            } else {
                "$commandBody*\r"
            }
            val commandBytes = commandString.toByteArray(Charsets.US_ASCII)

            Log.d(TAG, "--> Sending: ${commandBytes.toHexString()} (Command: '$commandString')")

            // Enviar el comando
            try {
                serialPort.write(commandBytes, 500)
                Log.d(TAG, "Command sent successfully (${commandBytes.size} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command", e)
                return@withContext null
            }

            // Pausa estratégica antes de leer
            delay(50)

            // Esperar la respuesta con timeout
            return@withContext withTimeoutOrNull(2000) {
                val buffer = ByteArray(1024)
                val responseBuilder = StringBuilder()
                var running = true

                while (running) {
                    val bytesRead = serialPort.read(buffer, 100)
                    if (bytesRead > 0) {
                        val chunk = String(buffer, 0, bytesRead, Charsets.US_ASCII)
                        Log.d(TAG, "<-- Received $bytesRead bytes: ${buffer.copyOf(bytesRead).toHexString()} (ASCII: '$chunk')")
                        responseBuilder.append(chunk)

                        // Buscar el terminador de respuesta
                        if (responseBuilder.toString().endsWith("*\r")) {
                            running = false
                        }
                    } else {
                        // Pequeña pausa si no hay datos
                        delay(20)
                    }
                }
                // Devolver la respuesta sin el terminador
                responseBuilder.substring(0, responseBuilder.length - 2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in sendHostLinkCommand", e)
            return@withContext null
        }
    }

    /**
     * Calcula el Frame Check Sequence (FCS) para Host Link.
     * El FCS es un XOR de todos los bytes del comando.
     */
    private fun calculateFcs(command: String): String {
        var fcs = 0
        command.toByteArray(Charsets.US_ASCII).forEach { fcs = fcs xor it.toInt() }
        return String.format("%02X", fcs)
    }

    /**
     * Convierte el código de área de memoria FINS a string Host Link.
     */
    private fun memoryAreaToString(memoryArea: Byte): String {
        return when (memoryArea) {
            FinsMemoryArea.DM -> "DM"
            FinsMemoryArea.HR -> "HR"
            FinsMemoryArea.WR -> "WR"   // Work Relay (W150 para reset contadores)
            FinsMemoryArea.AR -> "AR"
            FinsMemoryArea.CIO -> "CI"  // Mapear CIO a "CI" para Host Link
            else -> "DM" // Por defecto DM
        }
    }

    /**
     * Parsea datos en formato hexadecimal a ByteArray.
     * Cada palabra son 4 dígitos hex (2 bytes).
     */
    private fun parseHexData(hexStr: String, wordCount: Int): ByteArray {
        val bytes = ByteArray(wordCount * 2)
        for (i in 0 until wordCount) {
            val offset = i * 4
            if (offset + 4 <= hexStr.length) {
                val wordHex = hexStr.substring(offset, offset + 4)
                val word = wordHex.toInt(16).toShort()
                bytes[i * 2] = (word.toInt() shr 8).toByte()
                bytes[i * 2 + 1] = (word.toInt() and 0xFF).toByte()
            }
        }
        return bytes
    }

    /**
     * Helper para ver los bytes en formato hexadecimal en el log.
     */
    private fun ByteArray.toHexString() = joinToString(" ") { "%02X".format(it) }

    companion object {
        /**
         * Busca dispositivos USB Serial conectados.
         * @return Lista de puertos USB Serial disponibles
         */
        fun findAvailableDevices(context: Context): List<UsbSerialPort> {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            return availableDrivers.flatMap { it.ports }
        }

        /**
         * Crea un cliente ESP32 para el primer dispositivo USB disponible.
         * @return ESP32SerialClient o null si no hay dispositivos
         */
        suspend fun createFromFirstAvailableDevice(context: Context): ESP32SerialClient? {
            val devices = findAvailableDevices(context)
            if (devices.isEmpty()) {
                Log.w(TAG, "No USB serial devices found")
                return null
            }

            val port = devices.first()
            Log.i(TAG, "Creating ESP32 client for device: ${port.driver.device.deviceName}")
            return ESP32SerialClient(context, port)
        }
    }
}
