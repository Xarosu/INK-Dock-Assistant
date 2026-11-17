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

package com.example.ink_omron_viewer.ui.machines

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.ink_omron_viewer.R
import com.example.ink_omron_viewer.databinding.FragmentMachineDoorBinding
import com.example.ink_omron_viewer.domain.DoorViewModel
import com.example.ink_omron_viewer.ui.CommunicationViewModel
import com.example.ink_omron_viewer.ui.ConnectionType
import com.example.ink_omron_viewer.ui.SharedViewModel
import com.google.android.material.switchmaterial.SwitchMaterial

class DoorConfigurationFragment : Fragment() {
    private var _binding: FragmentMachineDoorBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val commsViewModel: CommunicationViewModel by activityViewModels()
    private val doorViewModel: DoorViewModel by activityViewModels()

    private lateinit var switches: List<SwitchMaterial>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMachineDoorBinding.inflate(inflater, container, false)

        switches = listOf(
            binding.switch1, binding.switch2, binding.switch3,
            binding.switch4
        )

        setupObservers()
        setupListeners()

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        // Observar el tipo de conexión para ocultar puertas en modo ESP32
        sharedViewModel.connectionType.observe(viewLifecycleOwner) { type ->
            when (type) {
                ConnectionType.USB_ESP32 -> {
                    // Ocultar todo el contenido y mostrar mensaje
                    binding.scrollViewContent.visibility = View.GONE
                    binding.textViewEsp32Warning.visibility = View.VISIBLE
                    binding.textViewEsp32Warning.text = getString(R.string.esp32_not_available_door)
                }
                ConnectionType.ETHERNET -> {
                    // Mostrar contenido normal
                    binding.scrollViewContent.visibility = View.VISIBLE
                    binding.textViewEsp32Warning.visibility = View.GONE
                    doorViewModel.loadDoorData(commsViewModel)
                }
                null -> {
                    // Estado inicial, mostrar contenido
                    binding.scrollViewContent.visibility = View.VISIBLE
                    binding.textViewEsp32Warning.visibility = View.GONE
                }
            }
        }
    }

    private fun setupObservers() {
        doorViewModel.timerValue.observe(viewLifecycleOwner) { value ->
            binding.editTextTimer.setText(value.toString())
        }

        doorViewModel.counterValues.observe(viewLifecycleOwner) { counters ->
            if (counters.size >= 4) {
                // Mostrar: D121 (maniobras subida), D123 (horas desde mantenimiento), D124 (horas totales)
                binding.textViewCounter1.text = "${getString(R.string.door_counter_1_label)}: ${counters[0]}"
                binding.textViewCounter2.text = "${getString(R.string.door_counter_3_label)}: ${counters[2]}"
                binding.textViewCounter3.text = "${getString(R.string.door_counter_4_label)}: ${counters[3]}"
            }
        }

        doorViewModel.writeStatus.observe(viewLifecycleOwner) { status ->
            if (status.isNotEmpty()) {
                Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show()
                doorViewModel.onWriteStatusShown()
            }
        }

        doorViewModel.serialNumber.observe(viewLifecycleOwner) { serialNumber ->
            if (binding.editTextSerialNumber.text.toString() != serialNumber) {
                binding.editTextSerialNumber.setText(serialNumber)
            }
        }

        doorViewModel.hasSerialData.observe(viewLifecycleOwner) { hasData ->
            binding.cardSerialNumber.visibility = if (hasData) View.GONE else View.VISIBLE
        }

        doorViewModel.switchStates.observe(viewLifecycleOwner) { states ->
            states.forEachIndexed { index, isActive ->
                if (index < switches.size) {
                    switches[index].isChecked = isActive
                }
            }
        }
    }

    private fun setupListeners() {
        binding.buttonSaveTimer.setOnClickListener {
            val value = binding.editTextTimer.text.toString().toShortOrNull() ?: 0
            doorViewModel.saveTimerValue(commsViewModel, value)
        }
        binding.buttonResetCounter1.setOnClickListener {
            doorViewModel.resetCounter(commsViewModel, 0)
        }
        binding.buttonResetCounter2.setOnClickListener {
            doorViewModel.resetCounter(commsViewModel, 1)
        }
        binding.buttonResetCounter3.setOnClickListener {
            doorViewModel.resetCounter(commsViewModel, 2)
        }

        binding.buttonSaveSerial.setOnClickListener {
            val serialNumber = binding.editTextSerialNumber.text.toString().trim()
            if (serialNumber.isNotEmpty()) {
                doorViewModel.saveSerialNumber(commsViewModel, serialNumber)
            } else {
                Toast.makeText(requireContext(), "Por favor ingrese un número de serie", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonSaveSwitches.setOnClickListener {
            val currentStates = switches.map { it.isChecked }
            doorViewModel.saveSwitchStates(commsViewModel, currentStates)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}