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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.Socket

/**
 * Exportador de logs por FTP
 * Implementación básica de cliente FTP para subir archivos
 */
class FtpExporter {

    companion object {
        private const val TAG = "FtpExporter"
        private const val BUFFER_SIZE = 8192
        private const val TIMEOUT_MS = 30000 // 30 segundos
    }

    /**
     * Sube un archivo al servidor FTP
     * @param file Archivo a subir
     * @param config Configuración del servidor FTP
     * @return Result con true si tuvo éxito, o el error en caso contrario
     */
    suspend fun uploadFile(file: File, config: FtpConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        var controlSocket: Socket? = null
        var dataSocket: Socket? = null

        try {
            // Conectar al servidor FTP
            controlSocket = Socket(config.host, config.port).apply {
                soTimeout = TIMEOUT_MS
            }

            val controlIn = controlSocket.getInputStream().bufferedReader()
            val controlOut = controlSocket.getOutputStream().bufferedWriter()

            // Leer respuesta de bienvenida
            var response = readResponse(controlIn)
            if (!response.startsWith("220")) {
                return@withContext Result.failure(Exception("Error en conexión FTP: $response"))
            }

            // Enviar usuario
            controlOut.write("USER ${config.username}\r\n")
            controlOut.flush()
            response = readResponse(controlIn)
            if (!response.startsWith("331")) {
                return@withContext Result.failure(Exception("Error en usuario FTP: $response"))
            }

            // Enviar contraseña
            controlOut.write("PASS ${config.password}\r\n")
            controlOut.flush()
            response = readResponse(controlIn)
            if (!response.startsWith("230")) {
                return@withContext Result.failure(Exception("Error en autenticación FTP: $response"))
            }

            // Cambiar a modo binario
            controlOut.write("TYPE I\r\n")
            controlOut.flush()
            response = readResponse(controlIn)
            if (!response.startsWith("200")) {
                Log.w(TAG, "Advertencia en modo binario: $response")
            }

            // Cambiar al directorio remoto
            if (config.remotePath.isNotEmpty()) {
                controlOut.write("CWD ${config.remotePath}\r\n")
                controlOut.flush()
                response = readResponse(controlIn)
                if (!response.startsWith("250")) {
                    Log.w(TAG, "Advertencia cambiando directorio: $response")
                }
            }

            // Entrar en modo pasivo
            controlOut.write("PASV\r\n")
            controlOut.flush()
            response = readResponse(controlIn)
            if (!response.startsWith("227")) {
                return@withContext Result.failure(Exception("Error en modo pasivo: $response"))
            }

            // Parsear respuesta PASV para obtener IP y puerto
            val pasvInfo = parsePasvResponse(response)
            if (pasvInfo == null) {
                return@withContext Result.failure(Exception("No se pudo parsear respuesta PASV"))
            }

            // Conectar al socket de datos
            dataSocket = Socket(pasvInfo.first, pasvInfo.second).apply {
                soTimeout = TIMEOUT_MS
            }

            // Enviar comando STOR
            val remoteFileName = file.name
            controlOut.write("STOR $remoteFileName\r\n")
            controlOut.flush()
            response = readResponse(controlIn)
            if (!response.startsWith("150") && !response.startsWith("125")) {
                return@withContext Result.failure(Exception("Error iniciando transferencia: $response"))
            }

            // Transferir el archivo
            val dataOut = BufferedOutputStream(dataSocket.getOutputStream())
            val fileIn = BufferedInputStream(FileInputStream(file))

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytes = 0L

            while (fileIn.read(buffer).also { bytesRead = it } != -1) {
                dataOut.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
            }

            dataOut.flush()
            fileIn.close()
            dataOut.close()
            dataSocket.close()

            Log.i(TAG, "Archivo transferido: $totalBytes bytes")

            // Leer respuesta final
            response = readResponse(controlIn)
            if (!response.startsWith("226")) {
                return@withContext Result.failure(Exception("Error finalizando transferencia: $response"))
            }

            // Desconectar
            controlOut.write("QUIT\r\n")
            controlOut.flush()
            readResponse(controlIn)

            controlOut.close()
            controlIn.close()
            controlSocket.close()

            Log.i(TAG, "Archivo subido exitosamente por FTP: ${file.name}")
            Result.success(true)

        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo archivo por FTP", e)
            Result.failure(e)
        } finally {
            try {
                dataSocket?.close()
                controlSocket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error cerrando conexiones FTP", e)
            }
        }
    }

