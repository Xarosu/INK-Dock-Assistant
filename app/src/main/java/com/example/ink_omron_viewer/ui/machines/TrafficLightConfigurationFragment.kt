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
import com.example.ink_omron_viewer.databinding.FragmentMachineTrafficLightBinding
import com.example.ink_omron_viewer.domain.TrafficLightViewModel
import com.example.ink_omron_viewer.ui.CommunicationViewModel
import com.google.android.material.switchmaterial.SwitchMaterial

class TrafficLightConfigurationFragment : Fragment() {
    private var _binding: FragmentMachineTrafficLightBinding? = null
    private val binding get() = _binding!!

    private val commsViewModel: CommunicationViewModel by activityViewModels()
    private val trafficLightViewModel: TrafficLightViewModel by activityViewModels()

    private lateinit var switches: List<SwitchMaterial>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMachineTrafficLightBinding.inflate(inflater, container, false)

        switches = listOf(
            binding.switch1,
            binding.switch2,
            binding.switch3,
            binding.switch4,
            binding.switch5
        )

        setupObservers()
        setupListeners()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        trafficLightViewModel.loadTrafficLightData(commsViewModel)
    }

    private fun setupObservers() {
        trafficLightViewModel.timerValue.observe(viewLifecycleOwner) { value ->
            binding.editTextTimer.setText(value.toString())
        }

        trafficLightViewModel.counterValues.observe(viewLifecycleOwner) { counters ->
            if (counters.size >= 3) {
                binding.textViewCounter1.text = "Contador 1 (D142): ${counters[0]}"
                binding.textViewCounter2.text = "Contador 2 (D143): ${counters[1]}"
                binding.textViewCounter3.text = "Contador 3 (D144): ${counters[2]}"
            }
        }

        trafficLightViewModel.writeStatus.observe(viewLifecycleOwner) { status ->
            if (status.isNotEmpty()) {
                Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show()
                trafficLightViewModel.onWriteStatusShown()
            }
        }

        trafficLightViewModel.serialNumber.observe(viewLifecycleOwner) { serialNumber ->
            if (binding.editTextSerialNumber.text.toString() != serialNumber) {
                binding.editTextSerialNumber.setText(serialNumber)
            }
        }

        trafficLightViewModel.hasSerialData.observe(viewLifecycleOwner) { hasData ->
            binding.cardSerialNumber.visibility = if (hasData) View.GONE else View.VISIBLE
        }

        trafficLightViewModel.switchStates.observe(viewLifecycleOwner) { states ->
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
            trafficLightViewModel.saveTimerValue(commsViewModel, value)
        }

        binding.buttonResetCounter1.setOnClickListener {
            trafficLightViewModel.resetCounter(commsViewModel, 0)
        }

        binding.buttonResetCounter2.setOnClickListener {
            trafficLightViewModel.resetCounter(commsViewModel, 1)
        }

        binding.buttonResetCounter3.setOnClickListener {
            trafficLightViewModel.resetCounter(commsViewModel, 2)
        }

        binding.buttonSaveSwitches.setOnClickListener {
            val currentStates = switches.map { it.isChecked }
            trafficLightViewModel.saveSwitchStates(commsViewModel, currentStates)
        }

        binding.buttonSaveSerial.setOnClickListener {
            val serialNumber = binding.editTextSerialNumber.text.toString().trim()
            if (serialNumber.isNotEmpty()) {
                trafficLightViewModel.saveSerialNumber(commsViewModel, serialNumber)
            } else {
                Toast.makeText(requireContext(), "Por favor ingrese un número de serie", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}