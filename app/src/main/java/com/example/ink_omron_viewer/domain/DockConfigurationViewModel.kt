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
import com.example.ink_omron_viewer.ui.CommunicationViewModel
import com.example.ink_omron_viewer.ui.SharedViewModel
import kotlinx.coroutines.launch

class DockConfigurationViewModel(application: Application) : AndroidViewModel(application) {

    private val _writeStatus = MutableLiveData<String>()
    val writeStatus: LiveData<String> = _writeStatus

    private val _platformIndex = MutableLiveData<Int>()
    val platformIndex: LiveData<Int> = _platformIndex

    private val _trafficLightIndex = MutableLiveData<Int>()
    val trafficLightIndex: LiveData<Int> = _trafficLightIndex

    private val _shelterEnabled = MutableLiveData<Boolean>()
    val shelterEnabled: LiveData<Boolean> = _shelterEnabled

    private val _doorEnabled = MutableLiveData<Boolean>()
    val doorEnabled: LiveData<Boolean> = _doorEnabled

    private val _orderNumber = MutableLiveData<String>()
    val orderNumber: LiveData<String> = _orderNumber

    private val _dockPosition = MutableLiveData<String>()
    val dockPosition: LiveData<String> = _dockPosition

    private val _hasOrderData = MutableLiveData<Boolean>(false)
    val hasOrderData: LiveData<Boolean> = _hasOrderData

    private val _hasDockPositionData = MutableLiveData<Boolean>(false)
    val hasDockPositionData: LiveData<Boolean> = _hasDockPositionData

    /**
     * Guarda la configuración completa del muelle en el PLC.
     * Orquesta múltiples escrituras y reporta un estado final.
     */
    fun saveDockConfiguration(
        commsViewModel: CommunicationViewModel, // Pasamos el CommunicationViewModel para acceder a la comunicación
        sharedViewModel: SharedViewModel,
        shelterEnabled: Boolean,
        doorEnabled: Boolean,
        orderNumber: String,
        dockPosition: String,
        platformIndex: Int,
        trafficLightIndex: Int,
    ) {
        viewModelScope.launch {
            _writeStatus.postValue("Guardando configuración...")

            val errorMessages = mutableListOf<String>()

            // Escribir switches de activación en el área HR
            val activationResults = listOf(
                commsViewModel.setBit(FinsMemoryArea.HR.toInt(), 0, 0, shelterEnabled), // H0.00
                commsViewModel.setBit(FinsMemoryArea.HR.toInt(), 1, 0, doorEnabled)    // H1.00
            )
            val selectionResults = listOf(
                commsViewModel.writeWords(FinsMemoryArea.DM.toInt(), 550, shortArrayOf(platformIndex.toShort())),
                commsViewModel.writeWords(FinsMemoryArea.DM.toInt(), 551, shortArrayOf(trafficLightIndex.toShort()))
            )

            (activationResults + selectionResults).forEachIndexed { index, result ->
                if (result.isFailure) {
                    val machine = listOf("Act. Abrigo", "Act. Puerta", "Modo Plataforma", "Modo Semáforos")[index]
                    errorMessages.add("$machine: ${result.exceptionOrNull()?.message}")
                }
            }

            // Guardar número de pedido en BCD (D530-D539)
            if (orderNumber.isNotEmpty()) {
                val orderResult = saveOrderNumberBcd(commsViewModel, orderNumber)
                if (orderResult.isFailure) {
                    errorMessages.add("Pedido: ${orderResult.exceptionOrNull()?.message}")
                }
            }

            // Guardar posición del muelle en BCD (D540)
            if (dockPosition.isNotEmpty()) {
                val positionResult = saveDockPositionBcd(commsViewModel, dockPosition)
                if (positionResult.isFailure) {
                    errorMessages.add("Posición: ${positionResult.exceptionOrNull()?.message}")
                }
            }

            // Guardar Modelo PLC e IP (usando los valores del SharedViewModel)
            val plcIdResult = savePlcIdentification(commsViewModel, sharedViewModel)
            if (plcIdResult.isFailure) {
                errorMessages.add("ID PLC: ${plcIdResult.exceptionOrNull()?.message}")
            }


            // Cuando todas las escrituras han terminado, actualizamos el estado
            val finalMessage = if (errorMessages.isEmpty()) {
                "¡Éxito! Configuración guardada."
            } else {
                "Guardado parcial. Errores: ${errorMessages.joinToString()}"
            }

            // Recargar la configuración para asegurar que la UI está sincronizada
            loadDockConfiguration(commsViewModel)
            // Post value to update UI from background thread if needed
            _writeStatus.postValue(finalMessage)
        }
    }

