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

package com.example.ink_omron_viewer.ui.configuration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import com.example.ink_omron_viewer.R
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ink_omron_viewer.domain.ConfigurationViewModel
import com.example.ink_omron_viewer.databinding.FragmentConfigurationBinding
import com.example.ink_omron_viewer.domain.FinsProtocol
import com.example.ink_omron_viewer.domain.PlcModel
import com.example.ink_omron_viewer.domain.PlcRepository
import com.example.ink_omron_viewer.ui.ConnectionType
import com.example.ink_omron_viewer.ui.CommunicationViewModel
import com.example.ink_omron_viewer.ui.SharedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import android.content.Context
import android.hardware.usb.UsbManager
import java.util.Collections

class ConfigurationFragment : Fragment() {

    private var _binding: FragmentConfigurationBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val commsViewModel: CommunicationViewModel by activityViewModels()
    private val configurationViewModel: ConfigurationViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigurationBinding.inflate(inflater, container, false)

        setupPlcModelSelector()
        setupConnectionTypeSelector()
        setupStatusObservers()
        setupIpAddressEditor()
        // setupUsbControls() // La lógica USB se moverá al CommunicationViewModel si es necesario

        displayNetworkInfo()

        return binding.root
    }

    private fun setupPlcModelSelector() {
        // Variable para mantener los modelos filtrados actuales
        var currentModels = PlcRepository.getModels()

        // Función helper para actualizar el adaptador
        fun updateAdapter(models: List<PlcModel>) {
            currentModels = models
            val modelNames = models.map { it.name }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, modelNames)
            binding.autoCompletePlcModel.setAdapter(adapter)
        }

        // Configurar listener de selección
        binding.autoCompletePlcModel.setOnItemClickListener { _, _, position, _ ->
            sharedViewModel.selectPlcModel(currentModels[position])
        }

        // Observar el modelo seleccionado para actualizar la UI
        sharedViewModel.selectedPlcModel.observe(viewLifecycleOwner) {
            binding.autoCompletePlcModel.setText(it.name, false)
        }

        // Observar el tipo de conexión para filtrar modelos
        sharedViewModel.connectionType.observe(viewLifecycleOwner) { type ->
            val filteredModels = when (type) {
                ConnectionType.USB_ESP32 -> {
                    // Solo mostrar modelos ESP32
                    PlcRepository.getModels().filter { it.name.startsWith("ESP32-") }
                }
                ConnectionType.ETHERNET -> {
                    // Solo mostrar modelos PLC (sin ESP32)
                    PlcRepository.getModels().filter { !it.name.startsWith("ESP32-") }
                }
                null -> PlcRepository.getModels()
            }
            updateAdapter(filteredModels)

            // Si el modelo actual no está en la lista filtrada, seleccionar el primero
            val currentModel = sharedViewModel.selectedPlcModel.value
            if (currentModel != null && !filteredModels.contains(currentModel)) {
                filteredModels.firstOrNull()?.let { sharedViewModel.selectPlcModel(it) }
            }
        }

        // Forzar que el desplegable muestre todas las opciones al hacer clic
        binding.autoCompletePlcModel.setOnClickListener {
            (binding.autoCompletePlcModel.adapter as? ArrayAdapter<*>)?.getFilter()?.filter(null)
            binding.autoCompletePlcModel.showDropDown()
        }
    }

    private fun setupConnectionTypeSelector() {
        sharedViewModel.connectionType.observe(viewLifecycleOwner) { type ->
            when (type) {
                ConnectionType.USB_ESP32 -> {
                    binding.radioGroupConnectionType.check(R.id.radio_button_usb)
                    binding.cardUsbSettings.visibility = View.VISIBLE
                    binding.cardEthernetSettings.visibility = View.GONE
                }
                ConnectionType.ETHERNET -> {
                    binding.radioGroupConnectionType.check(R.id.radio_button_ethernet)
                    binding.cardUsbSettings.visibility = View.GONE
                    binding.cardEthernetSettings.visibility = View.VISIBLE
                }
                null -> { /* Hide everything */ }
            }
        }

        binding.radioGroupConnectionType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_button_usb -> sharedViewModel.selectConnectionType(ConnectionType.USB_ESP32)
                R.id.radio_button_ethernet -> sharedViewModel.selectConnectionType(ConnectionType.ETHERNET)
            }
        }
    }

    private fun setupIpAddressEditor() {
        // Rellenar el campo con la IP actual del ViewModel
        var currentIp = sharedViewModel.plcIpAddress.value
        if (currentIp.isNullOrEmpty()) {
            currentIp = "192.168.250.1"
            sharedViewModel.savePlcIpAddress(currentIp)
        }

        binding.editTextPlcIp.setText(currentIp)
        binding.editTextPlcIp.doAfterTextChanged {
            commsViewModel.onIpAddressChanged(it.toString())
            sharedViewModel.savePlcIpAddress(it.toString())
        }
    }

    private fun setupStatusObservers() {
        // Observar el estado desde el CommunicationViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                commsViewModel.uiState.collect { state ->
                    binding.textViewConnectionStatus.text = state.connectionStatus
                    // binding.textViewResult.text = state.result // You can add a TextView to your layout to see results
                    // Actualizar la IP si cambia en el estado (por ejemplo, al cargar)
                    if (binding.editTextPlcIp.text.toString() != state.ipAddress) {
                        binding.editTextPlcIp.setText(state.ipAddress)
                    }

                    // Change button text based on connection status
                    if (state.connectionStatus.startsWith("Connected")) {
                        binding.buttonTestConnection.text = getString(R.string.disconnect_button_label)
                    } else {
                        binding.buttonTestConnection.text = getString(R.string.connect_button_label)
                    }
                }
            }
        }

        // Observar los datos de identificación leídos del PLC
        configurationViewModel.plcModel.observe(viewLifecycleOwner) { model ->
            if (binding.autoCompletePlcModel.text.toString() != model) {
                binding.autoCompletePlcModel.setText(model, false)
            }
        }
        configurationViewModel.plcIpAddress.observe(viewLifecycleOwner) { ip ->
            if (binding.editTextPlcIp.text.toString() != ip) {
                binding.editTextPlcIp.setText(ip)
            }
        }

        binding.buttonTestConnection.setOnClickListener { // This button now acts as connect/disconnect
            if (commsViewModel.uiState.value.connectionStatus.startsWith("Connected")) {
                commsViewModel.disconnect()
            } else {
                // Obtener el tipo de conexión del SharedViewModel
                val connectionType = sharedViewModel.connectionType.value ?: ConnectionType.ETHERNET

                // Manejar conexión según tipo
                when (connectionType) {
                    ConnectionType.USB_ESP32 -> showUsbDeviceSelectionDialog()
                    ConnectionType.ETHERNET -> commsViewModel.connect(connectionType)
                }
            }
        }
    }

    private fun displayNetworkInfo() {
        // Esta función ahora es más simple y se puede llamar desde onResume
        // para asegurar que se muestra la información más reciente al entrar a la pantalla.
        CoroutineScope(Dispatchers.IO).launch {
            val networkInfo = StringBuilder()
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    if (intf.isUp && !intf.isLoopback) {
                        val addrs = Collections.list(intf.inetAddresses)
                        for (addr in addrs) {
                            if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                                if (intf.displayName.contains("eth") || intf.displayName.contains("wlan") || intf.displayName.contains("rndis")) {
                                    networkInfo.append("${intf.displayName}: ${addr.hostAddress}\n")
                                }
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                networkInfo.append("Error getting network info: ${ex.message}")
            }
            if (networkInfo.isEmpty()) {
                networkInfo.append("No active network interfaces found.")
            }

            // Actualizar la UI en el hilo principal
            withContext(Dispatchers.Main) {
                binding.textViewNetworkInterfaces.text = networkInfo.toString()
            }
        }

        // Aumentar el tamaño de la letra para mejorar la legibilidad
        binding.textViewNetworkInterfaces.textSize = 14f
    }

    override fun onResume() {
        super.onResume()
        // Refrescar la información de red cada vez que el fragmento se vuelve visible
        displayNetworkInfo()
        // Cargar la identificación del PLC para rellenar los campos
        configurationViewModel.loadPlcIdentification(commsViewModel)
    }

    /**
     * Muestra un diálogo para seleccionar un dispositivo USB Serial y conectar.
     */
    private fun showUsbDeviceSelectionDialog() {
        val dialog = UsbDeviceSelectionDialog.newInstance { driver ->
            // Usuario seleccionó un dispositivo
            val port = driver.ports.firstOrNull()
            if (port != null) {
                // Solicitar permisos y conectar
                val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
                if (usbManager.hasPermission(driver.device)) {
                    // Ya tenemos permiso, conectar directamente
                    commsViewModel.connect(ConnectionType.USB_ESP32, port)
                } else {
                    // Solicitar permiso (esto debería mostrar un diálogo del sistema)
                    // Por simplicidad, intentamos conectar de todos modos
                    // El ESP32SerialClient manejará la solicitud de permisos
                    commsViewModel.connect(ConnectionType.USB_ESP32, port)
                }
            } else {
                Toast.makeText(requireContext(), "No se pudo obtener el puerto del dispositivo", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show(childFragmentManager, "UsbDeviceSelectionDialog")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}