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

class PlatformViewModel : ViewModel() {

    private val _switchStates = MutableLiveData<List<Boolean>>()
    val switchStates: LiveData<List<Boolean>> = _switchStates

    private val _timerValue = MutableLiveData<Short>()
    val timerValue: LiveData<Short> = _timerValue

    private val _timer2Value = MutableLiveData<Short>()
    val timer2Value: LiveData<Short> = _timer2Value

    private val _counterValues = MutableLiveData<List<Short>>(emptyList())
    val counterValues: LiveData<List<Short>> = _counterValues

    private val _writeStatus = MutableLiveData<String>()
    val writeStatus: LiveData<String> = _writeStatus

    private val _serialNumber = MutableLiveData<String>()
    val serialNumber: LiveData<String> = _serialNumber

    private val _hasSerialData = MutableLiveData<Boolean>()
    val hasSerialData: LiveData<Boolean> = _hasSerialData

    companion object {
        private const val PLATFORM_TIMER_COUNTERS_ADDRESS = 130
        private const val RESET_BITS_BASE_ADDRESS = 150
        private const val PLATFORM_SWITCHES_ADDRESS = 124 // H124-H125 - Switches de plataforma en área HR (2 palabras = 32 bits)
        private const val SERIAL_NUMBER_ADDRESS = 2120
    }

    fun loadSwitchStates(commsViewModel: CommunicationViewModel) {
        viewModelScope.launch {
            commsViewModel.readWords(FinsMemoryArea.HR.toInt(), PLATFORM_SWITCHES_ADDRESS, 1).onSuccess { data ->
                if (data.isNotEmpty()) {
                    val wordValue = data[0].toInt()
                    val states = List(7) { i -> (wordValue shr i) and 1 == 1 }
                    _switchStates.postValue(states)
                }
            }.onFailure {
                Log.w("PlatformVM", "Failed to read switch states: ${it.message}")
            }
        }
    }

    fun loadTimerAndCounters(commsViewModel: CommunicationViewModel) {
        viewModelScope.launch {
            // Leer 9 palabras: D130 (timer1), D131 (timer2), D132 (reserva), D133-D136 (contadores varios), D137 (horas desde mantenimiento), D138 (horas totales bomba)
            commsViewModel.readWords(FinsMemoryArea.DM.toInt(), PLATFORM_TIMER_COUNTERS_ADDRESS, 9).onSuccess { data ->
                if (data.size >= 9) {
                    _timerValue.postValue(data[0])
                    _timer2Value.postValue(data[1])
                    // Contadores: D133 (pulsador subida), D134 (sacar labio), D135 (recoger labio), D136 (activacion KM1), D137 (horas desde mantenimiento), D138 (horas totales bomba)
                    _counterValues.postValue(data.slice(3..8))
                }
            }.onFailure {
                Log.w("PlatformVM", "Failed to read timer/counters: ${it.message}")
            }

            // Leer número de serie (3 palabras BCD)
            loadSerialNumber(commsViewModel)
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
                Log.w("PlatformVM", "Failed to read serial number: ${it.message}")
                _hasSerialData.postValue(false)
                _serialNumber.postValue("")
            }
        }
    }

    fun saveSwitchStates(commsViewModel: CommunicationViewModel, states: List<Boolean>) {
        viewModelScope.launch {
            _writeStatus.postValue("Guardando...")

            var wordValue = 0
            states.forEachIndexed { index, isActive ->
                if (isActive) {
                    wordValue = wordValue or (1 shl index)
                }
            }

            val result = commsViewModel.writeWords(
                FinsMemoryArea.HR.toInt(),
                PLATFORM_SWITCHES_ADDRESS,
                shortArrayOf(wordValue.toShort())
            )

            if (result.isSuccess) {
                _writeStatus.postValue("¡Éxito! Configuración guardada.")
                loadSwitchStates(commsViewModel) // Re-leer para confirmar
            } else {
                _writeStatus.postValue("Error: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun saveTimerValue(commsViewModel: CommunicationViewModel, value: Short) {
        viewModelScope.launch {
            _writeStatus.postValue("Guardando temporizador...")
            val result = commsViewModel.writeWords(
                FinsMemoryArea.DM.toInt(),
                PLATFORM_TIMER_COUNTERS_ADDRESS,
                shortArrayOf(value)
            )

            if (result.isSuccess) {
                _writeStatus.postValue("¡Éxito! Temporizador guardado.")
                // Re-leer para confirmar
                loadTimerAndCounters(commsViewModel)
            } else {
                _writeStatus.postValue("Error: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun saveTimer2Value(commsViewModel: CommunicationViewModel, value: Short) {
        viewModelScope.launch {
            _writeStatus.postValue("Guardando temporizador 2...")
            val result = commsViewModel.writeWords(
                FinsMemoryArea.DM.toInt(),
                PLATFORM_TIMER_COUNTERS_ADDRESS + 1, // D131
                shortArrayOf(value)
            )

            if (result.isSuccess) {
                _writeStatus.postValue("¡Éxito! Temporizador 2 guardado.")
                // Re-leer para confirmar
                loadTimerAndCounters(commsViewModel)
            } else {
                _writeStatus.postValue("Error: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun resetCounter(commsViewModel: CommunicationViewModel, counterIndex: Int) {
        viewModelScope.launch {
            _writeStatus.postValue("Reseteando contador...")
            // Mapeo: Contador 0 -> Bit 6, Contador 1 -> Bit 7, Contador 2 -> Bit 8, Contador 3 -> Bit 9, Contador 4 -> Bit 10
            val bitToSet = 6 + counterIndex
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

    fun onWriteStatusShown() {
        _writeStatus.value = ""
    }
}