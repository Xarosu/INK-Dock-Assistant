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

package com.example.ink_omron_viewer.ui.synoptic

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.ink_omron_viewer.R
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.ink_omron_viewer.databinding.FragmentSynopticBinding
import com.example.ink_omron_viewer.databinding.ItemIoGroupBinding
import com.example.ink_omron_viewer.databinding.ItemLedBinding
import com.example.ink_omron_viewer.domain.FinsMemoryArea
import com.example.ink_omron_viewer.domain.PlcModel
import com.example.ink_omron_viewer.domain.SynopticViewModel
import com.example.ink_omron_viewer.ui.CommunicationViewModel
import com.example.ink_omron_viewer.ui.ConnectionType
import com.example.ink_omron_viewer.ui.SharedViewModel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.launch
import kotlin.math.ceil

class SynopticFragment : Fragment() {

    private var _binding: FragmentSynopticBinding? = null
    private val binding get() = _binding!!

    private val ledViewMap = mutableMapOf<String, ImageView>()
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val commsViewModel: CommunicationViewModel by activityViewModels() // Use the same instance
    private val synopticViewModel: SynopticViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSynopticBinding.inflate(inflater, container, false)
        val root: View = binding.root

        sharedViewModel.selectedPlcModel.observe(viewLifecycleOwner) { plcModel ->
            plcModel?.let { model -> populateUiForPlc(model) }
        }

