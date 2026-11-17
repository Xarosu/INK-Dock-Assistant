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

package com.example.ink_omron_viewer.ui.support

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SupportViewModel : ViewModel() {

    // Card 1: Contacto
    val contactEmail = MutableLiveData<String>().apply {
        value = "xarosu@gmail.com"
    }
    val contactPhone = MutableLiveData<String>().apply {
        value = "" // Sin teléfono: la línea se oculta
    }

    // Card 2: Soporte Técnico
    val supportEmail = MutableLiveData<String>().apply {
        value = "xarosu@gmail.com"
    }
    val supportPhone = MutableLiveData<String>().apply {
        value = "" // Sin teléfono: la línea se oculta
    }
    val supportWhatsapp = MutableLiveData<String>().apply {
        value = "" // Sin WhatsApp: la línea se oculta (solo dígitos si se rellena)
    }
    val supportWhatsappDisplay = MutableLiveData<String>().apply {
        value = ""
    }
}