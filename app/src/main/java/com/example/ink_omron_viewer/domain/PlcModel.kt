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

/**
 * Representa un canal de E/S en el PLC
 * @param channel Número de canal (ej: 0, 1, 2...)
 * @param bitCount Número de bits activos en este canal (máximo 16)
 */
data class IoChannel(
    val channel: Int,
    val bitCount: Int
)

/**
 * Representa un modelo de PLC con sus especificaciones de E/S.
 * @param name El nombre del modelo (ej. "CP2E-N20").
 * @param inputCount El número total de entradas físicas (legacy, para compatibilidad).
 * @param outputCount El número total de salidas físicas (legacy, para compatibilidad).
 * @param customInputChannels Lista de canales de entrada con sus bits activos
 * @param customOutputChannels Lista de canales de salida con sus bits activos
 */
data class PlcModel(
    val name: String,
    val inputCount: Int,
    val outputCount: Int,
    val customInputChannels: List<IoChannel> = emptyList(),
    val customOutputChannels: List<IoChannel> = emptyList()
) {
    /**
     * Obtiene los canales de entrada, usando customInputChannels si está definido,
     * o calculándolos desde inputCount si no
     */
    fun getInputChannels(): List<IoChannel> {
        return if (customInputChannels.isNotEmpty()) {
            customInputChannels
        } else {
            // Compatibilidad: calcular canales desde inputCount
            calculateChannelsFromCount(inputCount)
        }
    }

    /**
     * Obtiene los canales de salida, usando customOutputChannels si está definido,
     * o calculándolos desde outputCount si no
     */
    fun getOutputChannels(): List<IoChannel> {
        return if (customOutputChannels.isNotEmpty()) {
            customOutputChannels
        } else {
            // Compatibilidad: calcular canales desde outputCount
            calculateChannelsFromCount(outputCount)
        }
    }

    private fun calculateChannelsFromCount(totalPoints: Int): List<IoChannel> {
        val channels = mutableListOf<IoChannel>()
        var remaining = totalPoints
        var channelIndex = 0

        while (remaining > 0) {
            val bitsInChannel = minOf(16, remaining)
            channels.add(IoChannel(channelIndex, bitsInChannel))
            remaining -= bitsInChannel
            channelIndex++
        }

        return channels
    }
}

/**
 * Un repositorio simulado para obtener los modelos de PLC y ESP32 disponibles.
 */
object PlcRepository {
    private val models = listOf(
        // === ESP32 Platform Controllers ===
        PlcModel(
            name = "ESP32-P1",
            inputCount = 7,
            outputCount = 1,
            customInputChannels = listOf(
                IoChannel(0, 7)    // Canal 0: bits 0.00 a 0.06 (S1-S5, FC1, CS1)
            ),
            customOutputChannels = listOf(
                IoChannel(100, 1)  // Canal 100: bit 100.00 (KM1)
            )
        ),
        PlcModel(
            name = "ESP32-P2",
            inputCount = 7,
            outputCount = 3,
            customInputChannels = listOf(
                IoChannel(0, 7)    // Canal 0: bits 0.00 a 0.06 (S1-S5, FC1-FC3, CS1-CS4)
            ),
            customOutputChannels = listOf(
                IoChannel(100, 3)  // Canal 100: bits 100.00 a 100.02 (KM1, EV1, EV2)
            )
        ),
        PlcModel(
            name = "ESP32-P3",
            inputCount = 7,
            outputCount = 3,
            customInputChannels = listOf(
                IoChannel(0, 7)    // Canal 0: bits 0.00 a 0.06
            ),
            customOutputChannels = listOf(
                IoChannel(100, 3)  // Canal 100: bits 100.00 a 100.02
            )
        ),
        PlcModel(
            name = "ESP32-P4",
            inputCount = 7,
            outputCount = 1,
            customInputChannels = listOf(
                IoChannel(0, 7)    // Canal 0: bits 0.00 a 0.06
            ),
            customOutputChannels = listOf(
                IoChannel(100, 1)  // Canal 100: bit 100.00
            )
        ),

        // === Serie CP2E-N (NPT/NDT - Salidas NPN/PNP) ===
        PlcModel(
            name = "CP2E-N14DR-A",
            inputCount = 8,
            outputCount = 6,
            customInputChannels = listOf(
                IoChannel(0, 8)    // Canal 0: bits 0.00 a 0.07
            ),
            customOutputChannels = listOf(
                IoChannel(100, 6)  // Canal 100: bits 100.00 a 100.05
            )
        ),
        PlcModel(
            name = "CP2E-N20DR-A",
            inputCount = 12,
            outputCount = 8,
            customInputChannels = listOf(
                IoChannel(0, 12)   // Canal 0: bits 0.00 a 0.11
            ),
            customOutputChannels = listOf(
                IoChannel(100, 8)  // Canal 100: bits 100.00 a 100.07
            )
        ),
        PlcModel(
            name = "CP2E-N30DR-A",
            inputCount = 18,
            outputCount = 12,
            customInputChannels = listOf(
                IoChannel(0, 12),  // Canal 0: bits 0.00 a 0.11
                IoChannel(1, 6)    // Canal 1: bits 1.00 a 1.05
            ),
            customOutputChannels = listOf(
                IoChannel(100, 8),  // Canal 100: bits 100.00 a 100.07
                IoChannel(101, 4)   // Canal 101: bits 101.00 a 101.03
            )
        ),
        PlcModel(
            name = "CP2E-N40DR-A",
            inputCount = 24,
            outputCount = 16,
            customInputChannels = listOf(
                IoChannel(0, 16),  // Canal 0: bits 0.00 a 0.15
                IoChannel(1, 8)    // Canal 1: bits 1.00 a 1.07
            ),
            customOutputChannels = listOf(
                IoChannel(100, 16) // Canal 100: bits 100.00 a 100.15
            )
        ),
        PlcModel(
            name = "CP2E-N60DR-A",
            inputCount = 36,
            outputCount = 24,
            customInputChannels = listOf(
                IoChannel(0, 16),  // Canal 0: bits 0.00 a 0.15
                IoChannel(1, 16),  // Canal 1: bits 1.00 a 1.15
                IoChannel(2, 4)    // Canal 2: bits 2.00 a 2.03
            ),
            customOutputChannels = listOf(
                IoChannel(100, 16), // Canal 100: bits 100.00 a 100.15
                IoChannel(101, 8)   // Canal 101: bits 101.00 a 101.07
            )
        )
    )

    fun findByName(name: String): PlcModel? = models.find { it.name == name }

    fun getModels(): List<PlcModel> = models

    fun getDefault(): PlcModel? = models.firstOrNull()
}