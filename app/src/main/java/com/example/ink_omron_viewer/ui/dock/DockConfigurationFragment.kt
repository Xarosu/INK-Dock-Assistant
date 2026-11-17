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

package com.example.ink_omron_viewer.ui.dock

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.example.ink_omron_viewer.R
import com.example.ink_omron_viewer.databinding.FragmentDockConfigurationBinding
import com.example.ink_omron_viewer.ui.CommunicationViewModel
import com.example.ink_omron_viewer.domain.ActiveMachinesViewModel
import com.example.ink_omron_viewer.ui.SharedViewModel
import com.example.ink_omron_viewer.domain.DockConfigurationViewModel

class DockConfigurationFragment : Fragment() {

    private var _binding: FragmentDockConfigurationBinding? = null
    private val binding get() = _binding!!
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val commsViewModel: CommunicationViewModel by activityViewModels()
    private val dockViewModel: DockConfigurationViewModel by activityViewModels()
    private val activeMachinesViewModel: ActiveMachinesViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDockConfigurationBinding.inflate(inflater, container, false)
        
        setupAdapters()
        setupListeners()

        dockViewModel.writeStatus.observe(viewLifecycleOwner) { status ->
            if (status.isNotEmpty()) {
                Toast.makeText(requireContext(), status, Toast.LENGTH_LONG).show()
                binding.buttonResetDockConfig.isEnabled = true
                binding.buttonSaveDockConfig.isEnabled = true
                dockViewModel.onWriteStatusShown() // Consumir el evento para que no se muestre de nuevo
            }
        }

        // --- NUEVOS OBSERVADORES ---
        // Observar la configuración cargada y actualizar TODA la UI
        dockViewModel.shelterEnabled.observe(viewLifecycleOwner) { isEnabled ->
            binding.switchShelter.isChecked = isEnabled
            updateActiveMachinesMenu()
        }

        dockViewModel.doorEnabled.observe(viewLifecycleOwner) { isEnabled ->
            binding.switchSectionalDoor.isChecked = isEnabled
            updateActiveMachinesMenu()
        }

        dockViewModel.platformIndex.observe(viewLifecycleOwner) { index ->
            val platformTypes = resources.getStringArray(R.array.platform_types)
            if (index >= 0 && index < platformTypes.size) {
                binding.autoCompletePlatform.setText(platformTypes[index], false)
                updateActiveMachinesMenu() // Actualizar menú cuando cambia el índice
            }
        }

        dockViewModel.trafficLightIndex.observe(viewLifecycleOwner) { index ->
            val trafficLightSequences = resources.getStringArray(R.array.traffic_light_sequences)
            if (index >= 0 && index < trafficLightSequences.size) {
                binding.autoCompleteTrafficLight.setText(trafficLightSequences[index], false)
                updateTrafficLightDescription(index) // Actualizar descripción
                updateActiveMachinesMenu() // Actualizar menú cuando cambia el índice
            }
        }

        // Observar número de pedido
        dockViewModel.orderNumber.observe(viewLifecycleOwner) { orderNumber ->
            if (binding.editTextOrderNumber.text.toString() != orderNumber) {
                binding.editTextOrderNumber.setText(orderNumber)
            }
        }

        // Observar posición del muelle
        dockViewModel.dockPosition.observe(viewLifecycleOwner) { position ->
            if (binding.editTextDockPosition.text.toString() != position) {
                binding.editTextDockPosition.setText(position)
            }
        }

        // Observar si hay datos de pedido para ocultar/mostrar el card
        dockViewModel.hasOrderData.observe(viewLifecycleOwner) { hasData ->
            try {
                binding.cardOrderNumber.visibility = if (hasData) View.GONE else View.VISIBLE
            } catch (e: Exception) {
                Log.e("DockConfigFragment", "Error accessing cardOrderNumber", e)
            }
        }

        // Observar si hay datos de posición del muelle para ocultar/mostrar el card
        dockViewModel.hasDockPositionData.observe(viewLifecycleOwner) { hasData ->
            try {
                binding.cardDockPosition.visibility = if (hasData) View.GONE else View.VISIBLE
            } catch (e: Exception) {
                Log.e("DockConfigFragment", "Error accessing cardDockPosition", e)
            }
        }

