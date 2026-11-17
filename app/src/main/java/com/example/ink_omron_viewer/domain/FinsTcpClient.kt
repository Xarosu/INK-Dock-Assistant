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

import android.net.Network
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "FinsTcpClient"

class FinsTcpClient(
    private val plcIpAddress: String,
    private val plcPort: Int = 9600,
    private val requestedClientNode: Int // The FINS node address this client wants
) : IFinsClient {

    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: DataInputStream? = null
    
    private var serverNodeAddress: Int? = null
    private var assignedClientNode: Int? = null // The node address the PLC actually assigned to us

    private var sid: Byte = 0

    private fun getNextSid(): Byte {
        sid = (sid + 1).toByte()
        return sid
    }

    suspend fun connect(network: Network?): Boolean = withContext(Dispatchers.IO) {
        try {
            val newSocket = Socket()
            network?.bindSocket(newSocket)
            newSocket.connect(InetSocketAddress(plcIpAddress, plcPort), 2000)
            socket = newSocket
            outputStream = DataOutputStream(newSocket.getOutputStream())
            inputStream = DataInputStream(newSocket.getInputStream())

            Log.i(TAG, "Socket connected to $plcIpAddress:$plcPort")

            return@withContext performHandshake()

        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            disconnect()
            return@withContext false
        }
    }

    private fun performHandshake(): Boolean {
        try {
            // 1. Send client node address to PLC
            val handshakeCmd = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                .put("FINS".toByteArray())       // Header (4 bytes)
                .putInt(8)                       // Length (4 bytes) = 4 (Command) + 4 (Client Node)
                .putInt(0)                       // Command: Node Address Send (4 bytes)
                .putInt(requestedClientNode)     // Client Node Address (4 bytes)
                .array()

            outputStream?.write(handshakeCmd)
            Log.d(TAG, "--> Sent handshake with requested client node $requestedClientNode")

            // 2. Receive PLC node address response
            // The response can be 16 bytes (error) or 24 bytes (success)
            val response = ByteArray(24) 
            val bytesRead = inputStream?.read(response) ?: 0
            Log.d(TAG, "<-- Raw handshake response (hex): ${response.joinToString(" ") { "%02X".format(it) }}")

            if (bytesRead < 16) {
                Log.e(TAG, "Handshake response too short ($bytesRead bytes). Expected at least 16.")
                return false
            }

            val responseBuffer = ByteBuffer.wrap(response).order(ByteOrder.BIG_ENDIAN)
            val finsHeader = ByteArray(4).apply { responseBuffer.get(this) }
            if (String(finsHeader, Charsets.US_ASCII) != "FINS") {
                Log.e(TAG, "Invalid FINS header in handshake response")
                return false
            }

            val length = responseBuffer.getInt()
            val command = responseBuffer.getInt()

            val errorCode = responseBuffer.getInt()
            if (errorCode != 0) {
                Log.e(TAG, "Handshake failed with FINS error code: 0x${errorCode.toString(16).uppercase()}")
                return false
            }

            // A successful handshake response has command code 1.
            // An error response has command code 0.
            if (command != 1) {
                Log.e(TAG, "Handshake failed. PLC responded with command code $command instead of 1.")
                return false
            }

            if (bytesRead < 24) {
                Log.e(TAG, "Handshake response too short for success ($bytesRead bytes). Expected 24.")
                return false
            }

            val assignedClientNode = responseBuffer.getInt()
            val receivedServerNode = responseBuffer.getInt()

            // IMPORTANT: Accept the node the PLC gives us.
            this.assignedClientNode = assignedClientNode
            this.serverNodeAddress = receivedServerNode

            Log.i(TAG, "<-- Handshake successful. PLC Node: $serverNodeAddress, Assigned Client Node: $assignedClientNode (Requested: $requestedClientNode)")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Exception during handshake", e)
            return false
        }
    }

    override suspend fun readWords(memoryArea: Byte, address: Int, count: Int, network: Network?): FinsResponse = withContext(Dispatchers.IO) {
        if (socket?.isConnected != true || serverNodeAddress == null || assignedClientNode == null) {
            return@withContext FinsResponse.NetworkError(Exception("Not connected or handshake not complete"))
        }

        try {
            val finsHeader = byteArrayOf(
                0x80.toByte(), // ICF
                0x00,          // RSV
                0x07,          // GCT
                0x00,          // DNA
                serverNodeAddress!!.toByte(), // DA1
                0x00,          // DA2
                0x00,          // SNA
                assignedClientNode!!.toByte(), // SA1 - Use the ASSIGNED node
                0x00,          // SA2
                getNextSid()   // SID
            )

            val finsCommand = byteArrayOf(
                0x01, 0x01, memoryArea,
                (address shr 8).toByte(), (address and 0xFF).toByte(), 0x00,
                (count shr 8).toByte(), (count and 0xFF).toByte()
            )

            val finsPayload = finsHeader + finsCommand
            val tcpFrame = ByteBuffer.allocate(12 + finsPayload.size).order(ByteOrder.BIG_ENDIAN)
                .put("FINS".toByteArray())
                .putInt(4 + finsPayload.size)
                .putInt(2) // FINS Command Send
                .put(finsPayload)
                .array()

            outputStream?.write(tcpFrame)
            Log.d(TAG, "--> Sent Read Command")

            val headerBytes = ByteArray(8)
            inputStream?.readFully(headerBytes)
            val headerBuffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN)
            val finsHeaderStr = ByteArray(4).apply { headerBuffer.get(this) }
            if (String(finsHeaderStr) != "FINS") {
                return@withContext FinsResponse.NetworkError(Exception("Invalid FINS header in response"))
            }
            val payloadLength = headerBuffer.getInt()

            val payloadBytes = ByteArray(payloadLength)
            inputStream?.readFully(payloadBytes)
            val responseBytes = headerBytes + payloadBytes
            Log.d(TAG, "<-- Raw read response (hex): ${responseBytes.joinToString(" ") { "%02X".format(it) }}")

            val responseBuffer = ByteBuffer.wrap(responseBytes).order(ByteOrder.BIG_ENDIAN)
            responseBuffer.position(8) // Skip FINS and Length
            responseBuffer.getInt() // Skip Command

            val finsResponsePayload = responseBytes.sliceArray(12 until responseBytes.size)
            if (finsResponsePayload.size < 14) {
                 return@withContext FinsResponse.NetworkError(Exception("FINS payload too short"))
            }

            val endCode = ((finsResponsePayload[12].toInt() and 0xFF) shl 8) or (finsResponsePayload[13].toInt() and 0xFF)
            if (endCode == 0) {
                return@withContext FinsResponse.Success(finsResponsePayload.drop(14).toByteArray())
            } else {
                Log.w(TAG, "FINS command returned error: 0x${endCode.toString(16).uppercase()}")
                return@withContext FinsResponse.FinsError(endCode)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Read failed", e)
            disconnect()
            val errorType = e.javaClass.simpleName
            val errorMessage = e.message ?: "No message"
            return@withContext FinsResponse.NetworkError(Exception("Read failed with $errorType: $errorMessage", e))
        }
    }

    override suspend fun writeWords(memoryArea: Byte, address: Int, data: ShortArray, network: Network?): FinsResponse = withContext(Dispatchers.IO) {
        if (socket?.isConnected != true || serverNodeAddress == null || assignedClientNode == null) {
            return@withContext FinsResponse.NetworkError(Exception("Not connected or handshake not complete"))
        }

        try {
            val finsHeader = byteArrayOf(
                0x80.toByte(), 0x00, 0x07, 0x00,
                serverNodeAddress!!.toByte(), 0x00, 0x00,
                assignedClientNode!!.toByte(), 0x00, getNextSid()
            )

            val dataBytes = ByteBuffer.allocate(data.size * 2).order(ByteOrder.BIG_ENDIAN).apply {
                data.forEach { putShort(it) }
            }.array()

            val finsCommand = byteArrayOf(
                0x01, 0x02, memoryArea,
                (address shr 8).toByte(), (address and 0xFF).toByte(), 0x00,
                (data.size shr 8).toByte(), (data.size and 0xFF).toByte()
            ) + dataBytes
            
            val finsPayload = finsHeader + finsCommand
            val tcpFrame = ByteBuffer.allocate(12 + finsPayload.size).order(ByteOrder.BIG_ENDIAN)
                .put("FINS".toByteArray())
                .putInt(4 + finsPayload.size)
                .putInt(2)
                .put(finsPayload)
                .array()

            outputStream?.write(tcpFrame)
            Log.d(TAG, "--> Sent Write Command")

            val headerBytes = ByteArray(8)
            inputStream?.readFully(headerBytes)
            val headerBuffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN)
            val finsHeaderStr = ByteArray(4).apply { headerBuffer.get(this) }
            if (String(finsHeaderStr) != "FINS") {
                return@withContext FinsResponse.NetworkError(Exception("Invalid FINS header in response"))
            }
            val payloadLength = headerBuffer.getInt()

            val payloadBytes = ByteArray(payloadLength)
            inputStream?.readFully(payloadBytes)
            val responseBytes = headerBytes + payloadBytes

            val responseBuffer = ByteBuffer.wrap(responseBytes).order(ByteOrder.BIG_ENDIAN)
            responseBuffer.position(8)
            responseBuffer.getInt()

            val finsResponsePayload = responseBytes.sliceArray(12 until responseBytes.size)
            if (finsResponsePayload.size < 14) {
                 return@withContext FinsResponse.NetworkError(Exception("FINS payload too short"))
            }

            val endCode = ((finsResponsePayload[12].toInt() and 0xFF) shl 8) or (finsResponsePayload[13].toInt() and 0xFF)
            if (endCode == 0) {
                return@withContext FinsResponse.Success(finsResponsePayload.drop(14).toByteArray())
            } else {
                Log.w(TAG, "FINS command returned error: 0x${endCode.toString(16).uppercase()}")
                return@withContext FinsResponse.FinsError(endCode)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Write failed", e)
            disconnect()
            val errorType = e.javaClass.simpleName
            val errorMessage = e.message ?: "No message"
            return@withContext FinsResponse.NetworkError(Exception("Write failed with $errorType: $errorMessage", e))
        }
    }

    override fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Exception on disconnect", e)
        }
        socket = null
        outputStream = null
        inputStream = null
        serverNodeAddress = null
        assignedClientNode = null
        Log.i(TAG, "Disconnected.")
    }
}