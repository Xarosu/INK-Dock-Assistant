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
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.ink_omron_viewer.domain.PlcModel
import com.example.ink_omron_viewer.domain.PlcRepository
import com.hoho.android.usbserial.driver.UsbSerialPort

/**
 * Tipo de conexión soportado.
 * - ETHERNET: Conexión a PLC Omron vía FINS/TCP o FINS/UDP
 * - USB_ESP32: Conexión directa a ESP32 vía USB Serial
 */
enum class ConnectionType {
    ETHERNET,
    USB_ESP32
}

class SharedViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SharedViewModel"
        const val PLC_IP_ADDRESS_KEY = "plc_ip_address"
        const val CLIENT_NODE_KEY = "client_node"
    }

    private val sharedPreferences = application.getSharedPreferences("plc_settings", Context.MODE_PRIVATE)

    private val _selectedPlcModel = MutableLiveData<PlcModel>()
    val selectedPlcModel: LiveData<PlcModel> = _selectedPlcModel

    private val _connectionType = MutableLiveData<ConnectionType>()
    val connectionType: LiveData<ConnectionType> = _connectionType

    private val _plcIpAddress = MutableLiveData<String>()
    val plcIpAddress: LiveData<String> = _plcIpAddress

    private val _clientNode = MutableLiveData<Int>()
    val clientNode: LiveData<Int> = _clientNode

    private val _usbStatus = MutableLiveData("Not connected")
    val usbStatus: LiveData<String> = _usbStatus

    private val _isClientReady = MutableLiveData(false)
    val isClientReady: LiveData<Boolean> = _isClientReady

    private val _ports = MutableLiveData<List<UsbSerialPort>>().apply { value = emptyList() }
    val ports: LiveData<List<UsbSerialPort>> = _ports

    init {
        loadPreferences()
        // La conexión se iniciará desde la UI o al cambiar de tipo
    }

    fun testConnection() {
        _usbStatus.value = "Test Connection will be handled by CommunicationViewModel."
    }

    // region Preference Methods
    private fun loadPreferences() {
        val savedPlcName = sharedPreferences.getString("selected_plc_name", null)
        _selectedPlcModel.value = PlcRepository.findByName(savedPlcName ?: "") ?: PlcRepository.getDefault()
        val savedConnTypeName = sharedPreferences.getString("connection_type", ConnectionType.ETHERNET.name)
        _connectionType.value = ConnectionType.valueOf(savedConnTypeName ?: ConnectionType.ETHERNET.name)
        _plcIpAddress.value = sharedPreferences.getString(PLC_IP_ADDRESS_KEY, "192.168.250.1")
        _clientNode.value = sharedPreferences.getInt(CLIENT_NODE_KEY, 0)
    }

    fun selectPlcModel(plcModel: PlcModel) {
        _selectedPlcModel.value = plcModel
        sharedPreferences.edit().putString("selected_plc_name", plcModel.name).apply()
    }

    fun selectConnectionType(connectionType: ConnectionType, forceReconnect: Boolean = false) {
        if (!forceReconnect && _connectionType.value == connectionType) return
        _connectionType.value = connectionType
        sharedPreferences.edit().putString("connection_type", connectionType.name).apply()
        _usbStatus.value = "Connection type changed to ${connectionType.name}. Use Connect/Disconnect buttons."
    }

    fun savePlcIpAddress(ip: String) {
        // Solo actuar si la IP realmente ha cambiado
        if (_plcIpAddress.value == ip) return

        _plcIpAddress.value = ip
        sharedPreferences.edit().putString(PLC_IP_ADDRESS_KEY, ip).apply()
    }

    fun saveClientNode(node: Int) {
        // Validar rango 0-254
        val validNode = node.coerceIn(0, 254)
        if (_clientNode.value == validNode) return

        _clientNode.value = validNode
        sharedPreferences.edit().putInt(CLIENT_NODE_KEY, validNode).apply()
    }

    fun forceReconnect() {
        _usbStatus.value = "Reconnect will be handled by CommunicationViewModel."
    }

    // endregion
}