        return binding.root
    }

    private fun setupAdapters() {
        val platformTypes = resources.getStringArray(R.array.platform_types)
        val platformAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, platformTypes)
        binding.autoCompletePlatform.setAdapter(platformAdapter)

        val trafficLightSequences = resources.getStringArray(R.array.traffic_light_sequences)
        val trafficLightAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, trafficLightSequences)
        binding.autoCompleteTrafficLight.setAdapter(trafficLightAdapter)
    }

    private fun setupListeners() {
        binding.buttonSaveDockConfig.setOnClickListener {
            saveConfiguration()
        }

        binding.switchShelter.setOnCheckedChangeListener { _, _ -> updateActiveMachinesMenu() }
        binding.switchSectionalDoor.setOnCheckedChangeListener { _, _ -> updateActiveMachinesMenu() }

        binding.autoCompletePlatform.setOnItemClickListener { _, _, _, _ -> updateActiveMachinesMenu() }
        binding.autoCompleteTrafficLight.setOnItemClickListener { _, _, position, _ ->
            updateTrafficLightDescription(position)
            updateActiveMachinesMenu()
        }

        binding.buttonResetDockConfig.setOnClickListener {
            resetConfiguration()
        }

        // Forzar que el desplegable muestre todas las opciones al hacer clic,
        // limpiando el filtro del adaptador antes de mostrarlo.
        binding.autoCompletePlatform.setOnClickListener {
            (binding.autoCompletePlatform.adapter as? ArrayAdapter<*>)?.getFilter()?.filter(null)
            binding.autoCompletePlatform.showDropDown()
        }
        binding.autoCompleteTrafficLight.setOnClickListener {
            (binding.autoCompleteTrafficLight.adapter as? ArrayAdapter<*>)?.getFilter()?.filter(null)
            binding.autoCompleteTrafficLight.showDropDown()
        }
    }
    override fun onResume() {
        super.onResume() // Cargar la configuración actual del PLC cada vez que la pantalla se vuelve visible
        dockViewModel.loadDockConfiguration(commsViewModel) // Mantenemos la carga aquí para refrescar la pantalla si se entra
    }

    private fun updateTrafficLightDescription(position: Int) {
        val descriptionsResId = resources.getIdentifier("traffic_light_sequence_descriptions", "array", requireContext().packageName)
        if (descriptionsResId != 0) {
            val descriptions = resources.getStringArray(descriptionsResId)
            if (position < descriptions.size) {
                binding.textViewTrafficLightDescription.text = descriptions[position]
            }
        } else {
            binding.textViewTrafficLightDescription.text = ""
        }
    }

    private fun saveConfiguration() {
        // Validar que se hayan seleccionado opciones en los desplegables
        val platformText = binding.autoCompletePlatform.text.toString()
        val trafficLightText = binding.autoCompleteTrafficLight.text.toString()
        val platformIndex = resources.getStringArray(R.array.platform_types).indexOf(platformText)
        val trafficLightIndex = resources.getStringArray(R.array.traffic_light_sequences).indexOf(trafficLightText)

        if (platformIndex == -1 || trafficLightIndex == -1) {
            Toast.makeText(requireContext(), "Por favor, selecciona una opción para Plataforma y Semáforos.", Toast.LENGTH_LONG).show()
            return
        }

        // Obtener y validar número de pedido
        val orderNumber = binding.editTextOrderNumber.text.toString().trim()
        if (orderNumber.isNotEmpty()) {
            // Validar formato: 9 dígitos + "/" + 2 dígitos
            val orderPattern = Regex("^\\d{10}/\\d{2}$")
            if (!orderPattern.matches(orderNumber)) {
                Toast.makeText(requireContext(), "Formato de pedido inválido. Use: 1234567890/20", Toast.LENGTH_LONG).show()
                return
            }
        }

        // Obtener y validar posición del muelle
        val dockPosition = binding.editTextDockPosition.text.toString().trim()
        if (dockPosition.isNotEmpty()) {
            val position = dockPosition.toIntOrNull()
            if (position == null || position > 999) {
                Toast.makeText(requireContext(), "Posición debe ser un número entre 0 y 999", Toast.LENGTH_LONG).show()
                return
            }
        }

        // Obtener Modelo PLC e IP de los campos de texto correspondientes
        // Asumiendo que los IDs son `editTextPlcModel` y `editTextPlcIpAddress`
        // Si no existen, se pasarán vacíos y no se guardarán.
        // Deshabilitar el botón para evitar escrituras múltiples
        binding.buttonSaveDockConfig.isEnabled = false
        binding.buttonResetDockConfig.isEnabled = false

        // Delegar toda la lógica de escritura al ViewModel
        dockViewModel.saveDockConfiguration(
            commsViewModel = commsViewModel,
            sharedViewModel = sharedViewModel,
            shelterEnabled = binding.switchShelter.isChecked,
            doorEnabled = binding.switchSectionalDoor.isChecked,
            orderNumber = orderNumber,
            dockPosition = dockPosition,
            platformIndex = platformIndex,
            trafficLightIndex = trafficLightIndex,
        )

        updateActiveMachinesMenu() // La visibilidad se actualiza aquí también
    }

    private fun resetConfiguration() {
        // Deshabilitar botones para evitar acciones múltiples
        binding.buttonSaveDockConfig.isEnabled = false
        binding.buttonResetDockConfig.isEnabled = false

        dockViewModel.resetDockConfiguration(commsViewModel)
        // La UI se actualizará automáticamente cuando el `writeStatus` cambie y se recargue la config.
        // Forzamos una recarga para asegurar que la UI se actualiza tras el reset.

        // Ocultamos el menú ya que estamos reseteando a valores por defecto (desactivado)
        activeMachinesViewModel.updateMachineStatus(shelter = false, door = false, platform = false, trafficLight = false)
        dockViewModel.loadDockConfiguration(commsViewModel)
    }

    /**
     * Centraliza la lógica para actualizar la visibilidad del menú de máquinas activas.
     */
    private fun updateActiveMachinesMenu() {
 
       val platformIndex = resources.getStringArray(R.array.platform_types).indexOf(binding.autoCompletePlatform.text.toString())
        val trafficLightIndex = resources.getStringArray(R.array.traffic_light_sequences).indexOf(binding.autoCompleteTrafficLight.text.toString())

        activeMachinesViewModel.updateMachineStatus(
            shelter = binding.switchShelter.isChecked,
            door = binding.switchSectionalDoor.isChecked,
        platform = platformIndex > 0, // Activo si no es la primera opción ("Ninguna")
            trafficLight = trafficLightIndex > 0 // Activo si no es la primera opción ("Ninguna")
        )
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
