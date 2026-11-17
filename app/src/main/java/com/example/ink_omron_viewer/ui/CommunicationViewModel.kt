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

package com.example.ink_omron_viewer.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ink_omron_viewer.domain.ConnectionManager
import com.example.ink_omron_viewer.domain.ESP32SerialClient
import com.example.ink_omron_viewer.domain.FinsTcpClient
import com.example.ink_omron_viewer.domain.FinsResponse
import com.example.ink_omron_viewer.domain.IFinsClient
import com.example.ink_omron_viewer.domain.FinsMemoryArea
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class HmiUiState(
    val ipAddress: String = "192.168.250.1",
    val port: String = "9600",
    val clientNode: Int = 0,
    val memoryAddress: String = "100",
    val valueToWrite: String = "0",
    val connectionStatus: String = "Disconnected",
    val result: String = "Result will be shown here"
)

/**
 * ViewModel de comunicación unificado que soporta múltiples modos de conexión.
 *
 * Utiliza ConnectionManager para abstraer la implementación específica del cliente
 * (Ethernet/FINS o USB/ESP32) y proporciona una interfaz consistente para el resto
 * de la aplicación.
 */
class CommunicationViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HmiUiState())
    val uiState = _uiState.asStateFlow()

    // ConnectionManager gestiona los clientes y el estado de conexión
    private val connectionManager = ConnectionManager(application)

    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var ethernetNetworkDeferred = CompletableDeferred<Network>()
    private var disconnectJob: Job? = null

    companion object {
        private const val TAG = "CommunicationViewModel"
    }

    fun onIpAddressChanged(ip: String) {
        _uiState.value = _uiState.value.copy(ipAddress = ip)
    }

    fun onPortChanged(port: String) {
        _uiState.value = _uiState.value.copy(port = port)
    }

    fun onClientNodeChanged(node: Int) {
        val validNode = node.coerceIn(0, 254)
        _uiState.value = _uiState.value.copy(clientNode = validNode)
    }

    fun onMemoryAddressChanged(address: String) {
        _uiState.value = _uiState.value.copy(memoryAddress = address)
    }

    fun onValueToWriteChanged(value: String) {
        _uiState.value = _uiState.value.copy(valueToWrite = value)
    }

    init {
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.i(TAG, "Red Ethernet encontrada y disponible: $network")
                disconnectJob?.cancel()
                if (!ethernetNetworkDeferred.isCompleted) {
                    ethernetNetworkDeferred.complete(network)
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.w(TAG, "Red Ethernet perdida: $network")
                disconnectJob = viewModelScope.launch {
                    delay(1000)
                    Log.w(TAG, "Disconnecting due to network loss after delay.")
                    if (ethernetNetworkDeferred.isCompleted) {
                        ethernetNetworkDeferred = CompletableDeferred()
                    }
                    _uiState.value = _uiState.value.copy(connectionStatus = "Disconnected (Network lost)")
                    disconnect()
                }
            }
        }
        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }

    /**
     * Conecta usando el modo especificado por connectionType.
     *
     * @param connectionType Modo de conexión (ETHERNET o USB_ESP32)
     * @param serialPort Puerto USB Serial (solo para USB_ESP32)
     */
    fun connect(connectionType: ConnectionType, serialPort: UsbSerialPort? = null) {
        viewModelScope.launch {
            disconnectJob?.cancel()

            try {
                when (connectionType) {
                    ConnectionType.ETHERNET -> connectEthernet()
                    ConnectionType.USB_ESP32 -> connectUSB(serialPort)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                _uiState.value = _uiState.value.copy(connectionStatus = "Error: ${e.message}")
                disconnect()
            }
        }
    }

    /**
     * Conecta vía Ethernet al PLC Omron usando FINS/TCP.
     */
    private suspend fun connectEthernet() {
        val ip = uiState.value.ipAddress
        val port = uiState.value.port.toInt()
        val clientNode = uiState.value.clientNode

        Log.i(TAG, "Attempting Ethernet connection to $ip:$port (node: $clientNode)")
        _uiState.value = _uiState.value.copy(connectionStatus = "Buscando red Ethernet...")

        // Esperar a que la red Ethernet esté disponible
        val ethernetNetwork = withTimeoutOrNull(3000) { ethernetNetworkDeferred.await() }
        if (ethernetNetwork == null) {
            throw Exception("No se encontró red Ethernet. Verifique el adaptador.")
        }

        _uiState.value = _uiState.value.copy(connectionStatus = "Red Ethernet encontrada. Conectando...")

        // Crear cliente Ethernet
        val client = connectionManager.createEthernetClient(ip, port, clientNode) as FinsTcpClient

        // Conectar
        val success = connectionManager.connect(ethernetNetwork)
        if (success) {
            _uiState.value = _uiState.value.copy(
                connectionStatus = "Connected via Ethernet (PLC Omron)"
            )
            Log.i(TAG, "Ethernet connection successful")
        } else {
            throw Exception("Failed to establish Ethernet connection")
        }
    }

    /**
     * Conecta vía USB Serial al ESP32.
     */
    private suspend fun connectUSB(serialPort: UsbSerialPort?) {
        if (serialPort == null) {
            throw Exception("No USB device selected. Please select an ESP32 device.")
        }

        Log.i(TAG, "Attempting USB connection to ESP32: ${serialPort.driver.device.deviceName}")
        _uiState.value = _uiState.value.copy(connectionStatus = "Conectando a ESP32...")

        // Crear cliente ESP32
        val client = connectionManager.createESP32Client(serialPort)

        // Conectar
        val success = connectionManager.connect()
        if (success) {
            _uiState.value = _uiState.value.copy(
                connectionStatus = "Connected via USB (ESP32)"
            )
            Log.i(TAG, "USB ESP32 connection successful")
        } else {
            throw Exception("Failed to establish USB connection with ESP32")
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            disconnectJob?.cancel()
            Log.i(TAG, "Disconnecting...")

            connectionManager.disconnect()

            if (ethernetNetworkDeferred.isCompleted) {
                ethernetNetworkDeferred = CompletableDeferred()
            }
            _uiState.value = _uiState.value.copy(connectionStatus = "Disconnected")
            Log.i(TAG, "Disconnected.")
        }
    }

    fun readMemory() {
        viewModelScope.launch {
            val client = connectionManager.getCurrentClient()
            if (client == null || !connectionManager.isConnected()) {
                _uiState.value = _uiState.value.copy(result = "Not connected")
                Log.w(TAG, "Read attempt while not connected.")
                return@launch
            }

            try {
                val address = uiState.value.memoryAddress.toIntOrNull() ?: 0
                Log.i(TAG, "Reading from DM address $address")

                val response = client.readWords(FinsMemoryArea.DM, address, 1)

                when (response) {
                    is FinsResponse.Success -> {
                        if (response.data.size >= 2) {
                            val value = ((response.data[0].toInt() and 0xFF) shl 8) or (response.data[1].toInt() and 0xFF)
                            Log.i(TAG, "Read successful. Value: $value")
                            _uiState.value = _uiState.value.copy(result = "Read value: $value")
                        } else {
                            _uiState.value = _uiState.value.copy(result = "Read response too short")
                        }
                    }
                    is FinsResponse.FinsError -> {
                        _uiState.value = _uiState.value.copy(result = "FINS Error: 0x${response.endCode.toString(16)}")
                    }
                    is FinsResponse.TimeoutError -> {
                        _uiState.value = _uiState.value.copy(result = "Timeout error")
                    }
                    is FinsResponse.NetworkError -> {
                        _uiState.value = _uiState.value.copy(result = "Network error: ${response.exception.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading memory", e)
                _uiState.value = _uiState.value.copy(result = "Error reading: ${e.message}")
            }
        }
    }

    fun writeMemory() {
        viewModelScope.launch {
            val client = connectionManager.getCurrentClient()
            if (client == null || !connectionManager.isConnected()) {
                _uiState.value = _uiState.value.copy(result = "Not connected")
                Log.w(TAG, "Write attempt while not connected.")
                return@launch
            }

            try {
                val address = uiState.value.memoryAddress.toIntOrNull() ?: 0
                val value = uiState.value.valueToWrite.toIntOrNull() ?: 0
                Log.i(TAG, "Writing value $value to DM address $address")

                val data = shortArrayOf(value.toShort())
                val response = client.writeWords(FinsMemoryArea.DM, address, data)

                when (response) {
                    is FinsResponse.Success -> {
                        Log.i(TAG, "Write successful.")
                        _uiState.value = _uiState.value.copy(result = "Write successful")
                    }
                    is FinsResponse.FinsError -> {
                        _uiState.value = _uiState.value.copy(result = "FINS Error: 0x${response.endCode.toString(16)}")
                    }
                    is FinsResponse.TimeoutError -> {
                        _uiState.value = _uiState.value.copy(result = "Timeout error")
                    }
                    is FinsResponse.NetworkError -> {
                        _uiState.value = _uiState.value.copy(result = "Network error: ${response.exception.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing memory", e)
                _uiState.value = _uiState.value.copy(result = "Error writing: ${e.message}")
            }
        }
    }

    /**
     * Escribe un array de palabras a una dirección de memoria específica.
     * Usado por otros ViewModels para escrituras genéricas.
     *
     * @return Result indicando éxito o fracaso
     */
    suspend fun writeWords(memoryArea: Int, address: Int, data: ShortArray): Result<Unit> {
        val client = connectionManager.getCurrentClient()
        if (client == null || !connectionManager.isConnected()) {
            Log.w(TAG, "Write attempt while not connected.")
            return Result.failure(IllegalStateException("Not connected"))
        }

        return try {
            Log.i(TAG, "Writing ${data.size} words to area $memoryArea at address $address")

            val response = client.writeWords(memoryArea.toByte(), address, data)

            when (response) {
                is FinsResponse.Success -> Result.success(Unit)
                is FinsResponse.FinsError -> Result.failure(Exception("FINS Error Code: ${response.endCode}"))
                is FinsResponse.TimeoutError -> Result.failure(Exception("Timeout"))
                is FinsResponse.NetworkError -> Result.failure(response.exception)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lee un array de palabras desde una dirección de memoria específica.
     * Usado por otros ViewModels para lecturas genéricas.
     *
     * @return Result con ShortArray en caso de éxito, o Exception en caso de fallo
     */
    suspend fun readWords(memoryArea: Int, address: Int, count: Int): Result<ShortArray> {
        val client = connectionManager.getCurrentClient()
        if (client == null || !connectionManager.isConnected()) {
            Log.w(TAG, "Read attempt while not connected.")
            return Result.failure(IllegalStateException("Not connected"))
        }

        return try {
            Log.d(TAG, "Reading $count words from area $memoryArea at address $address")

            val response = client.readWords(memoryArea.toByte(), address, count)

            when (response) {
                is FinsResponse.Success -> {
                    // Convertir ByteArray a ShortArray
                    val dataBytes = response.data
                    val buffer = ByteBuffer.wrap(dataBytes).order(ByteOrder.BIG_ENDIAN)
                    val shortArray = ShortArray(dataBytes.size / 2) { buffer.getShort() }
                    Result.success(shortArray)
                }
                is FinsResponse.FinsError -> Result.failure(Exception("FINS Error Code: ${response.endCode}"))
                is FinsResponse.TimeoutError -> Result.failure(Exception("Timeout"))
                is FinsResponse.NetworkError -> Result.failure(response.exception)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Establece o limpia un bit específico en un área de memoria.
     *
     * @param memoryArea Código de área de memoria (ej. 0x82 para DM, 0x30 para CIO)
     * @param address Dirección de palabra
     * @param bitNumber Número de bit (0-15)
     * @param value true para establecer el bit, false para limpiarlo
     * @return Result indicando éxito o fracaso
     */
    suspend fun setBit(memoryArea: Int, address: Int, bitNumber: Int, value: Boolean): Result<Unit> {
        if (bitNumber !in 0..15) {
            return Result.failure(IllegalArgumentException("Bit number must be between 0 and 15"))
        }

        return try {
            // Leer el valor actual de la palabra
            val readResult = readWords(memoryArea, address, 1)
            if (readResult.isFailure) {
                return Result.failure(readResult.exceptionOrNull() ?: Exception("Read failed"))
            }

            val currentValue = readResult.getOrNull()!![0].toInt()
            val newValue = if (value) {
                currentValue or (1 shl bitNumber)  // Set bit
            } else {
                currentValue and (1 shl bitNumber).inv()  // Clear bit
            }

            // Escribir el valor modificado
            writeWords(memoryArea, address, shortArrayOf(newValue.toShort()))
        } catch (e: Exception) {
            Log.e(TAG, "Error setting bit", e)
            Result.failure(e)
        }
    }

    /**
     * Convierte un número decimal a formato BCD (Binary Coded Decimal).
     */
    fun decimalToBcd(decimal: Long, wordCount: Int): ShortArray {
        val result = ShortArray(wordCount) { 0 }
        var temp = decimal
        var wordIndex = wordCount - 1

        while (temp > 0 && wordIndex >= 0) {
            var bcdWord = 0
            for (digitPos in 0 until 4) {
                val digit = (temp % 10).toInt()
                bcdWord = bcdWord or (digit shl (digitPos * 4))
                temp /= 10
                if (temp == 0L) break
            }
            result[wordIndex] = bcdWord.toShort()
            wordIndex--
        }

        return result
    }

    /**
     * Convierte un array de palabras en formato BCD a número decimal.
     */
    fun bcdToDecimal(bcdWords: ShortArray): Long {
        var result = 0L

        for (word in bcdWords) {
            val wordValue = word.toInt() and 0xFFFF
            for (digitPos in 3 downTo 0) {
                val digit = (wordValue shr (digitPos * 4)) and 0x0F
                result = result * 10 + digit
            }
        }

        return result
    }

    /**
     * Obtiene el cliente actual para uso directo (casos especiales).
     */
    fun getCurrentClient(): IFinsClient? = connectionManager.getCurrentClient()

    /**
     * Verifica si hay una conexión activa.
     */
    fun isConnected(): Boolean = connectionManager.isConnected()

    /**
     * Obtiene el ConnectionManager para acceso directo (casos especiales).
     */
    fun getConnectionManager(): ConnectionManager = connectionManager

    override fun onCleared() {
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        connectionManager.cleanup()
        super.onCleared()
    }
}