        // Observe connection status from the new CommunicationViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                commsViewModel.uiState.collect { state ->
                    val isConnected = state.connectionStatus.startsWith("Connected")
                    if (isConnected) synopticViewModel.startPolling() else synopticViewModel.stopPolling()
                }
            }
        }

        // El pollEvent es un "timbre" que suena cada X segundos.
        // Cada vez que suena, realizamos una lectura, sin comprobar ningún valor.
        synopticViewModel.pollEvent.observe(viewLifecycleOwner) {
            synopticViewModel.pollPlcData(commsViewModel)
        }

        // Observamos el resultado de la lectura de IO y actualizamos los LEDs
        synopticViewModel.ioStatus.observe(viewLifecycleOwner) { values ->
            updateLedViews(values)
        }

        binding.buttonReadMemory.setOnClickListener {
            readMemory()
        }

        return root
    }

    override fun onResume() {
        super.onResume()
        // No iniciar el sondeo aquí directamente.
        // En modo USB ESP32, se inicia cuando se conecta.
        // En modo Ethernet, se inicia cuando el estado de conexión es "Connected".
        if (sharedViewModel.connectionType.value == ConnectionType.USB_ESP32 && sharedViewModel.isClientReady.value == true) {
            synopticViewModel.startPolling()
        }
    }

    override fun onPause() {
        super.onPause()
        synopticViewModel.stopPolling()
    }

    private fun readMemory() {
        val addressString = binding.editTextMemoryAddress.text.toString().uppercase()
        if (addressString.isBlank()) {
            Toast.makeText(requireContext(), "Please enter an address", Toast.LENGTH_SHORT).show()
            return
        }

        val parsedAddress = synopticViewModel.parseMemoryAddress(addressString)
        if (parsedAddress == null) {
            binding.textViewMemoryValue.text = "Invalid address format."
            return
        }

        binding.textViewMemoryValue.text = "Reading $addressString..."

        // Lanzamos una corrutina para realizar la operación de red
        viewLifecycleOwner.lifecycleScope.launch {
            // Siempre leemos una palabra (word) para la dirección solicitada
            val result = commsViewModel.readWords(parsedAddress.memoryArea.toInt(), parsedAddress.address, 1)

            result.onSuccess { data ->
                if (data.isNotEmpty()) {
                    val wordValue = data[0]
                    // Comprobamos si se pidió un bit específico o la palabra completa
                    val displayValue = if (parsedAddress.bit != null) {
                        // Extraemos el valor del bit (0 o 1)
                        (wordValue.toInt() shr parsedAddress.bit) and 1
                    } else {
                        // Mostramos el valor de la palabra completa
                        wordValue
                    }
                    binding.textViewMemoryValue.text = "Valor: $displayValue"
                } else {
                    binding.textViewMemoryValue.text = "Error: No se recibieron datos."
                }
            }.onFailure { error ->
                binding.textViewMemoryValue.text = "Error: ${error.message}"
            }
        }
    }

    private fun performPlcRead() {
        // Esta función ahora está delegada al SynopticViewModel
        synopticViewModel.pollPlcData(commsViewModel)
    }

    private fun updateLedViews(values: ShortArray) {
        Log.d("SynopticFragment", "updateLedViews called with ${values.size} words, ledViewMap has ${ledViewMap.size} LEDs")

        // Iteramos sobre el mapa de LEDs que creamos al construir la UI
        for ((address, imageView) in ledViewMap) {
            // Parseamos la dirección del LED, por ejemplo "I0.05" o "Q1.03"
            val type = address.first() // 'I' o 'Q'
            val addressPart = address.substring(1) // "0.05" o "23"

            // Intentar parsear como formato "canal.bit" (ej: "0.05", "1.11")
            val parts = addressPart.split(".")
            var wordIndex: Int
            var bitIndex: Int

            if (parts.size == 2) {
                // Formato "canal.bit" - estilo OMRON
                val channel = parts[0].toIntOrNull() ?: continue
                bitIndex = parts[1].toIntOrNull() ?: continue

                // El canal corresponde directamente al índice de palabra en el array
                // Para salidas (Q), el canal YA incluye el 100 (ej: Q100.03 -> canal=100)
                // Para entradas (I), el canal es directo (ej: I0.05 -> canal=0)
                wordIndex = channel
            } else {
                // Formato legacy "absoluteBit" (para compatibilidad)
                val absoluteBit = addressPart.toIntOrNull() ?: continue
                wordIndex = absoluteBit / 16
                bitIndex = absoluteBit % 16

                if (type == 'Q') {
                    wordIndex += 100
                }
            }

            if (wordIndex < values.size && bitIndex < 16) {
                val wordValue = values[wordIndex]
                // Comprobamos si el bit específico está a 1
                val isLedOn = (wordValue.toInt() shr bitIndex) and 1 == 1

                // Log detallado para depuración
                if (address == "I0.00" || address == "I0.10" || address == "I1.00" ||
                    address == "Q100.00" || address == "Q100.07" || address == "Q101.00") {
                    Log.d("SynopticFragment", "$address -> word[$wordIndex]=0x${wordValue.toString(16)}, bit=$bitIndex, isOn=$isLedOn")
                }

                imageView.isActivated = isLedOn
            } else {
                Log.w("SynopticFragment", "Address $address out of bounds: wordIndex=$wordIndex, bitIndex=$bitIndex, values.size=${values.size}")
            }
        }
    }

    private fun populateUiForPlc(plc: PlcModel) {
        // Limpiar vistas anteriores y el mapa de referencias
        binding.inputsContainer.removeAllViews()
        binding.outputsContainer.removeAllViews()
        ledViewMap.clear()

        Log.d("SynopticFragment", "Populating UI for ${plc.name}")

        // Generar vistas para las Entradas usando canales
        val inputChannels = plc.getInputChannels()
        Log.d("SynopticFragment", "Input channels: ${inputChannels.joinToString { "CH${it.channel}:${it.bitCount}" }}")
        generateIoViewsFromChannels(
            container = binding.inputsContainer,
            channels = inputChannels,
            pointType = getString(R.string.io_type_inputs),
            addressPrefix = "I"
        )

        // Generar vistas para las Salidas usando canales
        val outputChannels = plc.getOutputChannels()
        Log.d("SynopticFragment", "Output channels: ${outputChannels.joinToString { "CH${it.channel}:${it.bitCount}" }}")
        generateIoViewsFromChannels(
            container = binding.outputsContainer,
            channels = outputChannels,
            pointType = getString(R.string.io_type_outputs),
            addressPrefix = "Q"
        )
    }

    private fun generateIoViewsFromChannels(
        container: LinearLayout,
        channels: List<com.example.ink_omron_viewer.domain.IoChannel>,
        pointType: String,
        addressPrefix: String
    ) {
        if (channels.isEmpty()) return

        val inflater = LayoutInflater.from(requireContext())

        for (channel in channels) {
            val groupBinding = ItemIoGroupBinding.inflate(inflater, container, false)
            groupBinding.gridLayoutLeds.removeAllViews()

            // El número de canal para mostrar en el título
            groupBinding.textViewChannelTitle.text = getString(
                R.string.sinoptico_channel_title,
                pointType,
                channel.channel
            )

            Log.d("SynopticFragment", "Generating channel ${channel.channel} with ${channel.bitCount} bits")

            for (bitIndex in 0 until channel.bitCount) {
                val ledBinding = ItemLedBinding.inflate(inflater, groupBinding.gridLayoutLeds, false)

                // Usar formato OMRON "canal.bit" (ej: I0.05, Q100.03)
                val address = "$addressPrefix${channel.channel}.$bitIndex"
                ledBinding.ledLabel.text = "${channel.channel}.${bitIndex.toString().padStart(2, '0')}"
                groupBinding.gridLayoutLeds.addView(ledBinding.root)

                ledViewMap[address] = ledBinding.ledImage
                Log.d("SynopticFragment", "Created LED: $address")
            }

            container.addView(groupBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ledViewMap.clear()
        _binding = null
    }
}