    /**
     * Sube múltiples archivos al servidor FTP
     */
    suspend fun uploadFiles(files: List<File>, config: FtpConfig): Result<Int> = withContext(Dispatchers.IO) {
        var successCount = 0
        val errors = mutableListOf<String>()

        for (file in files) {
            val result = uploadFile(file, config)
            if (result.isSuccess) {
                successCount++
            } else {
                errors.add("${file.name}: ${result.exceptionOrNull()?.message}")
            }
        }

        if (errors.isEmpty()) {
            Result.success(successCount)
        } else {
            Result.failure(Exception("Errores al subir archivos:\n${errors.joinToString("\n")}"))
        }
    }

    /**
     * Lee una respuesta del servidor FTP
     */
    private fun readResponse(reader: java.io.BufferedReader): String {
        val response = StringBuilder()
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            response.append(line).append("\n")
            // Las respuestas FTP de una línea tienen formato "XXX mensaje"
            // Las respuestas multilínea tienen formato "XXX-mensaje" ... "XXX mensaje"
            if (line!!.length >= 4 && line!![3] == ' ') {
                break
            }
        }

        val result = response.toString().trim()
        Log.d(TAG, "FTP Response: $result")
        return result
    }

    /**
     * Parsea la respuesta del comando PASV para obtener IP y puerto
     * Formato: 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)
     */
    private fun parsePasvResponse(response: String): Pair<String, Int>? {
        try {
            val startIdx = response.indexOf('(')
            val endIdx = response.indexOf(')')
            if (startIdx == -1 || endIdx == -1) return null

            val numbers = response.substring(startIdx + 1, endIdx).split(',')
            if (numbers.size != 6) return null

            val ip = "${numbers[0]}.${numbers[1]}.${numbers[2]}.${numbers[3]}"
            val port = numbers[4].toInt() * 256 + numbers[5].toInt()

            Log.d(TAG, "PASV parsed: IP=$ip, Port=$port")
            return Pair(ip, port)

        } catch (e: Exception) {
            Log.e(TAG, "Error parseando respuesta PASV", e)
            return null
        }
    }

    /**
     * Prueba la conexión FTP
     */
    suspend fun testConnection(config: FtpConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        var socket: Socket? = null

        try {
            socket = Socket(config.host, config.port).apply {
                soTimeout = TIMEOUT_MS
            }

            val controlIn = socket.getInputStream().bufferedReader()
            val controlOut = socket.getOutputStream().bufferedWriter()

            // Leer bienvenida
            var response = readResponse(controlIn)
            if (!response.startsWith("220")) {
                return@withContext Result.failure(Exception("Error en conexión: $response"))
            }

            // Enviar usuario
            controlOut.write("USER ${config.username}\r\n")
            controlOut.flush()
            response = readResponse(controlIn)
            if (!response.startsWith("331")) {
                return@withContext Result.failure(Exception("Error en usuario: $response"))
            }

            // Enviar contraseña
            controlOut.write("PASS ${config.password}\r\n")
            controlOut.flush()
            response = readResponse(controlIn)
            if (!response.startsWith("230")) {
                return@withContext Result.failure(Exception("Error en autenticación: $response"))
            }

            // Desconectar
            controlOut.write("QUIT\r\n")
            controlOut.flush()

            controlOut.close()
            controlIn.close()
            socket.close()

            Log.i(TAG, "Conexión FTP exitosa")
            Result.success(true)

        } catch (e: Exception) {
            Log.e(TAG, "Error probando conexión FTP", e)
            Result.failure(e)
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error cerrando conexión de prueba", e)
            }
        }
    }
}