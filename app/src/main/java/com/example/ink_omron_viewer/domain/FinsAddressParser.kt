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

/**
 * Contiene el resultado de parsear una dirección de memoria FINS.
 * @param memoryArea El código del área de memoria (ej. DM, CIO).
 * @param address La dirección del canal o palabra.
 * @param bit El número de bit (0-15), si es una dirección de bit. Null si es una dirección de palabra.
 */
data class ParsedAddress(val memoryArea: Byte, val address: Int, val bit: Int? = null)

/**
 * Objeto para parsear strings de direcciones de memoria (ej. "D100", "CIO0.01")
 * a un formato que FinsUdpClient pueda entender.
 */
object FinsAddressParser {

    // Expresión regular para direcciones de bit, ej: CIO0.01, H50.15, d100.0
    private val BIT_ADDRESS_REGEX = "([A-Z_a-z]+)(\\d+)\\.(\\d{1,2})".toRegex(RegexOption.IGNORE_CASE)

    // Expresión regular para direcciones de palabra, ej: D100, CIO20, h5
    private val WORD_ADDRESS_REGEX = "([A-Z_a-z]+)(\\d+)".toRegex(RegexOption.IGNORE_CASE)

    fun parse(addressString: String): ParsedAddress? {
        // Primero intentamos con el formato de bit, que es más específico
        BIT_ADDRESS_REGEX.matchEntire(addressString.trim())?.let {
            val (areaStr, addrStr, bitStr) = it.destructured
            val memoryArea = getMemoryAreaCode(areaStr) ?: return null
            val address = addrStr.toIntOrNull() ?: return null
            val bit = bitStr.toIntOrNull() ?: return null
            if (bit !in 0..15) return null // El bit debe estar entre 0 y 15

            return ParsedAddress(memoryArea, address, bit)
        }

        // Si no, intentamos con el formato de palabra
        WORD_ADDRESS_REGEX.matchEntire(addressString.trim())?.let {
            val (areaStr, addrStr) = it.destructured
            val memoryArea = getMemoryAreaCode(areaStr) ?: return null
            val address = addrStr.toIntOrNull() ?: return null

            return ParsedAddress(memoryArea, address)
        }

        return null // Formato no reconocido
    }

    private fun getMemoryAreaCode(area: String): Byte? {
        return when (area.uppercase()) {
            "CIO", "C" -> FinsMemoryArea.CIO
            "DM", "D" -> FinsMemoryArea.DM
            "HR", "H" -> FinsMemoryArea.HR
            // Aquí se podrían añadir más áreas como 'W', 'A', etc.
            else -> null
        }
    }
}