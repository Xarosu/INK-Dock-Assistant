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

import com.example.ink_omron_viewer.domain.FinsMemoryArea

import android.net.Network
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Representa el resultado de una operación FINS.
 */
sealed class FinsResponse {
    data class Success(val data: ByteArray) : FinsResponse()
    data class FinsError(val endCode: Int) : FinsResponse()
    object TimeoutError : FinsResponse()
    data class NetworkError(val exception: Exception) : FinsResponse()
}

/**
 * Cliente para comunicarse con un PLC Omron usando el protocolo FINS sobre UDP.
 */
class FinsUdpClient(
    private val plcIpAddress: String,
    private val plcPort: Int = 9600
) : IFinsClient {

    private companion object {
        private const val TAG = "FinsUdpClient"
    }

    // Usamos un Int para evitar el desbordamiento de Byte (-128 a 127).
    // Lo mantendremos en el rango 0-255.
    private var sid: Byte = 0

    init {
        // Reiniciar SID para evitar colisiones si se recrea el cliente rápidamente.
        sid = (System.currentTimeMillis() % 256).toByte()
        Log.i(TAG, "Nuevo cliente FINS creado para IP: $plcIpAddress. SID reiniciado.")
    }

    private fun getNextSid(): Byte {
        sid = (sid + 1).toByte()
        return sid
    }



    override suspend fun readWords(memoryArea: Byte, address: Int, count: Int, network: Network?): FinsResponse {
        val destinationNode = plcIpAddress.substringAfterLast('.').toIntOrNull() ?: 0
        if (destinationNode == 0) {
            return FinsResponse.NetworkError(Exception("Invalid PLC IP for node address"))
        }

        // FINS Command Header (10 bytes)
        val header = byteArrayOf(
            0x80.toByte(), // ICF: Command
            0x00,          // RSV
            0x07,          // GCT: Gateway Count (Hop Limit)
            0x00,          // DNA: Destination Network Address (00 = local)
            destinationNode.toByte(), // DA1: Destination Node Address
            0x00,          // DA2: Destination Unit Address (00 = CPU)
            0x00,          // SNA: Source Network Address (00 = local)
            // TODO: El nodo de origen (SA1) debe ser el último octeto de la IP del dispositivo.
            // Se ha puesto 10 como ejemplo. La IP del PLC es 192.168.1.30.
            // Asumimos que la IP de este dispositivo es 192.168.1.10.
            10.toByte(),   // SA1: Source Node Address
            0x00,          // SA2: Source Unit Address (00 = CPU)
            getNextSid()   // SID: Service ID
        )

        // FINS Read Command (8 bytes)
        val command = byteArrayOf(
            0x01, // MRC: Memory Area Read
            0x01, // SRC
            memoryArea,
            (address shr 8).toByte(), // Address High
            (address and 0xFF).toByte(), // Address Low
            0x00, // Bit address (not used for words)
            (count shr 8).toByte(), // Count High
            (count and 0xFF).toByte()  // Count Low
        )

        val request = header + command
        return sendAndReceive(request, network)
    }

    override suspend fun writeWords(memoryArea: Byte, address: Int, data: ShortArray, network: Network?): FinsResponse {
        val destinationNode = plcIpAddress.substringAfterLast('.').toIntOrNull() ?: 0
        if (destinationNode == 0) {
            return FinsResponse.NetworkError(Exception("Invalid PLC IP for node address"))
        }

        // TODO: El nodo de origen (SA1) debe ser el último octeto de la IP del dispositivo.
        // Se ha puesto 10 como ejemplo. La IP del PLC es 192.168.1.30.
        // Asumimos que la IP de este dispositivo es 192.168.1.10.
        val sourceNode = 10
        val header = byteArrayOf(
            0x80.toByte(), 0x00, 0x07, 0x00, // GCT changed to 7
            destinationNode.toByte(), 0x00, 0x00,
            sourceNode.toByte(), // SA1: Source Node Address
            0x00,          // SA2: Source Unit Address (00 = CPU)
            getNextSid()
        )

        val dataBytes = ByteBuffer.allocate(data.size * 2).order(ByteOrder.BIG_ENDIAN).apply {
            data.forEach { putShort(it) }
        }.array()

        val command = byteArrayOf(
            0x01, 0x02, memoryArea,
            (address shr 8).toByte(),
            (address and 0xFF).toByte(),
            0x00,
            (data.size shr 8).toByte(),
            (data.size and 0xFF).toByte()
        ) + dataBytes

        val request = header + command
        return sendAndReceive(request, network)
    }

    override fun disconnect() {}

    /**
     * Envía un comando FINS y espera la respuesta.
     */
    private suspend fun sendAndReceive(request: ByteArray, network: Network?): FinsResponse = withContext(Dispatchers.IO) {
        try {
            val socket = DatagramSocket()
            // Vincular el socket a la red Ethernet si está disponible
            network?.let {
                it.bindSocket(socket)
                Log.d(TAG, "Socket vinculado a la red: $it")
            } ?: Log.d(TAG, "Socket vinculado a la red: default")


            val inetAddress = InetAddress.getByName(plcIpAddress)
            val sendPacket = DatagramPacket(request, request.size, inetAddress, plcPort)

            Log.d(TAG, "Destino: $plcIpAddress:$plcPort")
            Log.d(TAG, "--> Comando enviado (${request.size} bytes): ${request.toHexString()}")

            socket.send(sendPacket)
            Log.d(TAG, "Comando enviado. Esperando respuesta...")

            val responseBuffer = ByteArray(1024)
            val receivePacket = DatagramPacket(responseBuffer, responseBuffer.size)

            val response = withTimeoutOrNull(2000) {
                socket.receive(receivePacket)
                responseBuffer.copyOf(receivePacket.length)
            }

            socket.close()

            if (response == null) {
                Log.w(TAG, "Timeout esperando la respuesta FINS.")
                FinsResponse.TimeoutError
            } else {
                Log.d(TAG, "<-- Respuesta recibida (${response.size} bytes): ${response.toHexString()}")
                // FINS response header is 14 bytes (10 header + 4 command response)
                if (response.size < 14) {
                    Log.e(TAG, "Respuesta FINS inválida o demasiado corta.")
                    FinsResponse.NetworkError(Exception("Invalid FINS response length: ${response.size}"))
                } else {
                    val endCode = ((response[12].toInt() and 0xFF) shl 8) or (response[13].toInt() and 0xFF)
                    if (endCode == 0) {
                        FinsResponse.Success(response.drop(14).toByteArray())
                    } else {
                        Log.w(TAG, "Respuesta FINS con código de error: 0x${endCode.toString(16).uppercase()}")
                        FinsResponse.FinsError(endCode)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error de red en sendAndReceive", e)
            FinsResponse.NetworkError(e)
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(" ") { "%02X".format(it) }
    }
}