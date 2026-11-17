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
 * Define los modos de conexión soportados por la aplicación.
 */
enum class ConnectionMode {
    /**
     * Conexión al PLC Omron mediante Ethernet usando protocolo FINS/TCP o FINS/UDP.
     * Soporta todas las funcionalidades: plataformas, semáforos, abrigos y puertas.
     */
    ETHERNET,

    /**
     * Conexión a un ESP32 mediante USB Serial.
     * El ESP32 actúa como controlador para plataformas y semáforos únicamente.
     * No controla abrigos ni puertas (funcionalidad reservada para PLCs).
     */
    USB_ESP32
}

/**
 * Estado de conexión actual.
 */
data class ConnectionState(
    val mode: ConnectionMode = ConnectionMode.ETHERNET,
    val isConnected: Boolean = false,
    val status: String = "Desconectado",
    val deviceInfo: String? = null
) {
    /**
     * Indica si el modo actual soporta abrigos y puertas.
     * Solo el modo ETHERNET (PLC) soporta estas funcionalidades.
     */
    fun supportsSheltersAndDoors(): Boolean = mode == ConnectionMode.ETHERNET
}
