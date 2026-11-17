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

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ink_omron_viewer.ui.CommunicationViewModel
import kotlinx.coroutines.launch

class ShelterViewModel : ViewModel() {

    private val _timerValue = MutableLiveData<Short>()
    val timerValue: LiveData<Short> = _timerValue

    private val _counterValues = MutableLiveData<List<Short>>()
    val counterValues: LiveData<List<Short>> = _counterValues

    private val _writeStatus = MutableLiveData<String>()
    val writeStatus: LiveData<String> = _writeStatus

    private val _serialNumber = MutableLiveData<String>()
    val serialNumber: LiveData<String> = _serialNumber

    private val _hasSerialData = MutableLiveData<Boolean>()
    val hasSerialData: LiveData<Boolean> = _hasSerialData

    private val _switchStates = MutableLiveData<List<Boolean>>()
    val switchStates: LiveData<List<Boolean>> = _switchStates

    companion object {
        private const val SHELTER_BASE_ADDRESS = 110
        private const val RESET_BITS_BASE_ADDRESS = 150
        private const val SERIAL_NUMBER_ADDRESS = 2100
        private const val SHELTER_SWITCHES_ADDRESS = 120 // H120-H121 - Switches de abrigo en área HR (2 palabras = 32 bits)
    }

    fun loadShelterData(commsViewModel: CommunicationViewModel) {
        viewModelScope.launch {
            // Leer 4 palabras: D110 (temporizador), D111 (horas desde mantenimiento), D112 (horas totales soplador), D113 (reserva)
            commsViewModel.readWords(FinsMemoryArea.DM.toInt(), SHELTER_BASE_ADDRESS, 4).onSuccess { data ->
                if (data.size >= 4) {
                    _timerValue.postValue(data[0])
                    // Contadores: D111 (horas desde mantenimiento), D112 (horas totales)
                    _counterValues.postValue(data.slice(1..2))
                }
            }.onFailure {
                Log.w("ShelterVM", "Failed to read shelter data: ${it.message}")
            }

            // Leer número de serie (3 palabras BCD)
            loadSerialNumber(commsViewModel)

            // Leer estados de los switches
            loadSwitchStates(commsViewModel)
        }
    }

    fun loadSwitchStates(commsViewModel: CommunicationViewModel) {
        viewModelScope.launch {
            commsViewModel.readWords(FinsMemoryArea.HR.toInt(), SHELTER_SWITCHES_ADDRESS, 1).onSuccess { data ->
                if (data.isNotEmpty()) {
                    val wordValue = data[0].toInt()
                    val states = List(2) { i -> (wordValue shr i) and 1 == 1 }
                    _switchStates.postValue(states)
                }
            }.onFailure {
                Log.w("ShelterVM", "Failed to read switch states: ${it.message}")
            }
        }
    }

    private fun loadSerialNumber(commsViewModel: CommunicationViewModel) {
        viewModelScope.launch {
            commsViewModel.readWords(FinsMemoryArea.DM.toInt(), SERIAL_NUMBER_ADDRESS, 3).onSuccess { data ->
                if (data.size >= 3) {
                    val serialValue = commsViewModel.bcdToDecimal(data)
                    val hasData = serialValue > 0
                    _hasSerialData.postValue(hasData)
                    if (hasData) {
                        _serialNumber.postValue(serialValue.toString().padStart(12, '0'))
                    } else {
                        _serialNumber.postValue("")
                    }
                }
            }.onFailure {
                Log.w("ShelterVM", "Failed to read serial number: ${it.message}")
                _hasSerialData.postValue(false)
                _serialNumber.postValue("")
            }
        }
    }

    fun saveTimerValue(commsViewModel: CommunicationViewModel, value: Short) {
        viewModelScope.launch {
            _writeStatus.postValue("Guardando...")
            val result = commsViewModel.writeWords(
                FinsMemoryArea.DM.toInt(),
                SHELTER_BASE_ADDRESS,
                shortArrayOf(value)
            )
            if (result.isSuccess) {
                _writeStatus.postValue("¡Éxito! Valor guardado.")
                // Re-leer los datos para confirmar
                loadShelterData(commsViewModel)
            } else {
                _writeStatus.postValue("Error: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun resetCounter(commsViewModel: CommunicationViewModel, counterIndex: Int) {
        viewModelScope.launch {
            _writeStatus.postValue("Reseteando contador...")
            // Mapeo: Contador 0 -> Bit 0, Contador 1 -> Bit 1, Contador 2 -> Bit 2
            val bitToSet = 0 + counterIndex
            val result = commsViewModel.setBit(
                FinsMemoryArea.WR.toInt(),
                RESET_BITS_BASE_ADDRESS,
                bitToSet,
                true  // Set bit to 1
            )

            if (result.isSuccess) {
                _writeStatus.postValue("¡Éxito! Señal de reset enviada.")
            } else {
                _writeStatus.postValue("Error: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun saveSerialNumber(commsViewModel: CommunicationViewModel, serialNumber: String) {
        viewModelScope.launch {
            _writeStatus.postValue("Guardando número de serie...")
            try {
                val serialValue = serialNumber.toLongOrNull()
                if (serialValue == null || serialNumber.length != 12) {
                    _writeStatus.postValue("Error: El número de serie debe tener 12 dígitos")
                    return@launch
                }

                val bcdWords = commsViewModel.decimalToBcd(serialValue, 3)
                val result = commsViewModel.writeWords(
                    FinsMemoryArea.DM.toInt(),
                    SERIAL_NUMBER_ADDRESS,
                    bcdWords
                )

                if (result.isSuccess) {
                    _writeStatus.postValue("¡Éxito! Número de serie guardado.")
                    loadSerialNumber(commsViewModel)
                } else {
                    _writeStatus.postValue("Error: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _writeStatus.postValue("Error: ${e.message}")
            }
        }
    }

    fun saveSwitchStates(commsViewModel: CommunicationViewModel, states: List<Boolean>) {
        viewModelScope.launch {
            _writeStatus.postValue("Guardando opciones...")

            var wordValue = 0
            states.forEachIndexed { index, isActive ->
                if (isActive) {
                    wordValue = wordValue or (1 shl index)
                }
            }

            val result = commsViewModel.writeWords(
                FinsMemoryArea.HR.toInt(),
                SHELTER_SWITCHES_ADDRESS,
                shortArrayOf(wordValue.toShort())
            )

            if (result.isSuccess) {
                _writeStatus.postValue("¡Éxito! Opciones guardadas.")
                loadSwitchStates(commsViewModel)
            } else {
                _writeStatus.postValue("Error: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun onWriteStatusShown() {
        _writeStatus.value = ""
    }
}