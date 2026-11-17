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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ink_omron_viewer.domain.FinsAddressParser

class SynopticViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _pollEvent = MutableLiveData<Unit>()
    val pollEvent: LiveData<Unit> = _pollEvent

    private val _ioStatus = MutableLiveData<ShortArray>()
    val ioStatus: LiveData<ShortArray> = _ioStatus

    private var pollingJob: Job? = null

    fun startPolling() {
        if (pollingJob?.isActive == true) return

        pollingJob = viewModelScope.launch {
            while (true) {
                _pollEvent.postValue(Unit)
                delay(500) // Polling interval
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun pollPlcData(commsViewModel: CommunicationViewModel) {
        viewModelScope.launch {
            // Leer 110 palabras para cubrir las entradas (CIO 0-9) y las salidas (CIO 100-109)
            val result = commsViewModel.readWords(FinsMemoryArea.CIO.toInt(), 0, 110)
            result.onSuccess { data ->
                // Si la lectura es exitosa, actualizamos el LiveData
                _ioStatus.postValue(data)
                Log.d("SynopticViewModel", "CIO Read successful: ${data.joinToString { it.toString(16) }}")
            }.onFailure { error ->
                // Si falla, lo registramos en el log. Podríamos mostrar un error en la UI.
                Log.w("SynopticViewModel", "CIO Read failed: ${error.message}")
            }
        }
    }

    fun parseMemoryAddress(addressString: String): ParsedAddress? {
        return FinsAddressParser.parse(addressString)
    }
}