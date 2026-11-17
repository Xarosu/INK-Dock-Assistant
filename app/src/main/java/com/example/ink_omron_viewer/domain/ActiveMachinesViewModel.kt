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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class ActiveMachinesState(
    val isShelterActive: Boolean = false,
    val isDoorActive: Boolean = false,
    val isPlatformActive: Boolean = false,
    val isTrafficLightActive: Boolean = false
) {
    val isAnyMachineActive: Boolean
        get() = isShelterActive || isDoorActive || isPlatformActive || isTrafficLightActive
}

class ActiveMachinesViewModel : ViewModel() {

    private val _activeMachinesState = MutableLiveData(ActiveMachinesState())
    val activeMachinesState: LiveData<ActiveMachinesState> = _activeMachinesState

    fun updateMachineStatus(
        shelter: Boolean,
        door: Boolean,
        platform: Boolean,
        trafficLight: Boolean
    ) {
        _activeMachinesState.value = ActiveMachinesState(shelter, door, platform, trafficLight)
    }
}