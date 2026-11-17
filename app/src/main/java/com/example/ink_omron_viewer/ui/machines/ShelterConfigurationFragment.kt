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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.example.ink_omron_viewer.R
import com.example.ink_omron_viewer.databinding.FragmentMachineShelterBinding
import com.example.ink_omron_viewer.domain.ShelterViewModel
import com.example.ink_omron_viewer.ui.CommunicationViewModel
import com.example.ink_omron_viewer.ui.ConnectionType
import com.example.ink_omron_viewer.ui.SharedViewModel
import com.google.android.material.switchmaterial.SwitchMaterial

class ShelterConfigurationFragment : Fragment() {
    private var _binding: FragmentMachineShelterBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val commsViewModel: CommunicationViewModel by activityViewModels()
    private val shelterViewModel: ShelterViewModel by viewModels()

    private lateinit var switches: List<SwitchMaterial>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMachineShelterBinding.inflate(inflater, container, false)

        switches = listOf(
            binding.switch1, binding.switch2
        )

        setupObservers()
        setupListeners()
        return binding.root
    }

    override fun onResume() {
        super.onResume()

        // Observar el tipo de conexión para ocultar abrigos en modo ESP32
        sharedViewModel.connectionType.observe(viewLifecycleOwner) { type ->
            when (type) {
                ConnectionType.USB_ESP32 -> {
                    // Ocultar todo el contenido y mostrar mensaje
                    binding.scrollViewContent.visibility = View.GONE
                    binding.textViewEsp32Warning.visibility = View.VISIBLE
                    binding.textViewEsp32Warning.text = getString(R.string.esp32_not_available_shelter)
                }
                ConnectionType.ETHERNET -> {
                    // Mostrar contenido normal
                    binding.scrollViewContent.visibility = View.VISIBLE
                    binding.textViewEsp32Warning.visibility = View.GONE
                    shelterViewModel.loadShelterData(commsViewModel)
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
        shelterViewModel.timerValue.observe(viewLifecycleOwner) { value ->
            binding.editTextTimer.setText(value.toString())
        }

        shelterViewModel.counterValues.observe(viewLifecycleOwner) { counters ->
            if (counters.size >= 2) {
                binding.textViewCounter1.text = "${getString(R.string.shelter_counter_1_label)}: ${counters[0]}"
                binding.textViewCounter2.text = "${getString(R.string.shelter_counter_2_label)}: ${counters[1]}"
                binding.textViewCounter3.text = "-"
            }
        }

        shelterViewModel.serialNumber.observe(viewLifecycleOwner) { serialNumber ->
            if (binding.editTextSerialNumber.text.toString() != serialNumber) {
                binding.editTextSerialNumber.setText(serialNumber)
            }
        }

        shelterViewModel.hasSerialData.observe(viewLifecycleOwner) { hasData ->
            binding.cardSerialNumber.visibility = if (hasData) View.GONE else View.VISIBLE
        }

        shelterViewModel.switchStates.observe(viewLifecycleOwner) { states ->
            states.forEachIndexed { index, isActive ->
                if (index < switches.size) {
                    switches[index].isChecked = isActive
                }
            }
        }

        shelterViewModel.writeStatus.observe(viewLifecycleOwner) { status ->
            if (status.isNotEmpty()) {
                Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show()
                shelterViewModel.onWriteStatusShown()
            }
        }
    }

    private fun setupListeners() {
        binding.buttonSaveTimer.setOnClickListener {
            val value = binding.editTextTimer.text.toString().toShortOrNull() ?: 0
            shelterViewModel.saveTimerValue(commsViewModel, value)
        }

        binding.buttonResetCounter1.setOnClickListener {
            shelterViewModel.resetCounter(commsViewModel, 0)
        }
        binding.buttonResetCounter2.setOnClickListener {
            shelterViewModel.resetCounter(commsViewModel, 1)
        }
        binding.buttonResetCounter3.setOnClickListener {
            shelterViewModel.resetCounter(commsViewModel, 2)
        }

        binding.buttonSaveSerial.setOnClickListener {
            val serialNumber = binding.editTextSerialNumber.text.toString().trim()
            if (serialNumber.isNotEmpty()) {
                shelterViewModel.saveSerialNumber(commsViewModel, serialNumber)
            } else {
                Toast.makeText(requireContext(), "Por favor ingrese un número de serie", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonSaveSwitches.setOnClickListener {
            val currentStates = switches.map { it.isChecked }
            shelterViewModel.saveSwitchStates(commsViewModel, currentStates)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}