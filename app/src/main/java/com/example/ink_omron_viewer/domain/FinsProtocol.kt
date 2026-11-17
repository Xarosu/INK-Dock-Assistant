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

object FinsProtocol {

    // FINS Header Constants
    private const val ICF_COMMAND_RESPONSE_REQUIRED: Byte = 0x80.toByte()
    private const val GCT_DIRECT_TCP: Byte = 0x07.toByte() // Use 0x07 for direct TCP, not 0x02

    // FINS Command Code Constants
    private const val MRC_MEMORY_AREA_READ: Byte = 0x01
    private const val SRC_MEMORY_AREA_READ: Byte = 0x01
    private const val MRC_MEMORY_AREA_WRITE: Byte = 0x01
    private const val SRC_MEMORY_AREA_WRITE: Byte = 0x02

    fun buildReadCommand(memoryArea: Int, address: Int, itemCount: Int, clientNode: Int, serverNode: Int, sid: Byte): ByteArray {
        val command = ByteArray(18) // Reduced size, TCP wrapper adds the rest

        // FINS Header
        command[0] = ICF_COMMAND_RESPONSE_REQUIRED
        command[1] = 0x00.toByte() // RSV
        command[2] = GCT_DIRECT_TCP
        command[3] = 0x00.toByte() // DNA
        command[4] = serverNode.toByte() // DA1: PLC node
        command[5] = 0x00.toByte() // DA2
        command[6] = 0x00.toByte() // SNA
        command[7] = clientNode.toByte() // SA1: Client node
        command[8] = 0x00.toByte() // SA2
        command[9] = sid // SID

        // FINS Command
        command[10] = MRC_MEMORY_AREA_READ
        command[11] = SRC_MEMORY_AREA_READ

        // Memory Area
        command[12] = memoryArea.toByte()
        command[13] = (address shr 8).toByte()
        command[14] = address.toByte()
        command[15] = 0x00.toByte() // Bit address

        // Item Count
        command[16] = (itemCount shr 8).toByte()
        command[17] = itemCount.toByte()

        return command
    }

    fun buildWriteCommand(memoryArea: Int, address: Int, data: ByteArray, clientNode: Int, serverNode: Int, sid: Byte): ByteArray {
        val command = ByteArray(18 + data.size)

        // FINS Header
        command[0] = ICF_COMMAND_RESPONSE_REQUIRED
        command[1] = 0x00.toByte() // RSV
        command[2] = GCT_DIRECT_TCP
        command[3] = 0x00.toByte() // DNA
        command[4] = serverNode.toByte() // DA1: PLC node
        command[5] = 0x00.toByte() // DA2
        command[6] = 0x00.toByte() // SNA
        command[7] = clientNode.toByte() // SA1: Client node
        command[8] = 0x00.toByte() // SA2
        command[9] = sid // SID

        // FINS Command
        command[10] = MRC_MEMORY_AREA_WRITE
        command[11] = SRC_MEMORY_AREA_WRITE

        // Memory Area
        command[12] = memoryArea.toByte()
        command[13] = (address shr 8).toByte()
        command[14] = address.toByte()
        command[15] = 0x00.toByte() // Bit address

        // Item Count
        val itemCount = data.size / 2
        command[16] = (itemCount shr 8).toByte()
        command[17] = itemCount.toByte()

        // Data
        System.arraycopy(data, 0, command, 18, data.size)

        return command
    }
}