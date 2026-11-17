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

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.ink_omron_viewer.domain.FinsMemoryArea
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.example.ink_omron_viewer.ui.CommunicationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
class ConfigurationViewModel(application: Application) : AndroidViewModel(application) {

    private val usbSerialManager = UsbSerialManager(application.applicationContext)

    // USB
    private val _ports = MutableLiveData<List<UsbSerialPort>>().apply { value = emptyList() }
    val ports: LiveData<List<UsbSerialPort>> = _ports

    private val _connectionStatus = MutableLiveData<Boolean>()
    val connectionStatus: LiveData<Boolean> = _connectionStatus

    // Ethernet
    private val _ethernetStatus = MutableLiveData<String>()
    val ethernetStatus: LiveData<String> = _ethernetStatus

    private val _plcModel = MutableLiveData<String>()
    val plcModel: LiveData<String> = _plcModel

    private val _plcIpAddress = MutableLiveData<String>()
    val plcIpAddress: LiveData<String> = _plcIpAddress

    fun findUsbDevices() {
        _ports.value = usbSerialManager.findDrivers()
    }

    fun connect(port: UsbSerialPort) {
        usbSerialManager.connect(port) {
            _connectionStatus.postValue(it)
        }
    }

    fun disconnect() {
        usbSerialManager.close()
        _connectionStatus.postValue(false)
    }

    fun testEthernetConnection(ipAddress: String, client: FinsUdpClient, onConnectionSuccess: (String) -> Unit) {
        if (ipAddress.isBlank() || !ipAddress.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))) {
            _ethernetStatus.value = "La dirección IP no es válida."
            return
        }

        _ethernetStatus.value = "Conectando a $ipAddress..."

        viewModelScope.launch {
            when (val response = client.readWords(FinsMemoryArea.DM, 0, 1)) {
                is FinsResponse.Success -> {
                    onConnectionSuccess(ipAddress)
                    val value = java.nio.ByteBuffer.wrap(response.data).order(java.nio.ByteOrder.BIG_ENDIAN).short
                    _ethernetStatus.postValue("¡Éxito! Valor de D0: $value. IP guardada.")
                }
                is FinsResponse.FinsError -> {
                    _ethernetStatus.postValue("Error FINS. Código: ${response.endCode}")
                }
                is FinsResponse.TimeoutError -> {
                    _ethernetStatus.postValue("Error: Timeout. El PLC no respondió.")
                }
                is FinsResponse.NetworkError -> {
                    _ethernetStatus.postValue("Error de red: ${response.exception.message}")
                }
            }
        }
    }

    fun loadPlcIdentification(commsViewModel: CommunicationViewModel) {
        viewModelScope.launch {
            // Leer Modelo del PLC (D500, 20 palabras)
            commsViewModel.readWords(FinsMemoryArea.DM.toInt(), 500, 20).onSuccess { data ->
                val model = parseAsciiString(data)
                if (model.isNotEmpty()) {
                    _plcModel.postValue(model)
                }
            }.onFailure {
                Log.w("ConfigVM", "Failed to read PLC model: ${it.message}")
            }

            // Leer Dirección IP del PLC (D520, 10 palabras)
            commsViewModel.readWords(FinsMemoryArea.DM.toInt(), 520, 10).onSuccess { data ->
                val ip = parseAsciiString(data)
                if (ip.isNotEmpty()) {
                    _plcIpAddress.postValue(ip)
                }
            }.onFailure {
                Log.w("ConfigVM", "Failed to read PLC IP address: ${it.message}")
            }
        }
    }

    private fun parseAsciiString(words: ShortArray): String {
        val byteBuffer = ByteBuffer.allocate(words.size * 2).order(ByteOrder.BIG_ENDIAN)
        words.forEach { byteBuffer.putShort(it) }
        return String(byteBuffer.array(), StandardCharsets.US_ASCII).replace("\u0000", "").trim()
    }

    /**
     * Convierte un String a un ShortArray en formato ASCII, rellenando con nulos.
     * Cada palabra (Short) contiene 2 caracteres.
     */
    private fun stringToAsciiWords(input: String, wordCount: Int): ShortArray {
        val byteCount = wordCount * 2
        // Truncar o rellenar el string para que tenga la longitud exacta en bytes
        val paddedString = input.padEnd(byteCount, '\u0000').substring(0, byteCount)
        val bytes = paddedString.toByteArray(Charsets.US_ASCII)

        // Agrupar bytes en palabras (Shorts)
        val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val shortArray = ShortArray(wordCount)
        for (i in 0 until wordCount) {
            shortArray[i] = byteBuffer.short
        }
        return shortArray
    }
}