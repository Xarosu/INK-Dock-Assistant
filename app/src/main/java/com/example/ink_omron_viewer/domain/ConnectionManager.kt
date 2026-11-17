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

import android.content.Context
import android.net.Network
import android.util.Log
import com.example.ink_omron_viewer.ui.ConnectionType
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor centralizado de conexiones.
 * Proporciona un punto único para crear y gestionar clientes de comunicación
 * según el modo de conexión seleccionado.
 */
class ConnectionManager(private val context: Context) {

    private val TAG = "ConnectionManager"

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState = _connectionState.asStateFlow()

    private var currentClient: IFinsClient? = null

    /**
     * Crea un cliente FINS TCP para conectar a un PLC Omron vía Ethernet.
     *
     * @param ipAddress Dirección IP del PLC
     * @param port Puerto TCP (por defecto 9600)
     * @param clientNode Nodo FINS del cliente (0 = auto-asignación)
     * @return Cliente FINS TCP configurado
     */
    fun createEthernetClient(
        ipAddress: String,
        port: Int = 9600,
        clientNode: Int = 0
    ): FinsTcpClient {
        Log.i(TAG, "Creating Ethernet client for $ipAddress:$port (node: $clientNode)")

        disconnectCurrent()

        val client = FinsTcpClient(ipAddress, port, clientNode)
        currentClient = client

        _connectionState.value = ConnectionState(
            mode = ConnectionMode.ETHERNET,
            isConnected = false,
            status = "Cliente Ethernet creado",
            deviceInfo = "$ipAddress:$port"
        )

        return client
    }

    /**
     * Crea un cliente ESP32 para conectar vía USB Serial.
     *
     * @param serialPort Puerto USB Serial del ESP32
     * @return Cliente ESP32 configurado
     */
    fun createESP32Client(serialPort: UsbSerialPort): ESP32SerialClient {
        Log.i(TAG, "Creating ESP32 USB client for device: ${serialPort.driver.device.deviceName}")

        disconnectCurrent()

        val client = ESP32SerialClient(context, serialPort)
        currentClient = client

        _connectionState.value = ConnectionState(
            mode = ConnectionMode.USB_ESP32,
            isConnected = false,
            status = "Cliente ESP32 creado",
            deviceInfo = serialPort.driver.device.deviceName
        )

        return client
    }

    /**
     * Conecta el cliente actual.
     * El método de conexión depende del tipo de cliente.
     *
     * @param network Red a usar (solo para Ethernet, opcional)
     * @return true si la conexión fue exitosa
     */
    suspend fun connect(network: Network? = null): Boolean {
        val client = currentClient ?: run {
            Log.w(TAG, "No client created yet")
            _connectionState.value = _connectionState.value.copy(
                status = "Error: No hay cliente creado"
            )
            return false
        }

        return try {
            val success = when (client) {
                is FinsTcpClient -> {
                    Log.i(TAG, "Connecting Ethernet client...")
                    client.connect(network)
                }
                is ESP32SerialClient -> {
                    Log.i(TAG, "Connecting ESP32 client...")
                    client.connect()
                }
                else -> {
                    Log.e(TAG, "Unknown client type: ${client::class.simpleName}")
                    false
                }
            }

            if (success) {
                _connectionState.value = _connectionState.value.copy(
                    isConnected = true,
                    status = "Conectado"
                )
                Log.i(TAG, "Connection successful")
            } else {
                _connectionState.value = _connectionState.value.copy(
                    isConnected = false,
                    status = "Error de conexión"
                )
                Log.w(TAG, "Connection failed")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Exception during connection", e)
            _connectionState.value = _connectionState.value.copy(
                isConnected = false,
                status = "Error: ${e.message}"
            )
            false
        }
    }

    /**
     * Desconecta el cliente actual.
     */
    fun disconnect() {
        currentClient?.disconnect()
        _connectionState.value = _connectionState.value.copy(
            isConnected = false,
            status = "Desconectado"
        )
        Log.i(TAG, "Disconnected")
    }

    /**
     * Desconecta y limpia el cliente actual.
     */
    private fun disconnectCurrent() {
        currentClient?.let {
            it.disconnect()
            currentClient = null
            Log.i(TAG, "Previous client disconnected and cleared")
        }
    }

    /**
     * Obtiene el cliente actual.
     * @return Cliente FINS activo o null si no hay ninguno
     */
    fun getCurrentClient(): IFinsClient? = currentClient

    /**
     * Verifica si hay un cliente conectado actualmente.
     */
    fun isConnected(): Boolean = currentClient != null && _connectionState.value.isConnected

    /**
     * Obtiene el modo de conexión actual.
     */
    fun getCurrentMode(): ConnectionMode = _connectionState.value.mode

    /**
     * Verifica si el modo actual soporta abrigos y puertas.
     */
    fun supportsSheltersAndDoors(): Boolean = _connectionState.value.supportsSheltersAndDoors()

    /**
     * Libera todos los recursos al destruir el manager.
     */
    fun cleanup() {
        disconnectCurrent()
        Log.i(TAG, "ConnectionManager cleanup completed")
    }

    companion object {
        /**
         * Convierte ConnectionType (de SharedViewModel) a ConnectionMode.
         */
        fun connectionTypeToMode(type: ConnectionType): ConnectionMode {
            return when (type) {
                ConnectionType.ETHERNET -> ConnectionMode.ETHERNET
                ConnectionType.USB_ESP32 -> ConnectionMode.USB_ESP32
            }
        }

        /**
         * Convierte ConnectionMode a ConnectionType.
         */
        fun connectionModeToType(mode: ConnectionMode): ConnectionType {
            return when (mode) {
                ConnectionMode.ETHERNET -> ConnectionType.ETHERNET
                ConnectionMode.USB_ESP32 -> ConnectionType.USB_ESP32
            }
        }
    }
}
