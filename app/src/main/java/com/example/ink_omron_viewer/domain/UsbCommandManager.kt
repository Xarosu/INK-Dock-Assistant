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

import android.util.Log
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gestiona el envío de un comando y la espera de su respuesta específica por USB.
 */
class UsbCommandManager(
    private val tag: String,
    private val connection: UsbDeviceConnection,
    private val inEndpoint: UsbEndpoint,
    private val outEndpoint: UsbEndpoint
) {

    /**
     * Envía un comando Host Link y espera una respuesta que termine con '*\r'.
     *
     * @param commandBody El cuerpo del comando sin el delimitador final (ej. "@00RD00000001").
     * @param useFcs Si es `false`, no se calculará ni añadirá el FCS. Útil para comandos como IC.
     * @return La respuesta del PLC (sin el delimitador final) o null si hay timeout o error.
     */
    suspend fun sendAndReceive(commandBody: String, useFcs: Boolean = true): String? = withContext(Dispatchers.IO) {
        // 0. Las purgas de búfer FTDI se han desactivado. Pueden causar problemas si el chip no es FTDI.
        // connection.controlTransfer(0x40, 0, 1, 0, null, 0, 100) // Purge RX
        // connection.controlTransfer(0x40, 0, 2, 0, null, 0, 100) // Purge TX

        // 1. Construir el comando completo
        val commandString = if (useFcs) {
            val fcs = calculateFcs(commandBody)
            "$commandBody$fcs*\r"
        } else {
            "$commandBody*\r"
        }
        val commandBytes = commandString.toByteArray(Charsets.US_ASCII)

        Log.d(tag, "--> Sending bytes: ${commandBytes.toHexString()} (Command: '$commandString')")
        // 2. Enviar el comando completo en un solo paquete.
        // Es más eficiente y se alinea con el comportamiento estándar de transferencias bulk.
        val bytesSent = connection.bulkTransfer(outEndpoint, commandBytes, commandBytes.size, 500)
        if (bytesSent <= 0) {
            Log.e(tag, "Error al enviar el comando. Bytes enviados: $bytesSent")
            return@withContext null // Error al enviar
        }

        // 3. Pausa estratégica ANTES de leer. Da tiempo al PLC a procesar el comando.
        delay(50)

        // 4. Esperar la respuesta con un timeout general
        return@withContext withTimeoutOrNull(2000) {
            val buffer = ByteArray(inEndpoint.maxPacketSize)
            val responseBuilder = StringBuilder()
            var running = true

            while (running) {
                val bytesRead = connection.bulkTransfer(inEndpoint, buffer, buffer.size, 100)
                if (bytesRead > 0) {
                    val chunk = String(buffer, 0, bytesRead, Charsets.US_ASCII)
                    Log.d(tag, "<-- Received $bytesRead bytes: ${buffer.copyOf(bytesRead).toHexString()} (ASCII: '$chunk')")
                    responseBuilder.append(chunk)

                    if (responseBuilder.endsWith("*\r")) {
                        running = false
                    }
                } else {
                    // Pequeña pausa para no sobrecargar la CPU si no hay datos
                    delay(20)
                }
            }
            // Devolvemos la respuesta limpia (sin el terminador)
            responseBuilder.substring(0, responseBuilder.length - 2)
        }
    }

    private fun calculateFcs(command: String): String {
        var fcs = 0
        command.toByteArray(Charsets.US_ASCII).forEach { fcs = fcs xor it.toInt() }
        return String.format("%02X", fcs)
    }

    // Helper para ver los bytes en formato hexadecimal en el log
    private fun ByteArray.toHexString() = joinToString(" ") { "%02X".format(it) }
}