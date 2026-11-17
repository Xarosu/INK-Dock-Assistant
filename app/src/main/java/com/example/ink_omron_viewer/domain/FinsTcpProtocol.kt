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

import java.nio.ByteBuffer
import java.nio.ByteOrder

object FinsTcpProtocol {

    fun buildHandshakeCommand(requestedClientNode: Int = 0): ByteArray {
        val buffer = ByteBuffer.allocate(20)
            .order(ByteOrder.BIG_ENDIAN)
        buffer.put("FINS".toByteArray(Charsets.US_ASCII))
        buffer.putInt(12) // Length
        buffer.putInt(0)  // Command (Handshake)
        buffer.putInt(0)  // Error Code
        buffer.putInt(requestedClientNode)  // Client Node Address (0 = auto-assign)
        return buffer.array()
    }

    fun parseHandshakeResponse(response: ByteArray): Pair<Int, Int> {
        val buffer = ByteBuffer.wrap(response).order(ByteOrder.BIG_ENDIAN)
        buffer.position(16) // Skip header and command info
        val clientNode = buffer.int
        val serverNode = buffer.int
        return Pair(clientNode, serverNode)
    }

    fun wrapCommand(finsCommandPayload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(16 + finsCommandPayload.size)
            .order(ByteOrder.BIG_ENDIAN)
        buffer.put("FINS".toByteArray(Charsets.US_ASCII))
        buffer.putInt(8 + finsCommandPayload.size) // Length of data after this field
        buffer.putInt(2) // Command: FINS Command Send
        buffer.putInt(0) // Error Code: No error
        buffer.put(finsCommandPayload)
        return buffer.array()
    }

    fun unwrapResponse(response: ByteArray): ByteArray {
        // The FINS response payload starts after the 16-byte FINS/TCP header.
        // The header is: FINS (4) + Length (4) + Command (4) + Error Code (4) = 16 bytes.
        return response.copyOfRange(16, response.size)
    }
}