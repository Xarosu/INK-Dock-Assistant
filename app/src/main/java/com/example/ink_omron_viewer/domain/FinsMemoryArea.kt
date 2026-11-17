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

object FinsMemoryArea {
    const val CIO: Byte = 0xB0.toByte()
    const val WR: Byte = 0xB1.toByte()
    const val HR: Byte = 0xB2.toByte()
    const val AR: Byte = 0xB3.toByte()
    const val DM: Byte = 0x82.toByte()
    fun toString(memoryArea: Byte): String {
        return when (memoryArea) {
            CIO -> "C"
            DM -> "D"
            HR -> "H"
            else -> throw IllegalArgumentException("Unknown FINS memory area: $memoryArea")
        }
    }
}
