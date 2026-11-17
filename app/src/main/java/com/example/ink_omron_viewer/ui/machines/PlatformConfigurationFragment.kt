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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.ink_omron_viewer.R
import com.example.ink_omron_viewer.databinding.FragmentMachinePlatformBinding
import com.example.ink_omron_viewer.domain.DockConfigurationViewModel
import com.example.ink_omron_viewer.domain.PlatformViewModel
import com.example.ink_omron_viewer.ui.CommunicationViewModel
import com.google.android.material.switchmaterial.SwitchMaterial

class PlatformConfigurationFragment : Fragment() {
    private var _binding: FragmentMachinePlatformBinding? = null
    private val binding get() = _binding!!

    private val commsViewModel: CommunicationViewModel by activityViewModels()
    private val dockViewModel: DockConfigurationViewModel by activityViewModels()
    private val platformViewModel: PlatformViewModel by activityViewModels()

    private lateinit var switches: List<SwitchMaterial>
    private var currentPlatformIndex: Int = 0

    companion object {
        private const val TAG = "PlatformConfigFrag"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMachinePlatformBinding.inflate(inflater, container, false)

        switches = listOf(
            binding.switch1, binding.switch2, binding.switch3, binding.switch4,
            binding.switch5, binding.switch6, binding.switch7
        )

        setupObservers()
        setupListeners()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        // Primero cargamos los datos de temporizador y contadores
        platformViewModel.loadTimerAndCounters(commsViewModel)
        // Luego forzamos la carga de la config del muelle para saber qué plataforma está seleccionada
        Log.d(TAG, "Loading dock configuration to get platform index")
        dockViewModel.loadDockConfiguration(commsViewModel) // Esto disparará el observador de platformIndex

        // Si después de 2 segundos no tenemos platformIndex, asumir plataforma por defecto
        binding.root.postDelayed({
            if (currentPlatformIndex == 0) {
                Log.w(TAG, "Platform index not loaded after timeout, assuming default platform P2 (index=2)")
                // Simular que llegó el platformIndex = 2 (P2)
                currentPlatformIndex = 2
                configureUiForPlatform(2)
            }
        }, 2000)
    }

    private fun setupObservers() {
        // Observa el tipo de plataforma seleccionado para configurar la UI
        dockViewModel.platformIndex.observe(viewLifecycleOwner) { platformIndex ->
            Log.d(TAG, "platformIndex changed: $platformIndex")
            currentPlatformIndex = platformIndex
            configureUiForPlatform(platformIndex)
            // Una vez que sabemos qué plataforma es, cargamos el estado de sus switches
            platformViewModel.loadSwitchStates(commsViewModel)
        }

        // Observadores para el temporizador y contadores
        platformViewModel.timerValue.observe(viewLifecycleOwner) { value ->
            Log.d(TAG, "timerValue changed: $value")
            binding.editTextTimer.setText(value.toString())
        }

        platformViewModel.timer2Value.observe(viewLifecycleOwner) { value ->
            Log.d(TAG, "timer2Value changed: $value")
            binding.editTextTimer2.setText(value.toString())
        }

        platformViewModel.counterValues.observe(viewLifecycleOwner) { counters ->
            Log.d(TAG, "counterValues changed: size=${counters.size}, currentPlatformIndex=$currentPlatformIndex, values=$counters")
            // Actualizar contadores si ya se configuró la plataforma
            if (currentPlatformIndex > 0 && counters.isNotEmpty()) {
                Log.d(TAG, "Updating counter labels for platform $currentPlatformIndex")
                updateCounterLabels(currentPlatformIndex, counters)
            } else {
                Log.w(TAG, "NOT updating counters: platformIndex=$currentPlatformIndex, counters.isEmpty=${counters.isEmpty()}")
            }
        }

        // Observa los estados de los switches leídos del PLC
        platformViewModel.switchStates.observe(viewLifecycleOwner) { states ->
            states.forEachIndexed { index, isActive ->
                if (index < switches.size) {
                    switches[index].isChecked = isActive
                }
            }
        }

        // Observa el estado de la escritura
        platformViewModel.writeStatus.observe(viewLifecycleOwner) { status ->
            if (status.isNotEmpty()) {
                Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show()
                platformViewModel.onWriteStatusShown()
            }
        }

        platformViewModel.serialNumber.observe(viewLifecycleOwner) { serialNumber ->
            if (binding.editTextSerialNumber.text.toString() != serialNumber) {
                binding.editTextSerialNumber.setText(serialNumber)
            }
        }

        platformViewModel.hasSerialData.observe(viewLifecycleOwner) { hasData ->
            binding.cardSerialNumber.visibility = if (hasData) View.GONE else View.VISIBLE
        }
    }

