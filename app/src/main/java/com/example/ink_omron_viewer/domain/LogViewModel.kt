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
import com.example.ink_omron_viewer.domain.log.*
import com.example.ink_omron_viewer.ui.SharedViewModel
import com.example.ink_omron_viewer.ui.CommunicationViewModel
import kotlinx.coroutines.launch
import java.io.File

class LogViewModel(application: Application) : AndroidViewModel(application) {

    private val _systemLog = MutableLiveData<SystemLog?>()
    val systemLog: LiveData<SystemLog?> = _systemLog

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private val _generatedFile = MutableLiveData<File?>()
    val generatedFile: LiveData<File?> = _generatedFile

    private val csvGenerator = CsvGenerator(application)
    private val emailExporter = EmailExporter(application)
    private val ftpExporter = FtpExporter()

    fun loadSystemLog(
        commsViewModel: CommunicationViewModel,
        sharedViewModel: SharedViewModel,
        dockConfigViewModel: DockConfigurationViewModel
    ) {
        viewModelScope.launch {
            _isLoading.postValue(true)
            val logManager = LogManager(commsViewModel, sharedViewModel, dockConfigViewModel)
            val result = logManager.generateSystemLog()
            result.onSuccess { log ->
                _systemLog.postValue(log)
                _toastMessage.postValue("Log cargado con ${log.logEntries.size} entradas.")
            }.onFailure { error ->
                _systemLog.postValue(null)
                Log.e("LogViewModel", "Error loading system log (Ask Gemini)", error)
                _toastMessage.postValue("Error al cargar el log: ${error.message}")
            }
            _isLoading.postValue(false)
        }
    }

    fun generateCsvFile(exportConfig: ExportConfig = ExportConfig()) {
        val log = _systemLog.value ?: run {
            _toastMessage.postValue("No hay log para generar CSV.")
            return
        }
        viewModelScope.launch {
            _isLoading.postValue(true)
            val file = csvGenerator.generateCsv(log, exportConfig)
            if (file != null) {
                _generatedFile.postValue(file)
                _toastMessage.postValue("Archivo CSV generado: ${file.name}")
            } else {
                _generatedFile.postValue(null)
                _toastMessage.postValue("Error al generar el archivo CSV.")
            }
            _isLoading.postValue(false)
        }
    }

    fun exportByEmail(emailConfig: EmailConfig) {
        val file = _generatedFile.value ?: run {
            _toastMessage.postValue("Genere primero el archivo CSV.")
            return
        }
        val success = emailExporter.sendByEmail(file, emailConfig)
        if (!success) {
            _toastMessage.postValue("No se pudo abrir el cliente de email.")
        }
    }

    fun exportByFtp(ftpConfig: FtpConfig) {
        val file = _generatedFile.value ?: run {
            _toastMessage.postValue("Genere primero el archivo CSV.")
            return
        }
        viewModelScope.launch {
            _isLoading.postValue(true)
            val result = ftpExporter.uploadFile(file, ftpConfig)
            result.onSuccess {
                _toastMessage.postValue("Archivo subido a FTP con éxito.")
            }.onFailure {
                _toastMessage.postValue("Error en FTP: ${it.message}")
            }
            _isLoading.postValue(false)
        }
    }

    fun getStatistics(): Map<String, Int> {
        val log = _systemLog.value ?: return emptyMap()
        val stats = mutableMapOf<String, Int>()

        // Contar eventos por tipo de máquina
        val machineEventCounts = mutableMapOf(
            "Abrigo" to 0, "Puerta" to 0, "Plataforma" to 0, "Semáforos" to 0, "Sistema" to 0
        )
        log.logEntries.forEach { entry ->
            when (entry.eventCode) {
                in 100..199 -> machineEventCounts["Abrigo"] = machineEventCounts["Abrigo"]!! + 1
                in 200..299 -> machineEventCounts["Puerta"] = machineEventCounts["Puerta"]!! + 1
                in 300..399 -> machineEventCounts["Plataforma"] = machineEventCounts["Plataforma"]!! + 1
                in 400..499 -> machineEventCounts["Semáforos"] = machineEventCounts["Semáforos"]!! + 1
                in 900..999 -> machineEventCounts["Sistema"] = machineEventCounts["Sistema"]!! + 1
            }
        }
        stats.putAll(machineEventCounts.filter { it.value > 0 })
        return stats
    }

    fun onToastShown() {
        _toastMessage.value = ""
    }
}