    private suspend fun savePlcIdentification(commsViewModel: CommunicationViewModel, sharedViewModel: SharedViewModel): Result<Unit> {
        return try {
            val model = sharedViewModel.selectedPlcModel.value?.name ?: ""
            val ipAddress = sharedViewModel.plcIpAddress.value ?: ""

            if (model.isNotEmpty()) {
                if (model.length > 40) return Result.failure(Exception("El modelo excede los 40 caracteres."))
                val modelWords = stringToAsciiWords(model, 20)
                val modelResult = commsViewModel.writeWords(FinsMemoryArea.DM.toInt(), 500, modelWords)
                if (modelResult.isFailure) return modelResult
            }

            if (ipAddress.isNotEmpty()) {
                if (ipAddress.length > 20) return Result.failure(Exception("La IP excede los 20 caracteres."))
                val ipWords = stringToAsciiWords(ipAddress, 10)
                val ipResult = commsViewModel.writeWords(FinsMemoryArea.DM.toInt(), 520, ipWords)
                if (ipResult.isFailure) return ipResult
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun stringToAsciiWords(input: String, wordCount: Int): ShortArray {
        val byteCount = wordCount * 2
        val paddedString = input.padEnd(byteCount, '\u0000').substring(0, byteCount)
        val bytes = paddedString.toByteArray(Charsets.US_ASCII)
        val byteBuffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.BIG_ENDIAN)
        return ShortArray(wordCount) { byteBuffer.short }
    }

    /**
     * Guarda el número de pedido en formato BCD en D530-D539.
     * Formato esperado: "1120005678/20" (9 dígitos + "/" + 2 dígitos)
     * Se almacenan como 2 valores separados:
     * - Número de pedido (9 dígitos antes del "/"): D530-D534 (5 palabras BCD)
     * - Posición (2 dígitos después del "/"): D535-D539 (5 palabras BCD)
     */
    private suspend fun saveOrderNumberBcd(commsViewModel: CommunicationViewModel, orderNumber: String): Result<Unit> {
        return try {
            // Validar formato
            val parts = orderNumber.split("/")
            if (parts.size != 2) {
                return Result.failure(Exception("Formato inválido. Use: 1120005678/20"))
            }

            val pedido = parts[0].trim()      // 9 dígitos ANTES del "/"
            val posicion = parts[1].trim()    // 2 dígitos DESPUÉS del "/"

            if (pedido.length != 10 || !pedido.all { it.isDigit() }) {
                return Result.failure(Exception("El número de pedido debe tener 10 dígitos"))
            }

            if (posicion.length != 2 || !posicion.all { it.isDigit() }) {
                return Result.failure(Exception("La posición debe tener 2 dígitos"))
            }

            // Convertir pedido (10 dígitos) a BCD
            // 9 dígitos necesitan 3 palabras BCD (4 dígitos por palabra, redondeando hacia arriba)
            val pedidoNumber = pedido.toLong()
            val pedidoBcd = commsViewModel.decimalToBcd(pedidoNumber, 3)

            // Escribir pedido en D530-D534 (5 palabras - usando 3 de ellas)
            val result1 = commsViewModel.writeWords(FinsMemoryArea.DM.toInt(), 530, pedidoBcd)
            if (result1.isFailure) {
                return result1
            }

            // Convertir posición (2 dígitos) a BCD
            val posicionNumber = posicion.toLong()
            val posicionBcd = commsViewModel.decimalToBcd(posicionNumber, 1)

            // Escribir posición en D535 (primera palabra del segundo bloque)
            commsViewModel.writeWords(FinsMemoryArea.DM.toInt(), 535, posicionBcd)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Guarda la posición del muelle en formato BCD en D540.
     * Formato esperado: 3 dígitos (000-999)
     * NOTA: Esta es la posición del muelle de carga, NO la posición del pedido (que va en D535)
     */
    private suspend fun saveDockPositionBcd(commsViewModel: CommunicationViewModel, position: String): Result<Unit> {
        return try {
            // Validar formato
            if (position.length > 3 || !position.all { it.isDigit() }) {
                return Result.failure(Exception("Posición del muelle debe tener máximo 3 dígitos"))
            }

            val positionNumber = position.toLong()
            if (positionNumber > 999) {
                return Result.failure(Exception("Posición del muelle debe ser menor a 1000"))
            }

            // Convertir a BCD (3 dígitos necesitan 1 palabra)
            val positionBcd = commsViewModel.decimalToBcd(positionNumber, 1)

            // Escribir en D540
            commsViewModel.writeWords(FinsMemoryArea.DM.toInt(), 540, positionBcd)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Carga la configuración actual del muelle desde el PLC.
     */
    fun loadDockConfiguration(commsViewModel: CommunicationViewModel) {
        viewModelScope.launch {
            // Leer switches de activación desde el área HR
            commsViewModel.readWords(FinsMemoryArea.HR.toInt(), 0, 1).onSuccess { data ->
                if (data.isNotEmpty()) {
                    val word = data[0].toInt()
                    _shelterEnabled.postValue((word and (1 shl 0)) != 0) // H0.00
                }
            }.onFailure {
                Log.w("DockConfigVM", "Failed to read Shelter config (H0): ${it.message}")
            }
            commsViewModel.readWords(FinsMemoryArea.HR.toInt(), 1, 1).onSuccess { data ->
                if (data.isNotEmpty()) {
                    val word = data[0].toInt()
                    _doorEnabled.postValue((word and (1 shl 0)) != 0) // H1.00
                }
            }.onFailure {
                Log.w("DockConfigVM", "Failed to read Door config (H1): ${it.message}")
            }

            // Leer índices de Plataforma y Semáforos desde el área DM
            commsViewModel.readWords(FinsMemoryArea.DM.toInt(), 550, 2).onSuccess { data ->
                if (data.size >= 2) {
                    val platformIdx = data[0].toInt()
                    val trafficLightIdx = data[1].toInt()
                    Log.d("DockConfigVM", "Read platform index: $platformIdx, traffic light index: $trafficLightIdx")
                    _platformIndex.postValue(platformIdx)
                    _trafficLightIndex.postValue(trafficLightIdx)
                }
            }.onFailure {
                Log.w("DockConfigVM", "Failed to read platform/traffic light indices (D550-D551): ${it.message}")
            }

            // Leer número de pedido (D530-D534 para pedido, D535 para posición)
            commsViewModel.readWords(FinsMemoryArea.DM.toInt(), 530, 6).onSuccess { data ->
                if (data.size >= 6) {
                    try {
                        // Leer pedido (primeras 3 palabras para 10 dígitos)
                        val pedidoData = data.sliceArray(0..2)
                        val pedido = commsViewModel.bcdToDecimal(pedidoData)

                        // Leer posición del pedido (1 palabra desde D535)
                        val posicionData = shortArrayOf(data[5])
                        val posicion = commsViewModel.bcdToDecimal(posicionData)

                        // Verificar si hay datos
                        val hasData = pedido != 0L || posicion != 0L
                        _hasOrderData.postValue(hasData)

                        if (hasData) {
                            // Formatear como "1120005678/20"
                            val orderStr = String.format("%010d/%02d", pedido, posicion)
                            _orderNumber.postValue(orderStr)
                            Log.d("DockConfigVM", "Read order number: $orderStr")
                        } else {
                            _orderNumber.postValue("")
                        }
                    } catch (e: Exception) {
                        Log.w("DockConfigVM", "Error parsing order number: ${e.message}")
                        _orderNumber.postValue("")
                        _hasOrderData.postValue(false)
                    }
                }
            }.onFailure {
                Log.w("DockConfigVM", "Failed to read order number: ${it.message}")
                _orderNumber.postValue("")
                _hasOrderData.postValue(false)
            }

            // Leer posición del muelle (D540: 1 palabra = 3 dígitos máximo)
            commsViewModel.readWords(FinsMemoryArea.DM.toInt(), 540, 1).onSuccess { data ->
                if (data.isNotEmpty()) {
                    try {
                        val position = commsViewModel.bcdToDecimal(data)

                        // Verificar si hay datos (valor diferente de 0)
                        val hasData = position != 0L
                        _hasDockPositionData.postValue(hasData)

                        if (hasData) {
                            _dockPosition.postValue(position.toString())
                        } else {
                            _dockPosition.postValue("")
                        }
                    } catch (e: Exception) {
                        Log.w("DockConfigVM", "Error parsing dock position: ${e.message}")
                        _dockPosition.postValue("")
                        _hasDockPositionData.postValue(false)
                    }
                }
            }.onFailure {
                Log.w("DockConfigVM", "Failed to read dock position: ${it.message}")
                _dockPosition.postValue("")
                _hasDockPositionData.postValue(false)
            }
        }
    }

    /**
     * Indica que el mensaje de estado de escritura ya ha sido mostrado.
     */
    fun onWriteStatusShown() {
        _writeStatus.value = ""
    }

    /**
     * Restablece la configuración del muelle a sus valores por defecto en el PLC.
     */
    fun resetDockConfiguration(commsViewModel: CommunicationViewModel) {
        viewModelScope.launch {
            _writeStatus.postValue("Restableciendo configuración...")

            // Valores por defecto
            val writeResults = listOf(
                commsViewModel.setBit(FinsMemoryArea.HR.toInt(), 0, 0, false),
                commsViewModel.setBit(FinsMemoryArea.HR.toInt(), 1, 0, false),
                commsViewModel.writeWords(FinsMemoryArea.DM.toInt(), 550, shortArrayOf(0)), // Plataforma a "Ninguna"
                commsViewModel.writeWords(FinsMemoryArea.DM.toInt(), 551, shortArrayOf(0)), // Semáforos a "Ninguna"
                commsViewModel.writeWords(FinsMemoryArea.DM.toInt(), 530, ShortArray(5) { 0 }), // Borrar Pedido
                commsViewModel.writeWords(FinsMemoryArea.DM.toInt(), 540, shortArrayOf(0))  // Borrar Muelle
            )

            val errorMessages = mutableListOf<String>()
            writeResults.forEachIndexed { index, result ->
                if (result.isFailure) {
                    val operation = listOf("Abrigo", "Puerta", "Plataforma", "Semáforos", "Pedido", "Muelle")[index]
                    errorMessages.add("$operation: ${result.exceptionOrNull()?.message}")
                }
            }

            val finalMessage = if (errorMessages.isEmpty()) {
                "¡Éxito! Configuración restablecida."
            } else {
                "Restablecimiento parcial. Errores: ${errorMessages.joinToString()}"
            }

            loadDockConfiguration(commsViewModel) // Recargar para confirmar
            _writeStatus.postValue(finalMessage)
        }
    }
}