    private fun setupListeners() {
        binding.buttonSavePlatform.setOnClickListener {
            val currentStates = switches.map { it.isChecked }
            platformViewModel.saveSwitchStates(commsViewModel, currentStates)
        }

        binding.buttonSaveTimer.setOnClickListener {
            val value = binding.editTextTimer.text.toString().toShortOrNull() ?: 0
            platformViewModel.saveTimerValue(commsViewModel, value)
        }

        binding.buttonSaveTimer2.setOnClickListener {
            val value = binding.editTextTimer2.text.toString().toShortOrNull() ?: 0
            platformViewModel.saveTimer2Value(commsViewModel, value)
        }

        binding.buttonResetCounter1.setOnClickListener {
            platformViewModel.resetCounter(commsViewModel, 0)
        }
        binding.buttonResetCounter2.setOnClickListener {
            platformViewModel.resetCounter(commsViewModel, 1)
        }
        binding.buttonResetCounter3.setOnClickListener {
            platformViewModel.resetCounter(commsViewModel, 2)
        }
        binding.buttonResetCounter4.setOnClickListener {
            platformViewModel.resetCounter(commsViewModel, 3)
        }
        binding.buttonResetCounter5.setOnClickListener {
            platformViewModel.resetCounter(commsViewModel, 4)
        }

        binding.buttonSaveSerial.setOnClickListener {
            val serialNumber = binding.editTextSerialNumber.text.toString().trim()
            if (serialNumber.isNotEmpty()) {
                platformViewModel.saveSerialNumber(commsViewModel, serialNumber)
            } else {
                Toast.makeText(requireContext(), "Por favor ingrese un número de serie", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configureUiForPlatform(platformIndex: Int) {
        Log.d(TAG, "configureUiForPlatform called with platformIndex=$platformIndex")
        val platformTypes = resources.getStringArray(R.array.platform_types)
        val platformName = platformTypes.getOrNull(platformIndex) ?: "Desconocida"
        binding.textViewPlatformTitle.text = "Configuración de $platformName"

        when (platformIndex) {
            1 -> configureP1()
            2 -> configureP2()
            3 -> configureP3()
            4 -> configureP4()
            else -> {
                // Ninguna plataforma seleccionada
                switches.forEach { it.visibility = View.GONE }
            }
        }

        // Forzar actualización de contadores si ya están cargados
        val currentCounters = platformViewModel.counterValues.value
        Log.d(TAG, "After configuring platform, checking for existing counters: $currentCounters")
        currentCounters?.let { counters ->
            if (counters.isNotEmpty() && platformIndex > 0) {
                Log.d(TAG, "Found existing counters, forcing update")
                updateCounterLabels(platformIndex, counters)
            } else {
                Log.d(TAG, "Counters empty or platformIndex=0, skipping forced update")
            }
        } ?: Log.d(TAG, "No counter values available yet")
    }

    private fun configureP1() {
        // P1: 3 switches
        binding.switch1.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p1_switch_1) }
        binding.switch2.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p1_switch_2) }
        binding.switch3.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p1_switch_3) }
        binding.switch4.visibility = View.GONE
        binding.switch5.visibility = View.GONE
        binding.switch6.visibility = View.GONE
        binding.switch7.visibility = View.GONE

        // Actualizar label del timer
        binding.textInputLayoutTimer.hint = getString(R.string.platform_p1_timer_label)

        // Ocultar timer2 y contadores extras
        binding.textInputLayoutTimer2.visibility = View.GONE
        binding.buttonSaveTimer2.visibility = View.GONE
        binding.counter4Layout.visibility = View.GONE
        binding.counter5Layout.visibility = View.GONE

        // Actualizar etiquetas de contadores con valores actuales si ya se cargaron
        val counters = platformViewModel.counterValues.value
        if (counters != null && counters.size >= 6) {
            updateP1Counters(counters)
        } else {
            // Inicializar etiquetas de contadores vacíos
            binding.textViewCounter1.text = "${getString(R.string.platform_p1_counter_1_label)}: -"
            binding.textViewCounter2.text = "${getString(R.string.platform_p1_counter_2_label)}: -"
            binding.textViewCounter3.text = "${getString(R.string.platform_p1_counter_3_label)}: -"
        }
    }

    private fun configureP2() {
        // P2: 4 switches
        binding.switch1.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p2_switch_1) }
        binding.switch2.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p2_switch_2) }
        binding.switch3.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p2_switch_3) }
        binding.switch4.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p2_switch_4) }
        binding.switch5.visibility = View.GONE
        binding.switch6.visibility = View.GONE
        binding.switch7.visibility = View.GONE

        // Actualizar labels de los timers
        binding.textInputLayoutTimer.hint = getString(R.string.platform_p2_timer_1_label)
        binding.textInputLayoutTimer2.visibility = View.VISIBLE
        binding.textInputLayoutTimer2.hint = getString(R.string.platform_p2_timer_2_label)
        binding.buttonSaveTimer2.visibility = View.VISIBLE

        // Mostrar contadores adicionales
        binding.counter4Layout.visibility = View.VISIBLE
        binding.counter5Layout.visibility = View.VISIBLE

        // Actualizar etiquetas de contadores con valores actuales si ya se cargaron
        val counters = platformViewModel.counterValues.value
        if (counters != null && counters.size >= 6) {
            updateP2Counters(counters)
        } else {
            // Inicializar etiquetas de contadores vacíos
            binding.textViewCounter1.text = "${getString(R.string.platform_p2_counter_1_label)}: -"
            binding.textViewCounter2.text = "${getString(R.string.platform_p2_counter_2_label)}: -"
            binding.textViewCounter3.text = "${getString(R.string.platform_p2_counter_3_label)}: -"
            binding.textViewCounter4.text = "${getString(R.string.platform_p2_counter_4_label)}: -"
            binding.textViewCounter5.text = "${getString(R.string.platform_p2_counter_5_label)}: -"
        }
    }

    private fun configureP3() {
        // P3: 2 switches
        binding.switch1.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p3_switch_1) }
        binding.switch2.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p3_switch_2) }
        binding.switch3.visibility = View.GONE
        binding.switch4.visibility = View.GONE
        binding.switch5.visibility = View.GONE
        binding.switch6.visibility = View.GONE
        binding.switch7.visibility = View.GONE

        // Actualizar label del timer
        binding.textInputLayoutTimer.hint = getString(R.string.platform_p3_timer_label)

        // Ocultar timer2 y contadores extras
        binding.textInputLayoutTimer2.visibility = View.GONE
        binding.buttonSaveTimer2.visibility = View.GONE
        binding.counter4Layout.visibility = View.GONE
        binding.counter5Layout.visibility = View.GONE

        // Actualizar etiquetas de contadores con valores actuales si ya se cargaron
        val counters = platformViewModel.counterValues.value
        if (counters != null && counters.size >= 6) {
            updateP3Counters(counters)
        } else {
            // Inicializar etiquetas de contadores vacíos
            binding.textViewCounter1.text = "${getString(R.string.platform_p3_counter_1_label)}: -"
            binding.textViewCounter2.text = "${getString(R.string.platform_p3_counter_2_label)}: -"
            binding.textViewCounter3.text = "${getString(R.string.platform_p3_counter_3_label)}: -"
        }
    }

    private fun configureP4() {
        // P4: 7 switches (genérico - pendiente definir)
        binding.switch1.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p4_switch_1) }
        binding.switch2.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p4_switch_2) }
        binding.switch3.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p4_switch_3) }
        binding.switch4.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p4_switch_4) }
        binding.switch5.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p4_switch_5) }
        binding.switch6.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p4_switch_6) }
        binding.switch7.apply { visibility = View.VISIBLE; text = getString(R.string.platform_p4_switch_7) }

        // Actualizar label del timer
        binding.textInputLayoutTimer.hint = getString(R.string.platform_p4_timer_label)

        // Ocultar timer2 y contadores extras
        binding.textInputLayoutTimer2.visibility = View.GONE
        binding.buttonSaveTimer2.visibility = View.GONE
        binding.counter4Layout.visibility = View.GONE
        binding.counter5Layout.visibility = View.GONE

        // Actualizar etiquetas de contadores con valores actuales si ya se cargaron
        val counters = platformViewModel.counterValues.value
        if (counters != null && counters.size >= 6) {
            updateP4Counters(counters)
        } else {
            // Inicializar etiquetas de contadores vacíos
            binding.textViewCounter1.text = "${getString(R.string.platform_p4_counter_1_label)}: -"
            binding.textViewCounter2.text = "${getString(R.string.platform_p4_counter_2_label)}: -"
            binding.textViewCounter3.text = "${getString(R.string.platform_p4_counter_3_label)}: -"
        }
    }

    private fun updateCounterLabels(platformIndex: Int, counters: List<Short>) {
        when (platformIndex) {
            1 -> updateP1Counters(counters)
            2 -> updateP2Counters(counters)
            3 -> updateP3Counters(counters)
            4 -> updateP4Counters(counters)
            else -> {
                binding.textViewCounter1.text = "-"
                binding.textViewCounter2.text = "-"
                binding.textViewCounter3.text = "-"
            }
        }
    }

    private fun updateP1Counters(counters: List<Short>) {
        if (counters.size >= 6) {
            // P1: D133 (maniobras subida), D137 (horas desde mantenimiento), D138 (horas totales)
            binding.textViewCounter1.text = "${getString(R.string.platform_p1_counter_1_label)}: ${counters[0]}"
            binding.textViewCounter2.text = "${getString(R.string.platform_p1_counter_2_label)}: ${counters[4]}"
            binding.textViewCounter3.text = "${getString(R.string.platform_p1_counter_3_label)}: ${counters[5]}"
        }
    }

    private fun updateP2Counters(counters: List<Short>) {
        if (counters.size >= 6) {
            // P2: D133 (subida), D134 (sacar uña), D135 (recoger uña), D137 (horas desde mantenimiento), D138 (horas totales bomba)
            // Se omite D136 (KM1) para priorizar las horas totales de mantenimiento
            binding.textViewCounter1.text = "${getString(R.string.platform_p2_counter_1_label)}: ${counters[0]}"
            binding.textViewCounter2.text = "${getString(R.string.platform_p2_counter_2_label)}: ${counters[1]}"
            binding.textViewCounter3.text = "${getString(R.string.platform_p2_counter_3_label)}: ${counters[2]}"
            binding.textViewCounter4.text = "${getString(R.string.platform_p2_counter_4_label)}: ${counters[4]}"
            binding.textViewCounter5.text = "${getString(R.string.platform_p2_counter_5_label)}: ${counters[5]}"
        }
    }

    private fun updateP3Counters(counters: List<Short>) {
        if (counters.size >= 6) {
            // P3: D133 (maniobras subida), D137 (horas desde mantenimiento), D138 (horas totales)
            // Se omite D134 (maniobras bajada) para priorizar ambos contadores de mantenimiento
            binding.textViewCounter1.text = "${getString(R.string.platform_p3_counter_1_label)}: ${counters[0]}"
            binding.textViewCounter2.text = "${getString(R.string.platform_p3_counter_2_label)}: ${counters[4]}"
            binding.textViewCounter3.text = "${getString(R.string.platform_p3_counter_3_label)}: ${counters[5]}"
        }
    }

    private fun updateP4Counters(counters: List<Short>) {
        if (counters.size >= 6) {
            // P4: D133 (contador 1), D137 (horas desde mantenimiento), D138 (horas totales)
            // Se omite D134 (contador 2) para priorizar ambos contadores de mantenimiento
            binding.textViewCounter1.text = "${getString(R.string.platform_p4_counter_1_label)}: ${counters[0]}"
            binding.textViewCounter2.text = "${getString(R.string.platform_p4_counter_2_label)}: ${counters[4]}"
            binding.textViewCounter3.text = "${getString(R.string.platform_p4_counter_3_label)}: ${counters[5]}"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}