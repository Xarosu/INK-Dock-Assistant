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

/**
 * Interfaz que define las operaciones de comunicación FINS,
 * abstrayendo la implementación concreta (UDP, USB, etc.).
 */
interface IFinsClient {
    suspend fun readWords(memoryArea: Byte, address: Int, count: Int, network: Network? = null): FinsResponse
    suspend fun writeWords(memoryArea: Byte, address: Int, data: ShortArray, network: Network? = null): FinsResponse
    fun disconnect()
}